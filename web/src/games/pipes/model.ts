import { z } from 'zod';
import * as puzzleCore from '../../generated/puzzle-core/small-wins-puzzle-core.mjs';
import { type Engine } from '../../shared/lib/engine';

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
    levelPackId: 'web-authored';
    levelId: 'legacy-adapter';
    schemaVersion: 1;
    rulesVersion: 1;
    contentVersion: 1;
  };
  difficulty: { band: 'medium'; normalized: 0.5; parameters: Record<string, never> };
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

const encode = (value: unknown) => JSON.stringify(value);
const decode = <T>(value: string): T => JSON.parse(value) as T;
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

export const engine: Engine<Payload, State, Action> = {
  initial: (payload) =>
    decode<State>(puzzleCore.initialState('network', encode(coreLevel(payload)))),
  validState: (payload, state): state is State => {
    try {
      return decode<{ valid: boolean }>(
        puzzleCore.validateState('network', encode(coreLevel(payload)), encode(state)),
      ).valid;
    } catch {
      return false;
    }
  },
  apply: (payload, state, action) => {
    const result = decode<CoreTransition>(
      puzzleCore.transition(
        'network',
        encode(coreLevel(payload)),
        encode(state),
        encode(action),
      ),
    );
    return result.changed ? result : { ...result, state };
  },
  solved: (payload, state) =>
    decode<CoreEvaluation>(
      puzzleCore.evaluate('network', encode(coreLevel(payload)), encode(state)),
    ).solved,
  hint: (payload, state, used) => {
    const result = decode<CoreHint | null>(
      puzzleCore.hint('network', encode(coreLevel(payload)), encode(state), used),
    );
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
  const result = decode<CoreInspection>(
    puzzleCore.inspect('network', encode(coreLevel(payload)), encode(state)),
  );
  return {
    reached: new Set(result.reached),
    leaks: result.leaks.map(({ cell, direction }) => ({ cell, dir: direction })),
  };
}

export function pipeSolution(payload: Payload, budget = 20_000): State | null {
  return decode<State | null>(
    puzzleCore.solveWithBudget('network', encode(coreLevel(payload)), budget),
  );
}
