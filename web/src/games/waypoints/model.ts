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
  .superRefine((p, c) => {
    const cells = [p.start, p.target, ...p.blocked, ...p.waypoints];
    if (new Set(cells).size !== cells.length || cells.some((n) => n < 0 || n >= p.rows * p.cols))
      c.addIssue({ code: 'custom', message: 'Overlapping or invalid route cells' });
  });
export type Payload = z.infer<typeof payloadSchema>;
export type State = { path: number[] };
export type Action = { type: 'addCell'; cell: number };
const stateSchema = z.object({ path: z.array(z.number().int()).min(1) });
export const engine: Engine<Payload, State, Action> = {
  initial: (p) => ({ path: [p.start] }),
  validState: (p, s): s is State => {
    const v = stateSchema.safeParse(s);
    if (!v.success) return false;
    const path = v.data.path;
    return (
      path[0] === p.start &&
      new Set(path).size === path.length &&
      path.every(
        (n, i) =>
          n >= 0 &&
          n < p.rows * p.cols &&
          !p.blocked.includes(n) &&
          (i === 0 || neighbours(path[i - 1]!, p.rows, p.cols).includes(n)) &&
          (n !== p.target || (i === path.length - 1 && p.waypoints.every((w) => path.includes(w)))),
      )
    );
  },
  apply(p, s, a) {
    if (a.type !== 'addCell' || !Number.isInteger(a.cell)) return reject(s, 'Choose a cell.');
    const end = s.path.at(-1)!;
    if (a.cell === end) return accept(s, false);
    if (!neighbours(end, p.rows, p.cols).includes(a.cell))
      return reject(s, 'Choose a cell directly beside the end of your path.');
    if (p.blocked.includes(a.cell)) return reject(s, 'This cell is blocked.');
    if (s.path.includes(a.cell)) return reject(s, 'The path cannot visit a cell twice. Try Undo.');
    if (a.cell === p.target && !p.waypoints.every((w) => s.path.includes(w)))
      return reject(s, 'Collect every carrot before reaching the flag.');
    return accept({ path: [...s.path, a.cell] });
  },
  solved: (p, s) => s.path.at(-1) === p.target && p.waypoints.every((w) => s.path.includes(w)),
  hint: routeHint,
};

export function routeCompletion(p: Payload, s: State, budget = 25000) {
  let nodes = 0,
    exceeded = false;
  const path = [...s.path],
    seen = new Set(path),
    blocked = new Set(p.blocked);
  function search(): number[] | null {
    if (++nodes > budget) {
      exceeded = true;
      return null;
    }
    const end = path.at(-1)!;
    if (end === p.target) return p.waypoints.every((w) => seen.has(w)) ? [...path] : null;
    // Necessary reachability pruning; victory still requires one complete simple path.
    const reach = new Set([end]),
      queue = [end];
    while (queue.length)
      for (const n of neighbours(queue.pop()!, p.rows, p.cols)) {
        if (!seen.has(n) && !blocked.has(n) && !reach.has(n)) {
          reach.add(n);
          queue.push(n);
        }
      }
    if ([p.target, ...p.waypoints.filter((w) => !seen.has(w))].some((n) => !reach.has(n)))
      return null;
    const options = neighbours(end, p.rows, p.cols).filter(
      (n) =>
        !seen.has(n) &&
        !blocked.has(n) &&
        (n !== p.target || p.waypoints.every((w) => seen.has(w))),
    );
    for (const n of options) {
      path.push(n);
      seen.add(n);
      const answer = search();
      if (answer) return answer;
      seen.delete(n);
      path.pop();
      if (exceeded) break;
    }
    return null;
  }
  if (!engine.validState(p, s)) return { path: null, exceeded: false, nodes: 0 };
  const answer = search();
  return { path: answer, exceeded, nodes };
}
function routeHint(p: Payload, s: State, used: number): string | null {
  const result = routeCompletion(p, s);
  if (result.exceeded) return null;
  if (!result.path)
    return 'This path is a dead end: no complete route remains. Undo the last step, then ask again to check the shorter path.';
  const next = result.path[s.path.length];
  if (next == null) return null;
  const target =
    result.path.slice(s.path.length).find((n) => p.waypoints.includes(n) && !s.path.includes(n)) ??
    p.target;
  if (used === 0)
    return `Look toward row ${Math.floor(target / p.cols) + 1}. ${target === p.target ? 'The flag' : 'A remaining carrot'} there can be reached as part of a complete route.`;
  if (used === 1)
    return `Aim for ${cellName(target, p.cols)}, ${target === p.target ? 'the flag' : 'a carrot on a working route'}. Keep a way through to the finish.`;
  return `Next step: add ${cellName(next, p.cols)}. A complete route through every remaining carrot still exists after this move.`;
}
