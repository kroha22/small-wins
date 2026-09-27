import { useEffect, useReducer } from 'react';
import { type Engine, type GameId, type Level } from '../../shared/lib/engine';
import { sessionReducer, freshSession } from './reducer';
import { attemptSchema, readRecord, writeRecord } from '../progress/storage';
import { useProgress, completionKey } from '../progress/store';

export function restoreSession<P, S, A>(
  gameId: GameId,
  level: Level<P>,
  engine: Engine<P, S, A>,
  attemptId: string,
) {
  const raw = readRecord('active-attempt'),
    parsed = attemptSchema.safeParse(raw);
  if (parsed.success) {
    const s = parsed.data;
    if (
      s.gameId === gameId &&
      s.levelId === level.id &&
      s.contentVersion === level.contentVersion &&
      engine.validState(level.payload, s.state) &&
      s.history.every((h) => engine.validState(level.payload, h)) &&
      s.moveCount >= s.history.length
    ) {
      return {
        ...s,
        state: s.state,
        history: s.history as S[],
        message: 'Your last puzzle is ready.',
        hint: null,
      };
    }
  }
  return {
    ...freshSession(engine.initial(level.payload), attemptId),
    message: raw ? 'A fresh attempt. Only your latest compatible puzzle is resumed.' : '',
  };
}
export function useSession<P, S, A>(gameId: GameId, level: Level<P>, engine: Engine<P, S, A>) {
  const [session, dispatch] = useReducer(sessionReducer(engine, level.payload), null, () =>
    restoreSession(gameId, level, engine, crypto.randomUUID()),
  );
  useEffect(() => {
    document.querySelector<HTMLElement>('h1')?.focus({ preventScroll: true });
  }, []);
  const complete = useProgress((s) => s.complete);
  const solved = engine.solved(level.payload, session.state);
  useEffect(() => {
    writeRecord('active-attempt', {
      schemaVersion: 1,
      gameId,
      levelId: level.id,
      contentVersion: level.contentVersion,
      ...session,
    });
  }, [gameId, level.id, level.contentVersion, session]);
  useEffect(() => {
    if (solved)
      complete(completionKey(gameId, level.id, level.contentVersion), {
        moves: session.moveCount,
        assisted: session.hintsUsed > 0,
      });
  }, [
    solved,
    complete,
    gameId,
    level.id,
    level.contentVersion,
    session.moveCount,
    session.hintsUsed,
  ]);
  return {
    session,
    dispatch,
    solved,
    play: (action: A) => dispatch({ type: 'play', action }),
    undo: () => dispatch({ type: 'undo' }),
    restart: () => dispatch({ type: 'restart', attemptId: crypto.randomUUID() }),
    canHint: !!engine.hint,
    hint: () => {
      const text = engine.hint?.(level.payload, session.state, session.hintsUsed);
      if (text) dispatch({ type: 'hint', text, revision: session.revision });
      else
        dispatch({
          type: 'message',
          text: 'A reliable hint is not available for this position. No hint was used.',
        });
    },
  };
}
