import { useSyncExternalStore } from 'react';
import type { Point } from '../../games/untangle/model';

type Context = { levelId: string; contentVersion: number; nodeId: string };
type Event = Context &
  (
    | { event: 'drag-start'; pointerType: string; from: Point }
    | { event: 'drag-capture-lost'; position: Point }
    | { event: 'drag-release'; source: 'pointerup' | 'buttons-released'; to: Point }
    | {
        event: 'drag-cancel';
        reason:
          | 'escape'
          | 'pointer-cancel'
          | 'capture-lost'
          | 'blur'
          | 'hidden'
          | 'undo'
          | 'restart'
          | 'unmount';
      }
    | {
        event: 'move-accepted' | 'move-rejected' | 'move-unchanged';
        input: 'drag' | 'keyboard' | 'tap';
        from: Point;
        to: Point;
        reason?: string;
        positions: Record<string, Point>;
      }
  );
export type Diagnostic = Event & { sequence: number; at: string; gameId: 'untangle' };
const limit = 200;
let entries: Diagnostic[] = [];
let sequence = 0;
const listeners = new Set<() => void>();
const notify = () => listeners.forEach((listener) => listener());
export function logDiagnostic(event: Event) {
  // Only allowlisted game data; never DOM, URLs, storage contents or arbitrary errors.
  const entry = structuredClone({
    ...event,
    sequence: ++sequence,
    at: new Date().toISOString(),
    gameId: 'untangle' as const,
  });
  entries = [...entries.slice(-(limit - 1)), entry];
  notify();
}
export function clearDiagnostics() {
  entries = [];
  notify();
}
export const getDiagnostics = () => entries;
export function diagnosticsJson() {
  return JSON.stringify(
    { schemaVersion: 1, interactionVersion: 2, app: 'small-wins', events: entries },
    null,
    2,
  );
}
export function useDiagnosticCount() {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
    () => entries.length,
    () => 0,
  );
}
