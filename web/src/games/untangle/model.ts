import { z } from 'zod';
import type { Engine, Level, PortableSession } from '../../shared/lib/engine';
import { puzzleCore } from '../../shared/lib/puzzle-core';
import {
  intersects,
  onSegment,
  payloadSchema,
  pointSchema,
  type Action,
  type Payload,
  type Point,
  type State,
} from './legacy-model';

export { intersects, onSegment, payloadSchema, pointSchema };
export type { Action, Payload, Point, State };

type CorePayload = {
  nodes: { id: string; x: number; y: number; fixed: boolean }[];
  edges: {
    id: string;
    firstNodeId: string;
    secondNodeId: string;
    intersectionsAllowed: boolean;
  }[];
  fieldBounds: { left: number; top: number; right: number; bottom: number };
  minimumNodeDistance: number;
  placementZones: never[];
  forbiddenZones: never[];
  intersectionsAllowedWithinGroups: never[];
  epsilon: number;
  coordinateScale: number;
};
type CoreLevel = {
  identity: {
    mechanicId: 'untangle';
    productGameId: 'untangle';
    levelPackId: string;
    levelId: string;
    schemaVersion: 1;
    rulesVersion: 1;
    contentVersion: number;
  };
  difficulty: {
    band: 'tutorial' | 'easy' | 'medium' | 'hard';
    normalized: number;
    parameters: Record<string, never>;
  };
  payload: CorePayload;
};
type CoreTransition = { accepted: boolean; changed: boolean; state: State; reason?: string };
type CoreMetrics = {
  conflictCount: number;
  conflictEdgeIds: string[];
  placementsValid: boolean;
};
type CoreRestoreResult = { restored: boolean; session: PortableSession<State>; reason?: string };

const stateSchema = z.object({ positions: z.record(z.string(), pointSchema) });
const difficultyValue = (difficulty: Level<Payload>['difficulty']) =>
  difficulty === 'tutorial' ? 0 : difficulty === 'easy' ? 0.25 : difficulty === 'hard' ? 0.75 : 0.5;

const corePayload = (payload: Payload): CorePayload => ({
  nodes: payload.nodes,
  edges: payload.edges.map(([firstNodeId, secondNodeId], index) => ({
    id: `edge-${index}`,
    firstNodeId,
    secondNodeId,
    intersectionsAllowed: false,
  })),
  fieldBounds: {
    left: payload.bounds.min,
    top: payload.bounds.min,
    right: payload.bounds.max,
    bottom: payload.bounds.max,
  },
  minimumNodeDistance: payload.minNodeDistance,
  placementZones: [],
  forbiddenZones: [],
  intersectionsAllowedWithinGroups: [],
  epsilon: 0.000001,
  coordinateScale: 10,
});

const coreLevel = (
  payload: Payload,
  metadata: Pick<Level<Payload>, 'id' | 'difficulty' | 'contentVersion'> = {
    id: 'legacy-adapter',
    difficulty: 'medium',
    contentVersion: 1,
  },
): CoreLevel => ({
  identity: {
    mechanicId: 'untangle',
    productGameId: 'untangle',
    levelPackId: 'web-authored',
    levelId: metadata.id,
    schemaVersion: 1,
    rulesVersion: 1,
    contentVersion: metadata.contentVersion,
  },
  difficulty: { band: metadata.difficulty, normalized: difficultyValue(metadata.difficulty), parameters: {} },
  payload: corePayload(payload),
});

const reasonText: Record<string, string> = {
  'unknown-node': 'This node is fixed. Choose an unpinned node.',
  'fixed-node': 'This node is fixed. Choose an unpinned node.',
  'non-finite-position': 'Keep the node inside the board.',
  'out-of-bounds': 'Keep the node inside the board.',
  'nodes-too-close': 'Leave a little space between the nodes.',
};

export function conflicts(payload: Payload, state: State) {
  const metrics = puzzleCore.evaluate<{ metrics: CoreMetrics }>('untangle', coreLevel(payload), state).metrics;
  return {
    edges: new Set(metrics.conflictEdgeIds.map((id) => Number(id.slice('edge-'.length)))),
    count: metrics.conflictCount,
  };
}

export const engine: Engine<Payload, State, Action> = {
  initial: (payload) => puzzleCore.initialState<State>('untangle', coreLevel(payload)),
  validState: (payload, state): state is State => {
    if (!stateSchema.safeParse(state).success) return false;
    try {
      return puzzleCore.validateState<{ valid: boolean }>('untangle', coreLevel(payload), state).valid;
    } catch {
      return false;
    }
  },
  apply(payload, state, action) {
    if (!Number.isFinite(action.x) || !Number.isFinite(action.y))
      return { accepted: false, changed: false, state, reason: 'Keep the node inside the board.' };
    const result = puzzleCore.transition<CoreTransition>('untangle', coreLevel(payload), state, {
      type: action.type,
      nodeId: action.id,
      x: action.x,
      y: action.y,
    });
    return {
      ...result,
      state: result.changed ? result.state : state,
      reason: result.reason ? reasonText[result.reason] ?? result.reason : undefined,
    };
  },
  solved: (payload, state) =>
    puzzleCore.evaluate<{ solved: boolean }>('untangle', coreLevel(payload), state).solved,
  persistence: {
    restore: (level, raw, fallbackAttemptId) => {
      if (raw == null) return null;
      try {
        const result = puzzleCore.restoreSession<CoreRestoreResult>(
          'untangle',
          coreLevel(level.payload, level),
          raw,
          fallbackAttemptId,
        );
        if (!result.restored) return null;
        const { attemptId, state, history, moveCount, hintsUsed, revision } = result.session;
        return { attemptId, state, history, moveCount, hintsUsed, revision };
      } catch {
        return null;
      }
    },
    encode: (level, session) =>
      puzzleCore.encodeSession('untangle', coreLevel(level.payload, level), session),
  },
};
