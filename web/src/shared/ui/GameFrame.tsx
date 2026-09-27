import { RulesDialog } from './RulesDialog';
import { useEffect, useRef, type ReactNode } from 'react';
import './game-layout.css';
import { Link } from '@tanstack/react-router';
import { motion } from 'motion/react';
import { ArrowLeft, ArrowRight, Undo2, RotateCcw, Lightbulb, Check } from 'lucide-react';
import { findGame, levelMetadata } from '../../content/catalog';
import { type GameId, type Metadata } from '../lib/engine';
import { beginPlaytest, finishPlaytest, isPlaytestEnabled, updatePlaytest } from '../../features/playtest/log';

export function BoardToolbar({
  undo,
  restart,
  hint,
  canUndo,
  solved,
  pending = false,
  canHint = true,
  nextAction,
}: {
  undo: () => void;
  restart: () => void;
  hint: () => void;
  canUndo: boolean;
  solved: boolean;
  pending?: boolean;
  canHint?: boolean;
  nextAction?: ReactNode;
}) {
  return (
    <div className={`board-toolbar ${!canHint && !solved ? 'without-hint' : ''}`}>
      <button className="button secondary" onClick={undo} disabled={!canUndo}>
        <Undo2 size={18} />
        Undo
      </button>
      <button className="button secondary" onClick={restart}>
        <RotateCcw size={18} />
        Restart
      </button>
      {solved && nextAction ? (
        nextAction
      ) : canHint ? (
        <button className="button secondary" onClick={hint} disabled={solved || pending}>
          <Lightbulb size={18} />
          {pending ? 'Thinking…' : 'Hint'}
        </button>
      ) : null}
    </div>
  );
}
export function ResultCard() {
  return (
    <motion.section
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      className="completion-summary"
      aria-label="Puzzle complete"
    >
      <span className="result-icon">
        <Check size={22} />
      </span>
      <div>
        <h2>A little win. Nicely done.</h2>
      </div>
    </motion.section>
  );
}
export function GameFrame({
  gameId,
  level,
  children,
  aside,
  session,
  solved,
  undo,
  restart,
  hint,
  pending = false,
  canHint = true,
}: {
  gameId: GameId;
  level: Metadata;
  children: ReactNode;
  aside?: ReactNode;
  session: {
    attemptId: string;
    history: unknown[];
    moveCount: number;
    hintsUsed: number;
    message: string;
    hint: string | null;
  };
  solved: boolean;
  undo: () => void;
  restart: () => void;
  hint: () => void;
  pending?: boolean;
  canHint?: boolean;
}) {
  const game = findGame(gameId)!;
  const next = levelMetadata[levelMetadata.findIndex((l) => l.id === level.id) + 1]?.id;
  const playtestId = useRef<string | null>(null);
  const previousAttempt = useRef(session.attemptId);
  const latest = useRef({ solved, attemptId: session.attemptId, moves: session.moveCount, hints: session.hintsUsed, restarts: 0 });
  useEffect(() => {
    const restarts = latest.current.restarts + (previousAttempt.current === session.attemptId ? 0 : 1);
    previousAttempt.current = session.attemptId;
    latest.current = { solved, attemptId: session.attemptId, moves: session.moveCount, hints: session.hintsUsed, restarts };
    if (playtestId.current) {
      updatePlaytest(playtestId.current, { moves: session.moveCount, hintsUsed: session.hintsUsed, restarts, solved });
      if (solved) finishPlaytest(playtestId.current, 'completed');
    }
  }, [session.attemptId, session.hintsUsed, session.moveCount, solved]);
  useEffect(() => {
    if (!isPlaytestEnabled() || solved) return;
    const id = beginPlaytest(gameId, level);
    playtestId.current = id;
    return () => {
      if (playtestId.current) finishPlaytest(playtestId.current, latest.current.solved ? 'completed' : 'abandoned');
      playtestId.current = null;
    };
  }, [gameId, level, solved]);
  return (
    <main id="main" className="page play-page">
      <div className="game-navigation">
        <Link
          to="/games/$gameId"
          params={{ gameId }}
          search={{ difficulty: 'all' }}
          className="back-link"
        >
          <ArrowLeft size={16} />
          All {game.name} puzzles
        </Link>
        <RulesDialog gameId={gameId} />
      </div>
      <div className="game-heading">
        <div>
          <p className="eyebrow">
            {game.name} <span> / </span>{' '}
            {level.difficulty === 'tutorial' ? 'A gentle introduction' : level.difficulty}
          </p>
          <h1 tabIndex={-1}>{level.title}</h1>
          <p className="rule">{game.rule}</p>
        </div>
        <span className="moves">
          {session.moveCount}
          <small>moves</small>
        </span>
      </div>
      <div className={`game-layout ${aside ? 'with-aside' : ''}`}>
        <section
          className={`board-panel ${game.colour} ${aside ? 'board-with-clues' : ''}`}
          aria-label={`${game.name} puzzle`}
        >
          <motion.div
            key={level.id}
            className="board-content"
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.28, ease: [0.22, 1, 0.36, 1] }}
          >
            {children}
          </motion.div>
          {aside}
          <div className="game-actions">
            <p role="status" className="status-message" aria-atomic="true">
              {session.message || 'Make yourself comfortable. Every puzzle is unlocked.'}
            </p>
            {session.hint && !solved && (
              <motion.section
                initial={{ opacity: 0, y: 8, scale: 0.98 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                transition={{ duration: 0.2 }}
                className="hint-card"
                aria-label="Hint"
                aria-live="polite"
              >
                <Lightbulb size={20} />
                <p>{session.hint}</p>
              </motion.section>
            )}
            {solved && <ResultCard />}
            <BoardToolbar
              undo={undo}
              restart={restart}
              hint={hint}
              canUndo={session.history.length > 0}
              solved={solved}
              pending={pending}
              canHint={canHint}
              nextAction={
                next ? (
                  <Link
                    className="button primary next-puzzle"
                    to="/games/$gameId/levels/$levelId"
                    params={{ gameId, levelId: next }}
                  >
                    Next puzzle <ArrowRight size={18} />
                  </Link>
                ) : (
                  <Link className="button primary next-puzzle" to="/">
                    More puzzles <ArrowRight size={18} />
                  </Link>
                )
              }
            />
          </div>
        </section>
      </div>
    </main>
  );
}
