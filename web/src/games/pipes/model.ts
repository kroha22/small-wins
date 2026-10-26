import { z } from 'zod';
import { puzzleCore } from '../../shared/lib/puzzle-core';
import { type Engine, type Level, type PortableSession } from '../../shared/lib/engine';

export const payloadSchema = z
  .object({
    rows: z.number().int().min(2).max(6),
    cols: z.number().int().min(2).max(6),
    sourceCell: z.number().int().nonnegative(),
    ports: z.array(z.array(z.number().int().min(0).max(3)).min(1).max(4)),
    initialRotations: z.array(z.number().int().min(0).max(3)),
  })
  .superRefine((payload, context) => {
    if (
      payload.ports.length !== payload.rows * payload.cols ||
      payload.initialRotations.length !== payload.ports.length ||
      payload.sourceCell >= payload.ports.length ||
      payload.ports.some((ports) => new Set(ports).size !== ports.length)
    ) {
      context.addIssue({ code: 'custom', message: 'Invalid pipe grid' });
    }
  });

export type Payload = z.infer<typeof payloadSchema>;
export type State = { rotations: number[] };
export type Action = { type: 'rotate'; cell: number; quarterTurns: 1 | -1 };
type CoreLevel = {
  identity: {
    mechanicId: 'network';
    productGameId: 'pipes';
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
  payload: Payload;
};
type CoreTransition = {
  accepted: boolean;
  changed: boolean;
  state: State;
  reason?: string;
};
type CoreEvaluation = { solved: boolean };
type CoreHint = {
  kind: 'row' | 'cell' | 'orientation';
  data: { cell: number; row: number; column: number; targetRotation?: number };
};
type CoreInspection = {
  reached: number[];
  leaks: { cell: number; direction: number }[];
};
type CoreRestoreResult = {
  restored: boolean;
  session: PortableSession<State>;
  reason?: string;
};

const coreLevel = (payload: Payload): CoreLevel => ({
  identity: {
    mechanicId: 'network',
    productGameId: 'pipes',
    levelPackId: 'web-authored',
    levelId: 'legacy-adapter',
    schemaVersion: 1,
    rulesVersion: 1,
    contentVersion: 1,
  },
  difficulty: { band: 'medium', normalized: 0.5, parameters: {} },
  payload,
});

const portableCoreLevel = (level: Level<Payload>): CoreLevel => ({
  identity: {
    mechanicId: 'network',
    productGameId: 'pipes',
    levelPackId: 'web-authored',
    levelId: level.id,
    schemaVersion: 1,
    rulesVersion: 1,
    contentVersion: level.contentVersion,
  },
  difficulty: {
    band: level.difficulty,
    normalized:
      level.difficulty === 'tutorial'
        ? 0
        : level.difficulty === 'easy'
          ? 0.25
          : level.difficulty === 'hard'
            ? 0.75
            : 0.5,
    parameters: {},
  },
  payload: level.payload,
});

export const engine: Engine<Payload, State, Action> = {
  initial: (payload) =>
    puzzleCore.initialState<State>('network', coreLevel(payload)),
  validState: (payload, state): state is State => {
    try {
      return puzzleCore.validateState<{ valid: boolean }>('network', coreLevel(payload), state).valid;
    } catch {
      return false;
    }
  },
  apply: (payload, state, action) => {
    const result = puzzleCore.transition<CoreTransition>('network', coreLevel(payload), state, action);
    return result.changed ? result : { ...result, state };
  },
  solved: (payload, state) =>
    puzzleCore.evaluate<CoreEvaluation>('network', coreLevel(payload), state).solved,
  hint: (payload, state, used) => {
    const result = puzzleCore.hint<CoreHint | null>('network', coreLevel(payload), state, used);
    if (!result) return null;
    const { cell, row, column, targetRotation } = result.data;
    if (result.kind === 'row') {
      return `Look in row ${row + 1}. One of its pipes needs a different turn in a working arrangement.`;
    }
    if (result.kind === 'cell') {
      return `Focus on the pipe at row ${row + 1}, column ${column + 1}. Its openings can be turned to join a complete network.`;
    }
    const target = targetRotation ?? state.rotations[cell]!;
    const turns = (target - state.rotations[cell]! + 4) % 4;
    const direction = `clockwise ${turns === 1 ? 'once' : turns === 2 ? 'twice' : 'three times'}`;
    const openings = payload.ports[cell]!
      .map((value) => ['up', 'right', 'down', 'left'][(value + target) % 4])
      .join(' and ');
    return `One working arrangement: turn the pipe at row ${row + 1}, column ${column + 1} ${direction}, so its openings face ${openings}.`;
  },
  persistence: {
    restore: (level, raw, fallbackAttemptId) => {
      if (raw == null) return null;
      try {
        const result = puzzleCore.restoreSession<CoreRestoreResult>(
          'network', portableCoreLevel(level), raw, fallbackAttemptId,
        );
        if (!result.restored) return null;
        const { attemptId, state, history, moveCount, hintsUsed, revision } = result.session;
        return { attemptId, state, history, moveCount, hintsUsed, revision };
      } catch {
        return null;
      }
    },
    encode: (level, session) =>
      puzzleCore.encodeSession<unknown>('network', portableCoreLevel(level), session),
  },
};

export const portsAt = (payload: Payload, state: State, cell: number) =>
  payload.ports[cell]!.map((direction) => (direction + state.rotations[cell]!) % 4);

export function adjacent(payload: Payload, cell: number, direction: number): number | null {
  const row = Math.floor(cell / payload.cols);
  const column = cell % payload.cols;
  const nextRow = row + [-1, 0, 1, 0][direction]!;
  const nextColumn = column + [0, 1, 0, -1][direction]!;
  return nextRow < 0 || nextColumn < 0 || nextRow >= payload.rows || nextColumn >= payload.cols
    ? null
    : nextRow * payload.cols + nextColumn;
}

export function connections(payload: Payload, state: State) {
  const result = puzzleCore.inspect<CoreInspection>('network', coreLevel(payload), state);
  return {
    reached: new Set(result.reached),
    leaks: result.leaks.map(({ cell, direction }) => ({ cell, dir: direction })),
  };
}

export function pipeSolution(payload: Payload, budget = 20_000): State | null {
  return puzzleCore.solveWithBudget<State | null>('network', coreLevel(payload), budget);
}
