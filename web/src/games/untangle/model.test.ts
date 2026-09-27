import { describe, it, expect } from 'vitest';
import { engine, intersects, conflicts, type Payload } from './model';
const p: Payload = {
  nodes: [
    { id: 'A', x: 20, y: 20, fixed: false },
    { id: 'B', x: 80, y: 80, fixed: false },
    { id: 'C', x: 80, y: 20, fixed: false },
    { id: 'D', x: 20, y: 80, fixed: false },
  ],
  edges: [
    ['A', 'B'],
    ['B', 'C'],
    ['C', 'D'],
    ['D', 'A'],
  ],
  bounds: { min: 8, max: 92 },
  minNodeDistance: 12,
};
describe('Untangle', () => {
  it('allows intermediate crossings and recognizes a legal final embedding', () => {
    let s = engine.initial(p);
    expect(engine.solved(p, s)).toBe(false);
    s = engine.apply(p, s, { type: 'moveNode', id: 'B', x: 80, y: 60 }).state;
    expect(engine.solved(p, s)).toBe(false);
    s = engine.apply(p, s, { type: 'moveNode', id: 'C', x: 80, y: 80 }).state;
    expect(engine.solved(p, s)).toBe(true);
  });
  it('detects collinear overlap and endpoint touch', () => {
    expect(intersects({ x: 0, y: 0 }, { x: 8, y: 0 }, { x: 4, y: 0 }, { x: 10, y: 0 })).toBe(true);
    expect(intersects({ x: 0, y: 0 }, { x: 8, y: 0 }, { x: 4, y: 0 }, { x: 4, y: 5 })).toBe(true);
  });
  it('allows incident endpoints but not overlapping incident edges', () => {
    const l = {
      ...p,
      edges: [
        ['A', 'B'],
        ['A', 'C'],
      ] as [string, string][],
    };
    const s = {
      positions: {
        A: { x: 20, y: 20 },
        B: { x: 80, y: 20 },
        C: { x: 50, y: 20 },
        D: { x: 20, y: 80 },
      },
    };
    expect(conflicts(l, s).count).toBeGreaterThan(0);
    expect(conflicts(l, engine.initial(l)).count).toBe(0);
  });
  it('detects a foreign node on an edge even without another incident edge', () => {
    const l = { ...p, edges: [['A', 'B']] as [string, string][] };
    const s = {
      positions: {
        A: { x: 20, y: 20 },
        B: { x: 80, y: 20 },
        C: { x: 50, y: 20 },
        D: { x: 20, y: 80 },
      },
    };
    expect(engine.solved(l, s)).toBe(false);
  });
  it('rejects near nodes, non-finite points, bounds and fixed node movement', () => {
    const l = { ...p, nodes: p.nodes.map((n) => ({ ...n, fixed: n.id === 'A' })) };
    const s = engine.initial(l);
    for (const action of [
      { id: 'A', x: 30, y: 30 },
      { id: 'B', x: 20, y: 21 },
      { id: 'B', x: 95, y: 50 },
      { id: 'B', x: NaN, y: 50 },
    ])
      expect(engine.apply(l, s, { type: 'moveNode', ...action }).accepted).toBe(false);
  });
});
