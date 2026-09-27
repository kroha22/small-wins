// @vitest-environment jsdom
import { beforeEach, describe, it, expect, vi, afterEach } from 'vitest';
import { readRecord, writeRecord, clearRecords } from './storage';
import { restoreSession } from '../game-session/use-session';
import { useProgress } from './store';
import { engine, type Payload } from '../../games/waypoints/model';
const level = {
  id: 'tutorial',
  title: 'Walk',
  difficulty: 'tutorial' as const,
  contentVersion: 1 as const,
  payload: { rows: 3, cols: 3, start: 0, target: 2, waypoints: [1], blocked: [] } satisfies Payload,
};
beforeEach(() => {
  clearRecords();
  useProgress.getState().clear();
});
afterEach(() => vi.restoreAllMocks());
describe('Persistence boundary', () => {
  it('ignores malformed JSON', () => {
    localStorage.setItem('small-wins:v1:active-attempt', '{broken');
    expect(readRecord('active-attempt')).toBeNull();
  });
  it('restarts a corrupt domain state without clearing progress', () => {
    useProgress.getState().complete('waypoints/level-1/1', { moves: 2, assisted: false });
    writeRecord('active-attempt', {
      schemaVersion: 1,
      gameId: 'waypoints',
      levelId: 'tutorial',
      contentVersion: 1,
      attemptId: 'old',
      state: { path: [0, 8] },
      history: [],
      moveCount: 1,
      hintsUsed: 0,
      revision: 1,
    });
    const s = restoreSession('waypoints', level, engine, 'new');
    expect(s.state.path).toEqual([0]);
    expect(s.attemptId).toBe('new');
    expect(useProgress.getState().completed['waypoints/level-1/1']).toBeDefined();
  });
  it('restores matching state and rejects changed content versions', () => {
    const saved = {
      schemaVersion: 1,
      gameId: 'waypoints',
      levelId: 'tutorial',
      contentVersion: 1,
      attemptId: 'old',
      state: { path: [0, 1] },
      history: [{ path: [0] }],
      moveCount: 1,
      hintsUsed: 2,
      revision: 1,
    };
    writeRecord('active-attempt', saved);
    expect(restoreSession('waypoints', level, engine, 'new').state.path).toEqual([0, 1]);
    writeRecord('active-attempt', { ...saved, contentVersion: 2 });
    expect(restoreSession('waypoints', level, engine, 'new').attemptId).toBe('new');
  });
  it('keeps completion idempotent', () => {
    const store = useProgress.getState();
    store.complete('pipes/level-1/1', { moves: 4, assisted: false });
    store.complete('pipes/level-1/1', { moves: 7, assisted: true });
    expect(useProgress.getState().completed['pipes/level-1/1']).toEqual({
      moves: 4,
      assisted: false,
    });
  });
  it('does not crash on quota or security errors', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('No storage', 'QuotaExceededError');
    });
    expect(() => writeRecord('progress', { done: true })).not.toThrow();
  });
});
