import { z } from 'zod';
import { accept, reject, cellName, type Engine } from '../../shared/lib/engine';

export const payloadSchema = z
  .object({
    rows: z.number().int().min(2).max(6),
    cols: z.number().int().min(2).max(6),
    sourceCell: z.number().int().nonnegative(),
    ports: z.array(z.array(z.number().int().min(0).max(3)).min(1).max(4)),
    initialRotations: z.array(z.number().int().min(0).max(3)),
  })
  .superRefine((p, c) => {
    if (
      p.ports.length !== p.rows * p.cols ||
      p.initialRotations.length !== p.ports.length ||
      p.sourceCell >= p.ports.length ||
      p.ports.some((v) => new Set(v).size !== v.length)
    )
      c.addIssue({ code: 'custom', message: 'Invalid pipe grid' });
  });
export type Payload = z.infer<typeof payloadSchema>;
export type State = { rotations: number[] };
export type Action = { type: 'rotate'; cell: number; quarterTurns: 1 | -1 };
export const portsAt = (p: Payload, s: State, cell: number) =>
  p.ports[cell]!.map((d) => (d + s.rotations[cell]!) % 4);
export function adjacent(p: Payload, cell: number, dir: number): number | null {
  const row = Math.floor(cell / p.cols),
    col = cell % p.cols;
  const dr = [-1, 0, 1, 0][dir]!,
    dc = [0, 1, 0, -1][dir]!;
  const r = row + dr,
    c = col + dc;
  return r < 0 || c < 0 || r >= p.rows || c >= p.cols ? null : r * p.cols + c;
}
export function connections(p: Payload, s: State) {
  const reached = new Set<number>([p.sourceCell]);
  const stack = [p.sourceCell];
  while (stack.length) {
    const cell = stack.pop()!;
    for (const d of portsAt(p, s, cell)) {
      const n = adjacent(p, cell, d);
      if (n !== null && !reached.has(n) && portsAt(p, s, n).includes((d + 2) % 4)) {
        reached.add(n);
        stack.push(n);
      }
    }
  }
  const leaks = p.ports.flatMap((_, cell) =>
    portsAt(p, s, cell)
      .filter((d) => {
        const n = adjacent(p, cell, d);
        return n === null || !portsAt(p, s, n).includes((d + 2) % 4);
      })
      .map((dir) => ({ cell, dir })),
  );
  return { reached, leaks };
}
const stateSchema = z.object({ rotations: z.array(z.number().int().min(0).max(3)) });
export const engine: Engine<Payload, State, Action> = {
  initial: (p) => ({ rotations: [...p.initialRotations] }),
  validState: (p, s): s is State => {
    const v = stateSchema.safeParse(s);
    return v.success && v.data.rotations.length === p.ports.length;
  },
  apply(p, s, a) {
    if (
      a.type !== 'rotate' ||
      !Number.isInteger(a.cell) ||
      !p.ports[a.cell] ||
      ![1, -1].includes(a.quarterTurns)
    )
      return reject(s, 'Choose a pipe on the board.');
    const rotations = [...s.rotations];
    rotations[a.cell] = (rotations[a.cell]! + a.quarterTurns + 4) % 4;
    const next = { rotations };
    if (portsAt(p, s, a.cell).sort().join() === portsAt(p, next, a.cell).sort().join())
      return accept(s, false);
    return accept(next);
  },
  solved: (p, s) => {
    const c = connections(p, s);
    return c.reached.size === p.ports.length && c.leaks.length === 0;
  },
  hint: pipeHint,
};

// Constraint search over orientations; no authored answer is shipped to the UI.
export function pipeSolution(p: Payload, budget = 20000): State | null {
  const rotations = Array<number>(p.ports.length).fill(-1);
  const options = p.ports.map((ports, cell) => {
    const seen = new Set<string>();
    return [0, 1, 2, 3].filter((r) => {
      const dirs = ports.map((d) => (d + r) % 4).sort();
      const key = dirs.join();
      if (seen.has(key) || dirs.some((d) => adjacent(p, cell, d) === null)) return false;
      seen.add(key);
      return true;
    });
  });
  let nodes = 0;
  function search(): State | null {
    if (++nodes > budget) return null;
    let cell = -1,
      candidates: number[] = [];
    for (let i = 0; i < rotations.length; i++) {
      if (rotations[i] !== -1) continue;
      const valid = options[i]!.filter((r) =>
        [0, 1, 2, 3].every((d) => {
          const n = adjacent(p, i, d);
          return (
            n == null ||
            rotations[n] === -1 ||
            p.ports[i]!.some((v) => (v + r) % 4 === d) ===
              p.ports[n]!.some((v) => (v + rotations[n]!) % 4 === (d + 2) % 4)
          );
        }),
      );
      if (!valid.length) return null;
      if (cell === -1 || valid.length < candidates.length) {
        cell = i;
        candidates = valid;
      }
    }
    if (cell === -1) {
      const state = { rotations: [...rotations] };
      return engine.solved(p, state) ? state : null;
    }
    for (const r of candidates) {
      rotations[cell] = r;
      const answer = search();
      if (answer) return answer;
      if (nodes > budget) break;
    }
    rotations[cell] = -1;
    return null;
  }
  return search();
}
function pipeHint(p: Payload, s: State, used: number): string | null {
  const answer = pipeSolution(p);
  if (!answer) return null;
  const cell = p.ports.findIndex(
    (_, i) => portsAt(p, s, i).sort().join() !== portsAt(p, answer, i).sort().join(),
  );
  if (cell < 0) return null;
  if (used === 0)
    return `Look in row ${Math.floor(cell / p.cols) + 1}. One of its pipes needs a different turn in a working arrangement.`;
  if (used === 1)
    return `Focus on the pipe at ${cellName(cell, p.cols)}. Its openings can be turned to join a complete network.`;
  const turns = (answer.rotations[cell]! - s.rotations[cell]! + 4) % 4;
  const direction = `clockwise ${turns === 1 ? 'once' : turns === 2 ? 'twice' : 'three times'}`;
  const openings = portsAt(p, answer, cell)
    .map((d) => ['up', 'right', 'down', 'left'][d])
    .join(' and ');
  return `One working arrangement: turn the pipe at ${cellName(cell, p.cols)} ${direction}, so its openings face ${openings}.`;
}
