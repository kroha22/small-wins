import { describe, it, expect } from 'vitest';
import { engine, connections, type Payload } from './model';
const p: Payload = {
  rows: 2,
  cols: 2,
  sourceCell: 0,
  ports: [
    [0, 1],
    [0, 1],
    [0, 1],
    [0, 1],
  ],
  initialRotations: [0, 2, 0, 3],
};
describe('Pipes', () => {
  it('accepts a closed connected loop', () => {
    const t = engine.apply(p, engine.initial(p), { type: 'rotate', cell: 0, quarterTurns: 1 });
    expect(engine.solved(p, t.state)).toBe(true);
    expect(connections(p, t.state).leaks).toHaveLength(0);
  });
  it('does not mistake reached cells with leaks for victory', () => {
    const p2 = {
      ...p,
      ports: [
        [0, 1, 2],
        [0, 1],
        [0, 1],
        [0, 1],
      ],
    };
    const s = { rotations: [1, 2, 0, 3] };
    expect(connections(p2, s).reached.size).toBe(4);
    expect(engine.solved(p2, s)).toBe(false);
  });
  it('treats fully symmetric turns as no-ops', () => {
    const l = { ...p, ports: [[0, 1, 2, 3], ...p.ports.slice(1)] };
    expect(
      engine.apply(l, engine.initial(l), { type: 'rotate', cell: 0, quarterTurns: 1 }).changed,
    ).toBe(false);
  });
  it('rejects out-of-board rotations and malformed saves', () => {
    const s = engine.initial(p);
    expect(engine.apply(p, s, { type: 'rotate', cell: -1, quarterTurns: 1 }).state).toBe(s);
    expect(engine.validState(p, { rotations: [0, 0, 0] })).toBe(false);
  });
  it('rotates both ways without mutation', () => {
    const s = engine.initial(p);
    const t = engine.apply(p, s, { type: 'rotate', cell: 0, quarterTurns: -1 });
    expect(t.state.rotations[0]).toBe(3);
    expect(s.rotations[0]).toBe(0);
  });
});
