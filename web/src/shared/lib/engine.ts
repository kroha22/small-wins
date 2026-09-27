import { z } from 'zod';

export const gameIds = ['purrdoku', 'pipes', 'untangle', 'waypoints', 'shikaku', 'wildlife-survey'] as const;
export type GameId = (typeof gameIds)[number];
export const metadataSchema = z.object({
  id: z.string(),
  title: z.string(),
  difficulty: z.enum(['tutorial', 'easy', 'medium', 'hard']),
  contentVersion: z.number().int().positive(),
});
export type Metadata = z.infer<typeof metadataSchema>;
export type Level<P> = Metadata & { payload: P };
export type Transition<S> = { accepted: boolean; changed: boolean; state: S; reason?: string };
export interface Engine<P, S, A> {
  initial: (level: P) => S;
  apply: (level: P, state: S, action: A) => Transition<S>;
  solved: (level: P, state: S) => boolean;
  validState: (level: P, state: unknown) => state is S;
  hint?: (level: P, state: S, used: number) => string | null;
}
export const accept = <S>(state: S, changed = true): Transition<S> => ({
  accepted: true,
  changed,
  state,
});
export const reject = <S>(state: S, reason: string): Transition<S> => ({
  accepted: false,
  changed: false,
  state,
  reason,
});
export const cellName = (cell: number, cols: number) =>
  `row ${Math.floor(cell / cols) + 1}, column ${(cell % cols) + 1}`;
export const neighbours = (cell: number, rows: number, cols: number): number[] =>
  [cell - cols, cell + 1, cell + cols, cell - 1].filter(
    (n) =>
      n >= 0 &&
      n < rows * cols &&
      Math.abs((n % cols) - (cell % cols)) +
        Math.abs(Math.floor(n / cols) - Math.floor(cell / cols)) ===
        1,
  );
