import { describe, it, expect } from 'vitest';
import { engine, type Payload } from './model';
const p: Payload = { rows: 3, cols: 3, start: 0, target: 6, blocked: [4], waypoints: [2, 8] };
describe('Waypoints', () => {
  it('solves a complete simple path through all markers', () => {
    let s = engine.initial(p);
    for (const cell of [1, 2, 5, 8, 7, 6]) {
      const t = engine.apply(p, s, { type: 'addCell', cell });
      expect(t.accepted).toBe(true);
      s = t.state;
    }
    expect(engine.solved(p, s)).toBe(true);
    expect(engine.validState(p, s)).toBe(true);
  });
  it('rejects diagonal, blocked and already visited cells', () => {
    const s = { path: [0, 1] };
    for (const cell of [0, 4, 5])
      expect(engine.apply(p, s, { type: 'addCell', cell }).accepted).toBe(false);
  });
  it('rejects early finish and wrapping between rows', () => {
    expect(engine.apply(p, { path: [0, 3] }, { type: 'addCell', cell: 6 }).accepted).toBe(false);
    expect(engine.apply(p, { path: [0, 1, 2] }, { type: 'addCell', cell: 3 }).accepted).toBe(false);
  });
  it('accepts a dead end as a hypothesis and suggests undo', () => {
    const l = { ...p, blocked: [4, 5] };
    const t = engine.apply(l, { path: [0, 1] }, { type: 'addCell', cell: 2 });
    expect(t.accepted).toBe(true);
    expect(engine.hint!(l, t.state, 1)).toContain('dead end');
  });
  it('checks the entire saved path', () => {
    expect(engine.validState(p, { path: [0, 1, 2, 8, 7, 6] })).toBe(false);
    expect(engine.validState(p, { path: [0, 3, 6, 7, 8] })).toBe(false);
  });
});
