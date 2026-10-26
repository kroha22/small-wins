import { accept, reject, type Engine } from '../../shared/lib/engine';
import type { Action, Payload, State } from './model';

const rectangleCells = (
  payload: Payload,
  action: { top: number; left: number; bottom: number; right: number },
) => {
  const cells: number[] = [];
  for (let row = action.top; row <= action.bottom; row += 1)
    for (let column = action.left; column <= action.right; column += 1) cells.push(row * payload.cols + column);
  return cells;
};

const regionCells = (state: State, clue: number) =>
  state.regions.flatMap((value, cell) => (value === clue ? [cell] : []));

export function solutions(payload: Payload, limit = 2): State[] {
  const options = payload.clues.map((clue) => {
    const result: number[][] = [];
    for (let top = 0; top < payload.rows; top += 1)
      for (let left = 0; left < payload.cols; left += 1)
        for (let bottom = top; bottom < payload.rows; bottom += 1)
          for (let right = left; right < payload.cols; right += 1) {
            const cells = rectangleCells(payload, { top, left, bottom, right });
            if (cells.length === clue.area && cells.includes(clue.cell)) result.push(cells);
          }
    return result;
  });
  const found: State[] = [];
  const regions = Array<number>(payload.rows * payload.cols).fill(-1);
  const search = (clue: number) => {
    if (found.length >= limit) return;
    if (clue === payload.clues.length) {
      found.push({ regions: [...regions] });
      return;
    }
    for (const cells of options[clue]!) {
      if (cells.some((cell) => regions[cell] !== -1)) continue;
      cells.forEach((cell) => (regions[cell] = clue));
      search(clue + 1);
      cells.forEach((cell) => (regions[cell] = -1));
    }
  };
  search(0);
  return found;
}

export const engine: Engine<Payload, State, Action> = {
  initial: (payload) => ({ regions: [...payload.initialRegions] }),
  validState: (payload, state): state is State =>
    typeof state === 'object' &&
    state != null &&
    'regions' in state &&
    Array.isArray((state as State).regions) &&
    (state as State).regions.length === payload.rows * payload.cols &&
    (state as State).regions.every((region) => Number.isInteger(region) && region >= -1 && region < payload.clues.length),
  apply(payload, state, action) {
    if (
      action.type !== 'place' ||
      action.clue < 0 ||
      !payload.clues[action.clue] ||
      action.top < 0 ||
      action.left < 0 ||
      action.bottom >= payload.rows ||
      action.right >= payload.cols ||
      action.top > action.bottom ||
      action.left > action.right
    ) return reject(state, 'invalid');
    const cells = rectangleCells(payload, action);
    if (!cells.includes(payload.clues[action.clue]!.cell)) return reject(state, 'missing-clue');
    if (cells.length !== payload.clues[action.clue]!.area) return reject(state, 'area');
    if (cells.some((cell) => state.regions[cell] !== -1 && state.regions[cell] !== action.clue))
      return reject(state, 'overlap');
    const regions = [...state.regions];
    cells.forEach((cell) => (regions[cell] = action.clue));
    return accept({ regions });
  },
  solved: (payload, state) =>
    state.regions.every((region) => region >= 0) &&
    payload.clues.every((clue, index) => {
      const cells = regionCells(state, index);
      if (cells.length !== clue.area || !cells.includes(clue.cell)) return false;
      const rows = cells.map((cell) => Math.floor(cell / payload.cols));
      const columns = cells.map((cell) => cell % payload.cols);
      return (
        rectangleCells(payload, {
          top: Math.min(...rows),
          left: Math.min(...columns),
          bottom: Math.max(...rows),
          right: Math.max(...columns),
        }).length === cells.length
      );
    }),
};
