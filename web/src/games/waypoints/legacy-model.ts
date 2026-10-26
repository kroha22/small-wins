import { z } from 'zod';
import { accept, reject, neighbours, cellName, type Engine } from '../../shared/lib/engine';

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

export const engine: Engine<Payload, State, Action> = {
  initial: (payload) => ({ path: [payload.start] }),
  validState: (payload, state): state is State => {
    if (typeof state !== 'object' || state == null || !('path' in state) || !Array.isArray((state as State).path)) return false;
    const path = (state as State).path;
    return path.length > 0 && path[0] === payload.start && new Set(path).size === path.length && path.every(
      (cell, index) => Number.isInteger(cell) && cell >= 0 && cell < payload.rows * payload.cols &&
        !payload.blocked.includes(cell) &&
        (index === 0 || neighbours(path[index - 1]!, payload.rows, payload.cols).includes(cell)) &&
        (cell !== payload.target || (index === path.length - 1 && payload.waypoints.every((waypoint) => path.includes(waypoint)))),
    );
  },
  apply(payload, state, action) {
    if (action.type !== 'addCell' || !Number.isInteger(action.cell)) return reject(state, 'Choose a cell.');
    const end = state.path.at(-1)!;
    if (action.cell === end) return accept(state, false);
    if (!neighbours(end, payload.rows, payload.cols).includes(action.cell))
      return reject(state, 'Choose a cell directly beside the end of your path.');
    if (payload.blocked.includes(action.cell)) return reject(state, 'This cell is blocked.');
    if (state.path.includes(action.cell)) return reject(state, 'The path cannot visit a cell twice. Try Undo.');
    if (action.cell === payload.target && !payload.waypoints.every((waypoint) => state.path.includes(waypoint)))
      return reject(state, 'Collect every carrot before reaching the flag.');
    return accept({ path: [...state.path, action.cell] });
  },
  solved: (payload, state) => state.path.at(-1) === payload.target && payload.waypoints.every((waypoint) => state.path.includes(waypoint)),
};

export function routeCompletion(payload: Payload, state: State, budget = 25_000) {
  let nodes = 0;
  let exceeded = false;
  const path = [...state.path];
  const seen = new Set(path);
  const blocked = new Set(payload.blocked);
  const search = (): number[] | null => {
    nodes += 1;
    if (nodes > budget) { exceeded = true; return null; }
    const end = path.at(-1)!;
    if (end === payload.target) return payload.waypoints.every((waypoint) => seen.has(waypoint)) ? [...path] : null;
    const reach = new Set([end]);
    const queue = [end];
    while (queue.length)
      for (const neighbour of neighbours(queue.pop()!, payload.rows, payload.cols))
        if (!seen.has(neighbour) && !blocked.has(neighbour) && !reach.has(neighbour)) {
          reach.add(neighbour); queue.push(neighbour);
        }
    if ([payload.target, ...payload.waypoints.filter((waypoint) => !seen.has(waypoint))].some((cell) => !reach.has(cell))) return null;
    const options = neighbours(end, payload.rows, payload.cols).filter(
      (cell) => !seen.has(cell) && !blocked.has(cell) &&
        (cell !== payload.target || payload.waypoints.every((waypoint) => seen.has(waypoint))),
    );
    for (const cell of options) {
      path.push(cell); seen.add(cell);
      const answer = search();
      if (answer) return answer;
      seen.delete(cell); path.pop();
      if (exceeded) break;
    }
    return null;
  };
  if (!engine.validState(payload, state)) return { path: null, exceeded: false, nodes: 0 };
  return { path: search(), exceeded, nodes };
}

export function routeHint(payload: Payload, state: State, used: number) {
  const result = routeCompletion(payload, state);
  if (result.exceeded) return null;
  if (!result.path) return 'This path is a dead end: no complete route remains. Undo the last step, then ask again to check the shorter path.';
  const next = result.path[state.path.length];
  if (next == null) return null;
  const target = result.path.slice(state.path.length).find((cell) => payload.waypoints.includes(cell) && !state.path.includes(cell)) ?? payload.target;
  if (used === 0)
    return `Look toward row ${Math.floor(target / payload.cols) + 1}. ${target === payload.target ? 'The flag' : 'A remaining carrot'} there can be reached as part of a complete route.`;
  if (used === 1)
    return `Aim for ${cellName(target, payload.cols)}, ${target === payload.target ? 'the flag' : 'a carrot on a working route'}. Keep a way through to the finish.`;
  return `Next step: add ${cellName(next, payload.cols)}. A complete route through every remaining carrot still exists after this move.`;
}
