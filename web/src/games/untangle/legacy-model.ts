import { z } from 'zod';
import { accept, reject, type Engine } from '../../shared/lib/engine';
export const pointSchema = z.object({ x: z.number().finite(), y: z.number().finite() });
export type Point = z.infer<typeof pointSchema>;
export const payloadSchema = z
  .object({
    nodes: z
      .array(pointSchema.extend({ id: z.string(), fixed: z.boolean().default(false) }))
      .min(4)
      .max(10),
    edges: z.array(z.tuple([z.string(), z.string()])),
    bounds: z.object({ min: z.number(), max: z.number() }).default({ min: 8, max: 92 }),
    minNodeDistance: z.number().positive().default(12),
  })
  .superRefine((p, c) => {
    const ids = p.nodes.map((n) => n.id);
    const pairs = p.edges.map((e) => [...e].sort().join('|'));
    if (
      new Set(ids).size !== ids.length ||
      new Set(pairs).size !== pairs.length ||
      p.edges.some(([a, b]) => a === b || !ids.includes(a) || !ids.includes(b)) ||
      p.bounds.min >= p.bounds.max ||
      p.nodes.some(
        (n) => n.x < p.bounds.min || n.x > p.bounds.max || n.y < p.bounds.min || n.y > p.bounds.max,
      )
    )
      c.addIssue({ code: 'custom', message: 'Invalid graph' });
  });
export type Payload = z.infer<typeof payloadSchema>;
export type State = { positions: Record<string, Point> };
export type Action = { type: 'moveNode'; id: string; x: number; y: number };
const eps = 1e-6;
const cross = (a: Point, b: Point, c: Point) =>
  (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
export const onSegment = (a: Point, b: Point, p: Point) =>
  Math.abs(cross(a, b, p)) < eps &&
  p.x >= Math.min(a.x, b.x) - eps &&
  p.x <= Math.max(a.x, b.x) + eps &&
  p.y >= Math.min(a.y, b.y) - eps &&
  p.y <= Math.max(a.y, b.y) + eps;
export function intersects(a: Point, b: Point, c: Point, d: Point) {
  const abC = cross(a, b, c),
    abD = cross(a, b, d),
    cdA = cross(c, d, a),
    cdB = cross(c, d, b);
  return (
    (((abC > eps && abD < -eps) || (abC < -eps && abD > eps)) &&
      ((cdA > eps && cdB < -eps) || (cdA < -eps && cdB > eps))) ||
    onSegment(a, b, c) ||
    onSegment(a, b, d) ||
    onSegment(c, d, a) ||
    onSegment(c, d, b)
  );
}
export function conflicts(p: Payload, s: State) {
  const edges = new Set<number>();
  let count = 0;
  for (let i = 0; i < p.edges.length; i++)
    for (let j = i + 1; j < p.edges.length; j++) {
      const e = p.edges[i]!,
        f = p.edges[j]!,
        shared = e.find((id) => f.includes(id));
      let hit: boolean;
      if (shared) {
        const a = e.find((id) => id !== shared)!,
          b = f.find((id) => id !== shared)!;
        hit =
          onSegment(s.positions[shared]!, s.positions[a]!, s.positions[b]!) ||
          onSegment(s.positions[shared]!, s.positions[b]!, s.positions[a]!);
      } else
        hit = intersects(
          s.positions[e[0]]!,
          s.positions[e[1]]!,
          s.positions[f[0]]!,
          s.positions[f[1]]!,
        );
      if (hit) {
        edges.add(i);
        edges.add(j);
        count++;
      }
    }
  p.edges.forEach(([a, b], i) =>
    p.nodes.forEach((n) => {
      if (
        n.id !== a &&
        n.id !== b &&
        onSegment(s.positions[a]!, s.positions[b]!, s.positions[n.id]!)
      ) {
        edges.add(i);
        count++;
      }
    }),
  );
  return { edges, count };
}
const stateSchema = z.object({ positions: z.record(z.string(), pointSchema) });
export const engine: Engine<Payload, State, Action> = {
  initial: (p) => ({
    positions: Object.fromEntries(p.nodes.map((n) => [n.id, { x: n.x, y: n.y }])),
  }),
  validState: (p, s): s is State => {
    const v = stateSchema.safeParse(s);
    if (!v.success) return false;
    const entries = Object.entries(v.data.positions);
    return (
      entries.length === p.nodes.length &&
      entries.every(([id, n]) => {
        const orig = p.nodes.find((v) => v.id === id);
        return (
          !!orig &&
          n.x >= p.bounds.min &&
          n.x <= p.bounds.max &&
          n.y >= p.bounds.min &&
          n.y <= p.bounds.max &&
          (!orig.fixed || (n.x === orig.x && n.y === orig.y))
        );
      }) &&
      entries.every(([, a], i) =>
        entries
          .slice(i + 1)
          .every(([, b]) => Math.hypot(a.x - b.x, a.y - b.y) >= p.minNodeDistance - eps),
      )
    );
  },
  apply(p, s, a) {
    const n = p.nodes.find((n) => n.id === a.id);
    if (a.type !== 'moveNode' || !n || n.fixed)
      return reject(s, 'This node is fixed. Choose an unpinned node.');
    if (
      !Number.isFinite(a.x) ||
      !Number.isFinite(a.y) ||
      a.x < p.bounds.min ||
      a.x > p.bounds.max ||
      a.y < p.bounds.min ||
      a.y > p.bounds.max
    )
      return reject(s, 'Keep the node inside the board.');
    const pos = { x: Math.round(a.x * 10) / 10, y: Math.round(a.y * 10) / 10 };
    if (
      Object.entries(s.positions).some(
        ([id, v]) => id !== a.id && Math.hypot(v.x - pos.x, v.y - pos.y) < p.minNodeDistance - eps,
      )
    )
      return reject(s, 'Leave a little space between the nodes.');
    if (s.positions[a.id]!.x === pos.x && s.positions[a.id]!.y === pos.y) return accept(s, false);
    return accept({ positions: { ...s.positions, [a.id]: pos } });
  },
  solved: (p, s) => conflicts(p, s).count === 0,
};
