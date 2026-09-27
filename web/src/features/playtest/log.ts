import { useSyncExternalStore } from 'react';
import type { GameId, Metadata } from '../../shared/lib/engine';

const enabledKey = 'small-wins:v1:playtest-enabled';
const recordsKey = 'small-wins:v1:playtest-records';
const listeners = new Set<() => void>();
let snapshot = { enabled: false, count: 0 };
const refreshSnapshot = () => {
  const next = { enabled: isPlaytestEnabled(), count: memory.length };
  if (next.enabled !== snapshot.enabled || next.count !== snapshot.count) snapshot = next;
};
const notify = () => { refreshSnapshot(); listeners.forEach((listener) => listener()); };
export type PlaytestRecord = {
  id: string; gameId: GameId; levelId: string; difficulty: Metadata['difficulty'];
  startedAt: string; finishedAt?: string; elapsedMs: number; moves: number;
  hintsUsed: number; restarts: number; solved: boolean; result?: 'completed' | 'abandoned';
};
let memory: PlaytestRecord[] = [];
function read() {
  try {
    const raw = localStorage.getItem(recordsKey);
    if (raw) memory = JSON.parse(raw) as PlaytestRecord[];
  } catch { /* keep the in-memory copy */ }
  return memory;
}
function write() {
  memory = memory.slice(-500);
  try { localStorage.setItem(recordsKey, JSON.stringify(memory)); } catch { /* memory fallback */ }
  notify();
}
export function isPlaytestEnabled() {
  try { return localStorage.getItem(enabledKey) === '1'; } catch { return false; }
}
export function setPlaytestEnabled(enabled: boolean) {
  try {
    if (enabled) localStorage.setItem(enabledKey, '1');
    else localStorage.removeItem(enabledKey);
  } catch { /* the UI remains usable for this tab */ }
  notify();
}
export function beginPlaytest(gameId: GameId, level: Metadata) {
  const id = crypto.randomUUID();
  read();
  memory.push({ id, gameId, levelId: level.id, difficulty: level.difficulty, startedAt: new Date().toISOString(), elapsedMs: 0, moves: 0, hintsUsed: 0, restarts: 0, solved: false });
  write();
  return id;
}
export function updatePlaytest(id: string, patch: Pick<PlaytestRecord, 'moves' | 'hintsUsed' | 'restarts' | 'solved'>) {
  const item = memory.find((record) => record.id === id);
  if (!item || item.result) return;
  Object.assign(item, patch);
  item.elapsedMs = Math.max(0, Date.now() - Date.parse(item.startedAt));
  write();
}
export function finishPlaytest(id: string, result: PlaytestRecord['result']) {
  const item = memory.find((record) => record.id === id);
  if (!item || item.result) return;
  item.result = result;
  item.finishedAt = new Date().toISOString();
  item.elapsedMs = Math.max(0, Date.parse(item.finishedAt) - Date.parse(item.startedAt));
  if (item.elapsedMs < 1000 && item.moves === 0 && item.hintsUsed === 0 && item.restarts === 0) {
    memory = memory.filter((record) => record.id !== id);
    write();
    return;
  }
  write();
}
export function getPlaytestRecords() { return read(); }
export function clearPlaytestRecords() {
  memory = [];
  try { localStorage.removeItem(recordsKey); } catch { /* memory clear still applies */ }
  notify();
}
export function playtestJson() {
  return JSON.stringify({ schemaVersion: 1, app: 'small-wins', records: getPlaytestRecords() }, null, 2);
}
export function usePlaytestSnapshot() {
  read();
  refreshSnapshot();
  return useSyncExternalStore(
    (listener) => { listeners.add(listener); return () => listeners.delete(listener); },
    () => snapshot,
    () => ({ enabled: false, count: 0 }),
  );
}
