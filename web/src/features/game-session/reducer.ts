import { type Engine } from '../../shared/lib/engine';
export type Session<S> = {
  attemptId: string;
  state: S;
  history: S[];
  moveCount: number;
  hintsUsed: number;
  revision: number;
  message: string;
  hint: string | null;
};
export type SessionAction<A> =
  | { type: 'play'; action: A }
  | { type: 'undo' }
  | { type: 'restart'; attemptId: string }
  | { type: 'hint'; text: string; revision: number }
  | { type: 'message'; text: string };
export const freshSession = <S>(state: S, attemptId: string): Session<S> => ({
  attemptId,
  state,
  history: [],
  moveCount: 0,
  hintsUsed: 0,
  revision: 0,
  message: '',
  hint: null,
});
export function sessionReducer<P, S, A>(engine: Engine<P, S, A>, level: P) {
  return (s: Session<S>, a: SessionAction<A>): Session<S> => {
    if (a.type === 'restart')
      return {
        ...freshSession(engine.initial(level), a.attemptId),
        message: 'A fresh start. You have got this.',
      };
    if (a.type === 'message') return { ...s, message: a.text };
    if (a.type === 'hint')
      return a.revision === s.revision
        ? { ...s, hintsUsed: s.hintsUsed + 1, hint: a.text, message: 'Hint ready.' }
        : s;
    if (a.type === 'undo') {
      const previous = s.history.at(-1);
      return previous
        ? {
            ...s,
            state: previous,
            history: s.history.slice(0, -1),
            revision: s.revision + 1,
            hint: null,
            message: 'Last move undone.',
          }
        : s;
    }
    if (engine.solved(level, s.state)) return s;
    const t = engine.apply(level, s.state, a.action);
    if (!t.accepted) return { ...s, message: t.reason ?? 'That move is not available.' };
    if (!t.changed) return s;
    return {
      ...s,
      state: t.state,
      history: [...s.history, s.state],
      moveCount: s.moveCount + 1,
      revision: s.revision + 1,
      hint: null,
      message: engine.solved(level, t.state) ? 'Puzzle complete. A small win!' : 'Move placed.',
    };
  };
}
