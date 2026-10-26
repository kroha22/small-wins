import { z } from 'zod';
import type { Engine, Level, PortableSession } from '../../shared/lib/engine';
import { puzzleCore } from '../../shared/lib/puzzle-core';

export const payloadSchema = z
  .object({
    rows: z.number().int().min(2).max(8),
    cols: z.number().int().min(2).max(8),
    clues: z
      .array(
        z.object({
          cell: z.number().int().nonnegative(),
          area: z.number().int().min(1),
        }),
      )
      .min(1),
    initialRegions: z.array(z.number().int().min(-1)),
  })
  .superRefine((p, ctx) => {
    const size = p.rows * p.cols;
    if (p.initialRegions.length !== size) {
      ctx.addIssue({ code: 'custom', message: 'Initial regions must cover the grid.' });
    }
    const cells = new Set<number>();
    p.clues.forEach((clue, index) => {
      if (clue.cell >= size || cells.has(clue.cell)) {
        ctx.addIssue({ code: 'custom', message: `Invalid or duplicate clue ${index}.` });
      }
      cells.add(clue.cell);
      if (clue.area > size) {
        ctx.addIssue({ code: 'custom', message: `Clue ${index} is larger than the grid.` });
      }
    });
    if (p.initialRegions.some((region) => region >= p.clues.length)) {
      ctx.addIssue({ code: 'custom', message: 'Initial region references an unknown clue.' });
    }
  });

export type Payload = z.infer<typeof payloadSchema>;
export type State = { regions: number[] };
export type Action =
  | {
      type: 'place';
      clue: number;
      top: number;
      left: number;
      bottom: number;
      right: number;
    }
  | { type: 'clear'; clue: number };
const stateSchema = z.object({ regions: z.array(z.number().int()) });

type CoreLevel = {
  identity: {
    mechanicId: 'shikaku';
    productGameId: 'shikaku';
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
type CoreTransition = { accepted: boolean; changed: boolean; state: State; reason?: string };
type CoreHint = {
  kind: 'area' | 'rectangle';
  data: { clue: number; area: number; top: number; left: number; bottom: number; right: number };
};
type CoreSolveResult = { solutions: State[]; nodes: number; exceeded: boolean };
type CoreRestoreResult = { restored: boolean; session: PortableSession<State>; reason?: string };

const difficultyValue = (difficulty: Level<Payload>['difficulty']) =>
  difficulty === 'tutorial' ? 0 : difficulty === 'easy' ? 0.25 : difficulty === 'hard' ? 0.75 : 0.5;
const coreLevel = (
  payload: Payload,
  metadata: Pick<Level<Payload>, 'id' | 'difficulty' | 'contentVersion'> = {
    id: 'legacy-adapter',
    difficulty: 'medium',
    contentVersion: 1,
  },
): CoreLevel => ({
  identity: {
    mechanicId: 'shikaku',
    productGameId: 'shikaku',
    levelPackId: 'web-authored',
    levelId: metadata.id,
    schemaVersion: 1,
    rulesVersion: 1,
    contentVersion: metadata.contentVersion,
  },
  difficulty: {
    band: metadata.difficulty,
    normalized: difficultyValue(metadata.difficulty),
    parameters: {},
  },
  payload,
});

export function rectangleCells(
  p: Payload,
  action: { top: number; left: number; bottom: number; right: number },
) {
  const cells: number[] = [];
  for (let row = action.top; row <= action.bottom; row += 1) {
    for (let col = action.left; col <= action.right; col += 1) {
      cells.push(row * p.cols + col);
    }
  }
  return cells;
}

export function regionCells(state: State, clue: number) {
  return state.regions.flatMap((value, cell) => (value === clue ? [cell] : []));
}

export function solutions(p: Payload, limit = 2): State[] {
  return puzzleCore.solveDetails<CoreSolveResult>('shikaku', coreLevel(p), null, limit, 100_000).solutions;
}

const reasonText: Record<string, string> = {
  'invalid-rectangle': 'Choose a rectangle inside the board.',
  'missing-clue': 'Every region must contain its clue.',
  'area-mismatch': 'The rectangle area must match its clue.',
  overlap: 'Regions cannot overlap.',
};

export const engine: Engine<Payload, State, Action> = {
  initial: (p) => puzzleCore.initialState<State>('shikaku', coreLevel(p)),
  validState: (p, state): state is State => {
    if (!stateSchema.safeParse(state).success) return false;
    try {
      return puzzleCore.validateState<{ valid: boolean }>('shikaku', coreLevel(p), state).valid;
    } catch {
      return false;
    }
  },
  apply(p, state, action) {
    const result = puzzleCore.transition<CoreTransition>('shikaku', coreLevel(p), state, action);
    return {
      ...result,
      state: result.changed ? result.state : state,
      reason: result.reason ? reasonText[result.reason] ?? result.reason : undefined,
    };
  },
  solved: (p, state) => puzzleCore.evaluate<{ solved: boolean }>('shikaku', coreLevel(p), state).solved,
  hint: (p, state, used) => {
    const hint = puzzleCore.hint<CoreHint | null>('shikaku', coreLevel(p), state, used);
    if (!hint) return null;
    if (hint.kind === 'area') {
      return `Start with clue ${hint.data.area}. Its rectangle includes ${hint.data.area} squares.`;
    }
    return `Try the rectangle containing clue ${hint.data.area} from row ${hint.data.top + 1}, column ${hint.data.left + 1} to row ${hint.data.bottom + 1}, column ${hint.data.right + 1}.`;
  },
  persistence: {
    restore: (level, raw, fallbackAttemptId) => {
      if (raw == null) return null;
      try {
        const result = puzzleCore.restoreSession<CoreRestoreResult>(
          'shikaku',
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
    encode: (level, session) => puzzleCore.encodeSession('shikaku', coreLevel(level.payload, level), session),
  },
};
