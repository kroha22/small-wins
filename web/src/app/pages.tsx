import { lazy, Suspense, useEffect, useSyncExternalStore, Component, type ReactNode } from 'react';
import { Link, Outlet, useRouterState, useParams, useSearch } from '@tanstack/react-router';
import { MotionConfig } from 'motion/react';
import { ArrowRight, Check, ArrowLeft, Sparkles } from 'lucide-react';
import { catalog, findGame, levelMetadata, loaders } from '../content/catalog';
import { type GameId } from '../shared/lib/engine';
import { Preview } from '../shared/ui/Preview';
import { Settings } from '../shared/ui/Settings';
import { useProgress, completionKey } from '../features/progress/store';
import {
  attemptSchema,
  readRecord,
  storageAvailable,
  subscribeStorage,
} from '../features/progress/storage';

const gameComponents = {
  purrdoku: lazy(loaders.purrdoku),
  pipes: lazy(loaders.pipes),
  untangle: lazy(loaders.untangle),
  waypoints: lazy(loaders.waypoints),
  shikaku: lazy(loaders.shikaku),
  'wildlife-survey': lazy(loaders['wildlife-survey']),
};
function FocusHeading() {
  const path = useRouterState({ select: (s) => s.location.pathname });
  useEffect(() => {
    const frame = requestAnimationFrame(() =>
      document.querySelector<HTMLElement>('h1')?.focus({ preventScroll: true }),
    );
    return () => cancelAnimationFrame(frame);
  }, [path]);
  return null;
}
export function Shell() {
  const reduced = useProgress((s) => s.settings.reduceMotion);
  const saving = useSyncExternalStore(subscribeStorage, storageAvailable, () => true);
  return (
    <MotionConfig reducedMotion={reduced ? 'always' : 'user'}>
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <div className={reduced ? 'app reduce-motion' : 'app'}>
        <header className="site-header page">
          <Link className="wordmark" to="/" aria-label="Small Wins home">
            <span className="brand-mark">
              <Check size={21} />
            </span>
            small wins<span className="wordmark-dot">.</span>
          </Link>
          <nav aria-label="Main navigation">
            <Link to="/" className="nav-play">
              Play
            </Link>
            <Settings />
          </nav>
        </header>
        {!saving && (
          <p className="storage-warning" role="status">
            Progress cannot be saved on this device. You can keep playing in this tab.
          </p>
        )}
        <FocusHeading />
        <Outlet />
        <footer className="site-footer page">
          <Link to="/" className="footer-brand">
            small wins.
          </Link>
          <p>A little play goes a long way.</p>
          <span>
            Made for a moment of calm <Sparkles size={14} />
          </span>
        </footer>
      </div>
    </MotionConfig>
  );
}
export function NotFound() {
  return (
    <main id="main" className="page empty-state">
      <p className="eyebrow">A little detour</p>
      <h1 tabIndex={-1}>This puzzle wandered off.</h1>
      <p>We could not find that game or level.</p>
      <Link to="/" className="button primary">
        Back to the games <ArrowRight size={18} />
      </Link>
    </main>
  );
}
export function Home() {
  const completed = useProgress((s) => s.completed);
  const last = attemptSchema.safeParse(readRecord('active-attempt')).data;
  const continueGame =
    last && findGame(last.gameId) && levelMetadata.some((l) => l.id === last.levelId) ? last : null;
  return (
    <main id="main" className="page home-page">
      <section className="hero">
        <div>
          <p className="eyebrow">
            <span className="tiny-star">✳</span>A TINY PUZZLE CLUB
          </p>
          <h1 tabIndex={-1}>
            Small puzzles.
            <br />
            <span>Good little moments.</span>
          </h1>
          <p className="hero-copy">
            Six small games. One satisfying break. <br />
            Settle in, follow your curiosity, and find your next small win.
          </p>
        </div>
        <div className="hero-note" aria-hidden="true">
          <svg viewBox="0 0 90 80">
            <path
              d="M13 15q48-13 47 16T30 50q-20-3-14-15t38 5 14 22m0 0-2-13m2 13-13-1"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
            />
          </svg>
          <span>
            nothing to chase.
            <br />
            just a little play.
          </span>
        </div>
      </section>
      <div className="collection-label">
        <h2>Pick your little escape</h2>
        <span>ALL LEVELS OPEN · ALWAYS</span>
      </div>
      <section className="game-cards" aria-label="Choose a game">
        {catalog.map((game, i) => {
          const count = levelMetadata.filter(
            (l) =>
              l.difficulty !== 'tutorial' &&
              completed[completionKey(game.id, l.id, game.contentVersion)],
          ).length;
          const Icon = game.icon;
          return (
            <Link
              key={game.id}
              to="/games/$gameId"
              params={{ gameId: game.id }}
              search={{ difficulty: 'all' }}
              className={`game-card ${game.colour} ${game.id === 'purrdoku' ? 'featured' : ''}`}
            >
              <div className="card-top">
                <span className="card-category">
                  <Icon size={17} />
                  {game.tags}
                </span>
                <span className="card-number">0{i + 1}</span>
              </div>
              <div className="card-body">
                <div className="card-copy">
                  <p className="eyebrow">{game.eyebrow}</p>
                  <h3>{game.name}</h3>
                  <p>{game.description}</p>
                </div>
                <Preview gameId={game.id} />
              </div>
              <div className="card-bottom">
                <span className="play-label">
                  Let’s play <ArrowRight size={18} />
                </span>
                <span>{count ? `${count} / 6 little wins` : '6 puzzles + a gentle start'}</span>
              </div>
            </Link>
          );
        })}
      </section>
      {continueGame && (
        <Link
          className="continue-link"
          to="/games/$gameId/levels/$levelId"
          params={{ gameId: continueGame.gameId, levelId: continueGame.levelId }}
        >
          <span>
            <strong>Pick up where you left off</strong>
            <small>
              {findGame(continueGame.gameId)!.name} ·{' '}
              {continueGame.levelId === 'tutorial'
                ? 'A gentle start'
                : `Puzzle ${continueGame.levelId.split('-')[1]}`}{' '}
              · Saved on this device
            </small>
          </span>
          <ArrowRight size={21} />
        </Link>
      )}
      <div className="home-footnote">
        <span>NO CLOCK.</span>
        <span>NO PRESSURE.</span>
        <span>JUST PLAY.</span>
      </div>
    </main>
  );
}
export function GameOverview() {
  const { gameId } = useParams({ from: '/games/$gameId' });
  const { difficulty } = useSearch({ from: '/games/$gameId' });
  const game = findGame(gameId);
  const completed = useProgress((s) => s.completed);
  if (!game) return <NotFound />;
  const Icon = game.icon;
  const count = levelMetadata.filter(
    (l) =>
      l.difficulty !== 'tutorial' && completed[completionKey(game.id, l.id, game.contentVersion)],
  ).length;
  return (
    <main id="main" className="page overview-page">
      <Link to="/" className="back-link">
        <ArrowLeft size={16} />
        All games
      </Link>
      <section className={`overview-hero ${game.colour}`}>
        <div>
          <p className="eyebrow">
            <Icon size={18} />
            {game.tags}
          </p>
          <h1 tabIndex={-1}>{game.name}</h1>
          <p>{game.description}</p>
          <p className="overview-rule">{game.rule}</p>
          <span className="progress-caption">{count} / 6 little wins</span>
        </div>
        <Preview gameId={game.id} />
      </section>
      <div className="levels-heading">
        <div>
          <p className="eyebrow">YOUR NEXT SMALL WIN</p>
          <h2>A good place to begin.</h2>
        </div>
        <nav className="difficulty-filter" aria-label="Difficulty">
          {(['all', 'easy', 'medium', 'hard'] as const).map((d) => (
            <Link
              key={d}
              to="/games/$gameId"
              params={{ gameId }}
              search={{ difficulty: d }}
              className={difficulty === d ? 'active' : ''}
              aria-current={difficulty === d ? 'true' : undefined}
            >
              {d === 'all' ? 'All puzzles' : d}
            </Link>
          ))}
        </nav>
      </div>
      <section className="level-list" aria-label={`${game.name} levels`}>
        {levelMetadata
          .filter(
            (l) =>
              l.difficulty === 'tutorial' || difficulty === 'all' || l.difficulty === difficulty,
          )
          .map((l) => {
            const done = completed[completionKey(gameId, l.id, game.contentVersion)];
            return (
              <Link
                key={l.id}
                to="/games/$gameId/levels/$levelId"
                params={{ gameId, levelId: l.id }}
                className={`level-tile ${l.difficulty === 'tutorial' ? 'tutorial-tile' : ''}`}
              >
                <span className="level-number">
                  {done ? (
                    <Check size={24} />
                  ) : l.difficulty === 'tutorial' ? (
                    <Sparkles size={24} />
                  ) : (
                    l.label
                  )}
                </span>
                <span>
                  <strong>
                    {l.difficulty === 'tutorial' ? 'A gentle start' : `Puzzle ${l.label}`}
                  </strong>
                  <small>
                    {l.difficulty === 'tutorial' ? 'Learn by playing' : l.difficulty}
                    {done ? ` · Complete${done.assisted ? ' with a hint' : ''}` : ''}
                  </small>
                </span>
                <ArrowRight size={20} />
              </Link>
            );
          })}
      </section>
      <p className="overview-note">Start anywhere. Your progress is saved on this device.</p>
    </main>
  );
}
class GameErrorBoundary extends Component<{ children: ReactNode }, { error: boolean }> {
  state = { error: false };
  static getDerivedStateFromError() {
    return { error: true };
  }
  render() {
    return this.state.error ? (
      <main id="main" className="page empty-state">
        <h1 tabIndex={-1}>A small interruption.</h1>
        <p>This puzzle could not load. Your saved progress is still here.</p>
        <button className="button primary" onClick={() => window.location.reload()}>
          Try again
        </button>
        <Link className="text-button" to="/">
          Back to games
        </Link>
      </main>
    ) : (
      this.props.children
    );
  }
}
export function Play() {
  const { gameId, levelId } = useParams({ from: '/games/$gameId/levels/$levelId' });
  if (!findGame(gameId) || !levelMetadata.some((l) => l.id === levelId)) return <NotFound />;
  const Game = gameComponents[gameId as GameId];
  return (
    <GameErrorBoundary key={`${gameId}/${levelId}`}>
      <Suspense
        fallback={
          <main id="main" className="page empty-state" role="status">
            <h1 tabIndex={-1}>Setting out your puzzle…</h1>
          </main>
        }
      >
        <Game key={`${gameId}/${levelId}`} levelId={levelId} />
      </Suspense>
    </GameErrorBoundary>
  );
}
