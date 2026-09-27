import { describe, expect, it } from 'vitest';
import { engine, payloadSchema, solutions, type Payload } from './model';

const payload: Payload = {
  rows: 2,
  cols: 3,
  clues: [
    { cell: 0, area: 2 },
    { cell: 2, area: 2 },
    { cell: 4, area: 2 },
  ],
  initialRegions: [-1, -1, -1, -1, -1, -1],
};

describe('Shikaku model', () => {
  it('accepts a valid level and rejects malformed region references', () => {
    expect(payloadSchema.safeParse(payload).success).toBe(true);
    expect(payloadSchema.safeParse({ ...payload, initialRegions: [9] }).success).toBe(false);
  });

  it('places a rectangle only when it contains its clue and matches the area', () => {
    const state = engine.initial(payload);
    expect(
      engine.apply(payload, state, { type: 'place', clue: 0, top: 0, left: 0, bottom: 0, right: 1 }).changed,
    ).toBe(true);
    expect(
      engine.apply(payload, state, { type: 'place', clue: 0, top: 0, left: 1, bottom: 1, right: 1 }).state,
    ).toEqual(state);
  });

  it('rejects overlap with another region', () => {
    let state = engine.initial(payload);
    state = engine.apply(payload, state, { type: 'place', clue: 0, top: 0, left: 0, bottom: 0, right: 1 }).state;
    const result = engine.apply(payload, state, {
      type: 'place', clue: 1, top: 0, left: 1, bottom: 1, right: 1,
    });
    expect(result.changed).toBe(false);
  });

  it('recognises a complete tiling', () => {
    let state = engine.initial(payload);
    for (const action of [
      { type: 'place' as const, clue: 0, top: 0, left: 0, bottom: 0, right: 1 },
      { type: 'place' as const, clue: 1, top: 0, left: 2, bottom: 1, right: 2 },
      { type: 'place' as const, clue: 2, top: 1, left: 0, bottom: 1, right: 1 },
    ]) state = engine.apply(payload, state, action).state;
    expect(engine.solved(payload, state)).toBe(true);
  });

  it('bounds search and exposes that the starter puzzle is not yet unique', () => {
    expect(solutions(payload, 2)).toHaveLength(2);
  });

  it('provides progressive hints from a verified solution', () => {
    const hint = engine.hint!(payload, engine.initial(payload), 0);
    expect(hint).toContain('clue 2');
    expect(engine.hint!(payload, engine.initial(payload), 1)).toContain('row');
  });

  it('does not mutate the previous state', () => {
    const state = engine.initial(payload);
    engine.apply(payload, state, { type: 'place', clue: 0, top: 0, left: 0, bottom: 0, right: 1 });
    expect(state.regions).toEqual([-1, -1, -1, -1, -1, -1]);
  });
});
