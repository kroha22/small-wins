// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from 'vitest';
import { beginPlaytest, clearPlaytestRecords, finishPlaytest, getPlaytestRecords, updatePlaytest } from './log';

const level = { id: 'tutorial', title: 'Start', difficulty: 'tutorial' as const, contentVersion: 1 };
describe('playtest records', () => {
  beforeEach(() => { localStorage.clear(); clearPlaytestRecords(); });
  it('records progress and closes an attempt with timing metadata', () => {
    const id = beginPlaytest('pipes', level);
    updatePlaytest(id, { moves: 3, hintsUsed: 1, restarts: 2, solved: true });
    finishPlaytest(id, 'completed');
    expect(getPlaytestRecords()[0]).toMatchObject({ gameId: 'pipes', moves: 3, hintsUsed: 1, restarts: 2, solved: true, result: 'completed' });
    expect(getPlaytestRecords()[0]!.elapsedMs).toBeGreaterThanOrEqual(0);
  });
});
