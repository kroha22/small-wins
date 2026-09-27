import { describe, it, expect } from 'vitest';
import { freshSession, sessionReducer } from './reducer';
import { engine, type Payload } from '../../games/waypoints/model';
const p: Payload = { rows: 3, cols: 3, start: 0, target: 2, waypoints: [1], blocked: [] };
const reduce = sessionReducer(engine, p);
describe('Session history', () => {
  it('counts only changed actions and keeps moves monotonic across undo', () => {
    let s = freshSession(engine.initial(p), 'one');
    s = reduce(s, { type: 'play', action: { type: 'addCell', cell: 4 } });
    expect(s.moveCount).toBe(0);
    expect(s.history).toHaveLength(0);
    s = reduce(s, { type: 'play', action: { type: 'addCell', cell: 1 } });
    s = reduce(s, { type: 'undo' });
    expect(s.moveCount).toBe(1);
    expect(s.state.path).toEqual([0]);
  });
  it('blocks ordinary actions after victory, but allows undo', () => {
    let s = freshSession(engine.initial(p), 'one');
    for (const cell of [1, 2]) s = reduce(s, { type: 'play', action: { type: 'addCell', cell } });
    expect(reduce(s, { type: 'play', action: { type: 'addCell', cell: 5 } })).toBe(s);
    expect(engine.solved(p, reduce(s, { type: 'undo' }).state)).toBe(false);
  });
  it('ignores stale hints, preserves hint count on undo, resets only attempt metrics', () => {
    let s = freshSession(engine.initial(p), 'one');
    s = reduce(s, { type: 'play', action: { type: 'addCell', cell: 1 } });
    s = reduce(s, { type: 'hint', text: 'stale', revision: 0 });
    expect(s.hintsUsed).toBe(0);
    s = reduce(s, { type: 'hint', text: 'valid', revision: 1 });
    s = reduce(s, { type: 'undo' });
    expect(s.hintsUsed).toBe(1);
    const next = reduce(s, { type: 'restart', attemptId: 'two' });
    expect(next.attemptId).toBe('two');
    expect(next.hintsUsed).toBe(0);
    expect(next.moveCount).toBe(0);
    expect(next.history).toEqual([]);
  });
});
