import { z } from 'zod';
import { cellName, type Engine, type Level, type PortableSession } from '../../shared/lib/engine';
import { puzzleCore } from '../../shared/lib/puzzle-core';

export const payloadSchema = z
  .object({
    rows: z.number().int().min(3).max(6),
    cols: z.number().int().min(3).max(6),
    start: z.number().int(),
    target: z.number().int(),
    blocked: z.array(z.number().int()),
    waypoints: z.array(z.number().int()),
  })
  .superRefine((payload, context) => {
    const cells = [payload.start, payload.target, ...payload.blocked, ...payload.waypoints];
    if (new Set(cells).size !== cells.length || cells.some((cell) => cell < 0 || cell >= payload.rows * payload.cols))
      context.addIssue({ code: 'custom', message: 'Overlapping or invalid route cells' });
  });
export type Payload = z.infer<typeof payloadSchema>;
export type State = { path: number[] };
export type Action = { type: 'addCell'; cell: number };

type CorePayload = {
  nodes: { id: string; x: number; y: number }[];
  connections: { firstNodeId: string; secondNodeId: string; bidirectional: boolean }[];
  starts: { id: string; nodeId: string }[];
  targets: { id: string; nodeId: string }[];
  waypoints: { id: string; nodeId: string; required: boolean }[];
  forbiddenNodeIds: string[];
  pathRules: {
    nodesMayRepeat: boolean;
    pathsMayCross: boolean;
    pathsMayShareNodes: boolean;
    targetRequiresAllRequiredWaypoints: boolean;
  };
};
type CoreLevel = {
  identity: {
    mechanicId: 'route'; productGameId: 'waypoints'; levelPackId: string; levelId: string;
    schemaVersion: 1; rulesVersion: 1; contentVersion: number;
  };
  difficulty: {
    band: 'tutorial' | 'easy' | 'medium' | 'hard'; normalized: number; parameters: Record<string, never>;
  };
  payload: CorePayload;
};
type CorePath = { startId: string; targetId?: string; nodeIds: string[] };
type CoreState = {
  paths: CorePath[]; activeStartId?: string; visitedWaypointIds: string[]; deadEndDetected: boolean;
};
type CoreTransition = { accepted: boolean; changed: boolean; state: CoreState; reason?: string };
type CoreSolveResult = { solutions: CoreState[]; nodes: number; exceeded: boolean };
type CoreHint = {
  kind: 'dead-end' | 'target-axis' | 'target-node' | 'next-node';
  data: { nodeId?: string; targetNodeId?: string; targetKind?: 'target' | 'waypoint' };
};
type CoreRestoreResult = { restored: boolean; session: PortableSession<CoreState>; reason?: string };

const nodeId = (cell: number) => `cell-${cell}`;
const nodeCell = (id: string) => Number(id.slice('cell-'.length));
const difficultyValue = (difficulty: Level<Payload>['difficulty']) =>
  difficulty === 'tutorial' ? 0 : difficulty === 'easy' ? 0.25 : difficulty === 'hard' ? 0.75 : 0.5;

const corePayload = (payload: Payload): CorePayload => {
  const connections: CorePayload['connections'] = [];
  for (let row = 0; row < payload.rows; row += 1)
    for (let column = 0; column < payload.cols; column += 1) {
      const cell = row * payload.cols + column;
      if (column + 1 < payload.cols)
        connections.push({ firstNodeId: nodeId(cell), secondNodeId: nodeId(cell + 1), bidirectional: true });
      if (row + 1 < payload.rows)
        connections.push({ firstNodeId: nodeId(cell), secondNodeId: nodeId(cell + payload.cols), bidirectional: true });
    }
  return {
    nodes: Array.from({ length: payload.rows * payload.cols }, (_, cell) => ({
      id: nodeId(cell), x: cell % payload.cols, y: Math.floor(cell / payload.cols),
    })),
    connections,
    starts: [{ id: 'start', nodeId: nodeId(payload.start) }],
    targets: [{ id: 'target', nodeId: nodeId(payload.target) }],
    waypoints: payload.waypoints.map((cell, index) => ({
      id: `waypoint-${index + 1}`, nodeId: nodeId(cell), required: true,
    })),
    forbiddenNodeIds: payload.blocked.map(nodeId),
    pathRules: {
      nodesMayRepeat: false,
      pathsMayCross: false,
      pathsMayShareNodes: false,
      targetRequiresAllRequiredWaypoints: true,
    },
  };
};

const coreLevel = (
  payload: Payload,
  metadata: Pick<Level<Payload>, 'id' | 'difficulty' | 'contentVersion'> = {
    id: 'legacy-adapter', difficulty: 'medium', contentVersion: 1,
  },
): CoreLevel => ({
  identity: {
    mechanicId: 'route', productGameId: 'waypoints', levelPackId: 'web-authored', levelId: metadata.id,
    schemaVersion: 1, rulesVersion: 1, contentVersion: metadata.contentVersion,
  },
  difficulty: { band: metadata.difficulty, normalized: difficultyValue(metadata.difficulty), parameters: {} },
  payload: corePayload(payload),
});

const toCoreState = (payload: Payload, state: State): CoreState => ({
  paths: [{
    startId: 'start',
    ...(state.path.at(-1) === payload.target ? { targetId: 'target' } : {}),
    nodeIds: state.path.map(nodeId),
  }],
  activeStartId: 'start',
  visitedWaypointIds: payload.waypoints
    .map((cell, index) => ({ cell, id: `waypoint-${index + 1}` }))
    .filter(({ cell }) => state.path.includes(cell))
    .map(({ id }) => id),
  deadEndDetected: false,
});
const fromCoreState = (payload: Payload, state: CoreState): State => ({
  path: state.paths[0]?.nodeIds.map(nodeCell) ?? [payload.start],
});
const startCoreState = (payload: Payload) => {
  const level = coreLevel(payload);
  const initial = puzzleCore.initialState<CoreState>('route', level);
  return puzzleCore.transition<CoreTransition>('route', level, initial, { type: 'start', startId: 'start' }).state;
};

const reasonText: Record<string, string> = {
  'unknown-node': 'Choose a cell.',
  'not-connected': 'Choose a cell directly beside the end of your path.',
  'forbidden-node': 'This cell is blocked.',
  'node-already-used': 'The path cannot visit a cell twice. Try Undo.',
  'missing-waypoints': 'Collect every carrot before reaching the flag.',
};

export const engine: Engine<Payload, State, Action> = {
  initial: (payload) => fromCoreState(payload, startCoreState(payload)),
  validState: (payload, state): state is State => {
    if (typeof state !== 'object' || state == null || !('path' in state) || !Array.isArray((state as State).path)) return false;
    try {
      return puzzleCore.validateState<{ valid: boolean }>('route', coreLevel(payload), toCoreState(payload, state as State)).valid;
    } catch {
      return false;
    }
  },
  apply(payload, state, action) {
    if (action.type !== 'addCell' || !Number.isInteger(action.cell))
      return { accepted: false, changed: false, state, reason: 'Choose a cell.' };
    if (action.cell === state.path.at(-1)) return { accepted: true, changed: false, state };
    const result = puzzleCore.transition<CoreTransition>(
      'route', coreLevel(payload), toCoreState(payload, state), { type: 'extend', nodeId: nodeId(action.cell) },
    );
    return {
      ...result,
      state: result.changed ? fromCoreState(payload, result.state) : state,
      reason: result.reason ? reasonText[result.reason] ?? result.reason : undefined,
    };
  },
  solved: (payload, state) =>
    puzzleCore.evaluate<{ solved: boolean }>('route', coreLevel(payload), toCoreState(payload, state)).solved,
  hint: routeHint,
  persistence: {
    restore: (level, raw, fallbackAttemptId) => {
      if (raw == null) return null;
      try {
        const result = puzzleCore.restoreSession<CoreRestoreResult>(
          'route', coreLevel(level.payload, level), raw, fallbackAttemptId,
        );
        if (!result.restored) return null;
        const { attemptId, state, history, moveCount, hintsUsed, revision } = result.session;
        return {
          attemptId,
          state: fromCoreState(level.payload, state),
          history: history.map((item) => fromCoreState(level.payload, item)),
          moveCount, hintsUsed, revision,
        };
      } catch {
        return null;
      }
    },
    encode: (level, session) => puzzleCore.encodeSession(
      'route', coreLevel(level.payload, level), {
        ...session,
        state: toCoreState(level.payload, session.state),
        history: session.history.map((item) => toCoreState(level.payload, item)),
      },
    ),
  },
};

export function routeCompletion(payload: Payload, state: State, budget = 25_000) {
  const result = puzzleCore.solveDetails<CoreSolveResult>(
    'route', coreLevel(payload), toCoreState(payload, state), 1, budget,
  );
  return {
    path: result.solutions[0] ? fromCoreState(payload, result.solutions[0]).path : null,
    exceeded: result.exceeded,
    nodes: result.nodes,
  };
}

function routeHint(payload: Payload, state: State, used: number): string | null {
  const hint = puzzleCore.hint<CoreHint | null>('route', coreLevel(payload), toCoreState(payload, state), used);
  if (!hint) return null;
  if (hint.kind === 'dead-end')
    return 'This path is a dead end: no complete route remains. Undo the last step, then ask again to check the shorter path.';
  const target = nodeCell(hint.data.targetNodeId!);
  const next = nodeCell(hint.data.nodeId!);
  if (hint.kind === 'target-axis')
    return `Look toward row ${Math.floor(target / payload.cols) + 1}. ${hint.data.targetKind === 'target' ? 'The flag' : 'A remaining carrot'} there can be reached as part of a complete route.`;
  if (hint.kind === 'target-node')
    return `Aim for ${cellName(target, payload.cols)}, ${hint.data.targetKind === 'target' ? 'the flag' : 'a carrot on a working route'}. Keep a way through to the finish.`;
  return `Next step: add ${cellName(next, payload.cols)}. A complete route through every remaining carrot still exists after this move.`;
}
