import { describe, expect, it } from 'vitest';
import { engine, payloadSchema, type Payload } from './model';

const payload: Payload = {
  rows: 4,
  cols: 4,
  theme: 'Pond Life',
  rowCounts: [1, 1, 1, 1],
  colCounts: [1, 1, 1, 1],
  chains: [{ id: 'fish-a', length: 2, species: 'turtle' }, { id: 'fish-b', length: 2, species: 'turtle' }],
  occupied: [0, 5, 10, 15],
};

describe('Wildlife Survey model', () => {
  it('validates matching row, column and chain totals', () => {
    expect(payloadSchema.safeParse(payload).success).toBe(true);
    expect(payloadSchema.safeParse({ ...payload, colCounts: [2, 1, 1, 1] }).success).toBe(false);
  });

  it('toggles a mark without mutating the previous state', () => {
    const state = engine.initial(payload);
    const next = engine.apply(payload, state, { type: 'mark', cell: 0, mark: 'found' }).state;
    expect(next.marks[0]).toBe('found');
    expect(engine.apply(payload, next, { type: 'mark', cell: 0, mark: 'found' }).state.marks[0]).toBe('unknown');
    expect(state.marks.every((mark) => mark === 'unknown')).toBe(true);
  });

  it('requires every occupied and empty cell to be marked correctly', () => {
    let state = engine.initial(payload);
    for (let cell = 0; cell < 16; cell += 1)
      state = engine.apply(payload, state, { type: 'mark', cell, mark: payload.occupied.includes(cell) ? 'found' : 'empty' }).state;
    expect(engine.solved(payload, state)).toBe(true);
  });

  it('offers a broad hint before a concrete mark', () => {
    const state = engine.initial(payload);
    expect(engine.hint!(payload, state, 0)).toContain('Scan row');
    expect(engine.hint!(payload, state, 1)).toContain('column');
  });
});
