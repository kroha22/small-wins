import { z } from 'zod';
import { accept, reject, type Engine } from '../../shared/lib/engine';

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
export type Action = {
  type: 'place';
  clue: number;
  top: number;
  left: number;
  bottom: number;
  right: number;
};
const stateSchema = z.object({ regions: z.array(z.number().int()) });

export function rectangleCells(p: Payload, action: Pick<Action, 'top' | 'left' | 'bottom' | 'right'>) {
  const cells: number[] = [];
  for (let row = action.top; row <= action.bottom; row += 1) {
    for (let col = action.left; col <= action.right; col += 1) {
      cells.push(row * p.cols + col);
    }
  }
  return cells;
}

function isRectangle(p: Payload, cells: number[]) {
  if (cells.length === 0) return false;
  const rows = cells.map((cell) => Math.floor(cell / p.cols));
  const cols = cells.map((cell) => cell % p.cols);
  const top = Math.min(...rows);
  const bottom = Math.max(...rows);
  const left = Math.min(...cols);
  const right = Math.max(...cols);
  return rectangleCells(p, { top, left, bottom, right }).length === cells.length;
}

export function regionCells(state: State, clue: number) {
  return state.regions.flatMap((value, cell) => (value === clue ? [cell] : []));
}

export function solutions(p: Payload, limit = 2): State[] {
  const options = p.clues.map((clue) => {
    const result: number[][] = [];
    for (let top = 0; top < p.rows; top += 1)
      for (let left = 0; left < p.cols; left += 1)
        for (let bottom = top; bottom < p.rows; bottom += 1)
          for (let right = left; right < p.cols; right += 1) {
            const cells = rectangleCells(p, { top, left, bottom, right });
            if (cells.length === clue.area && cells.includes(clue.cell)) result.push(cells);
          }
    return result;
  });
  const found: State[] = [];
  const regions = Array<number>(p.rows * p.cols).fill(-1);
  function search(clue: number) {
    if (found.length >= limit) return;
    if (clue === p.clues.length) {
      found.push({ regions: [...regions] });
      return;
    }
    for (const cells of options[clue]!) {
      if (cells.some((cell) => regions[cell] !== -1)) continue;
      cells.forEach((cell) => (regions[cell] = clue));
      search(clue + 1);
      cells.forEach((cell) => (regions[cell] = -1));
    }
  }
  search(0);
  return found;
}

export const engine: Engine<Payload, State, Action> = {
  initial: (p) => ({ regions: [...p.initialRegions] }),
  validState: (p, state): state is State => {
    const parsed = stateSchema.safeParse(state);
    return (
      parsed.success &&
      parsed.data.regions.length === p.rows * p.cols &&
      parsed.data.regions.every((region) => region >= -1 && region < p.clues.length)
    );
  },
  apply(p, state, action) {
    if (
      action.type !== 'place' ||
      !Number.isInteger(action.clue) ||
      !p.clues[action.clue] ||
      ![action.top, action.left, action.bottom, action.right].every(Number.isInteger) ||
      action.top < 0 ||
      action.left < 0 ||
      action.bottom >= p.rows ||
      action.right >= p.cols ||
      action.top > action.bottom ||
      action.left > action.right
    ) {
      return reject(state, 'Choose a rectangle inside the board.');
    }
    const cells = rectangleCells(p, action);
    const clueCell = p.clues[action.clue]!.cell;
    if (!cells.includes(clueCell)) return reject(state, 'Every region must contain its clue.');
    if (cells.length !== p.clues[action.clue]!.area) {
      return reject(state, 'The rectangle area must match its clue.');
    }
    if (cells.some((cell) => state.regions[cell] !== -1 && state.regions[cell] !== action.clue)) {
      return reject(state, 'Regions cannot overlap.');
    }
    const regions = [...state.regions];
    cells.forEach((cell) => {
      regions[cell] = action.clue;
    });
    return accept({ regions });
  },
  solved: (p, state) =>
    state.regions.every((region) => region >= 0) &&
    p.clues.every((clue, index) => {
      const cells = regionCells(state, index);
      return cells.length === clue.area && cells.includes(clue.cell) && isRectangle(p, cells);
    }),
  hint: (p, state, used) => {
    const answer = solutions(p, 1)[0];
    if (!answer) return null;
    const clue = p.clues.findIndex((_, index) => regionCells(state, index).length === 0);
    if (clue < 0) return null;
    const cells = regionCells(answer, clue);
    if (used === 0) return `Start with clue ${p.clues[clue]!.area}. Its rectangle includes ${cells.length} squares.`;
    const first = cells[0]!;
    return `Try the rectangle containing clue ${p.clues[clue]!.area} at row ${Math.floor(first / p.cols) + 1}, column ${(first % p.cols) + 1}.`;
  },
};
