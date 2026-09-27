import { z } from 'zod';
const prefix = 'small-wins:v1:';
const memory = new Map<string, string>();
let available = true;
const listeners = new Set<() => void>();
export const storageAvailable = () => available;
export const subscribeStorage = (fn: () => void) => {
  listeners.add(fn);
  return () => {
    listeners.delete(fn);
  };
};
function unavailable() {
  if (available) {
    available = false;
    listeners.forEach((fn) => fn());
  }
}
export function readRecord(key: string): unknown {
  let raw: string | null | undefined;
  try {
    raw =
      !available || typeof localStorage === 'undefined'
        ? memory.get(key)
        : localStorage.getItem(prefix + key);
    if (raw) memory.set(key, raw);
  } catch {
    unavailable();
    raw = memory.get(key);
  }
  try {
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}
export function writeRecord(key: string, value: unknown) {
  const raw = JSON.stringify(value);
  memory.set(key, raw);
  try {
    if (typeof localStorage !== 'undefined') localStorage.setItem(prefix + key, raw);
  } catch {
    unavailable();
  }
}
export function clearRecords() {
  memory.clear();
  for (const key of ['progress', 'settings', 'active-attempt']) {
    try {
      if (typeof localStorage !== 'undefined') localStorage.removeItem(prefix + key);
    } catch {
      unavailable();
    }
  }
}
export const attemptSchema = z.object({
  schemaVersion: z.literal(1),
  gameId: z.string(),
  levelId: z.string(),
  contentVersion: z.number().int(),
  attemptId: z.string().min(1),
  state: z.unknown(),
  history: z.array(z.unknown()).max(20000),
  moveCount: z.number().int().nonnegative(),
  hintsUsed: z.number().int().nonnegative(),
  revision: z.number().int().nonnegative(),
});
