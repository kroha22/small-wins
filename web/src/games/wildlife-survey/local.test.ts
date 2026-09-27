import { describe, expect, it } from 'vitest';
import levels from './levels.json';
import { engine, localEngine, payloadSchema } from './model';
const payload = payloadSchema.parse(levels[0]!.payload);
describe('wildlife local mode', () => {
  it('keeps player marks private while passing the device', () => {
    let state = localEngine.initial(payload);
    state = localEngine.apply(payload, state, { type: 'mark', cell: 0, mark: 'found' }).state;
    expect(state.players[0]!.marks[0]).toBe('found');
    expect(state.players[1]!.marks[0]).toBe('unknown');
    state = localEngine.apply(payload, state, { type: 'pass' }).state;
    expect(state.activePlayer).toBe(1);
  });
  it('uses each player local solution board', () => {
    let state = localEngine.initial(payload);
    for (const cell of Array.from({ length: payload.rows * payload.cols }, (_, i) => i)) {
      const mark = payload.localOccupied![0]!.includes(cell) ? 'found' : 'empty';
      state = localEngine.apply(payload, state, { type: 'mark', cell, mark }).state;
    }
    expect(engine.solved({ ...payload, occupied: payload.localOccupied![0]! }, state.players[0]!)).toBe(true);
  });
});
