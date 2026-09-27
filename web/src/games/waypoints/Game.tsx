import { useRef } from 'react';
import { z } from 'zod';
import { Carrot, Check, Flag, Rabbit } from 'lucide-react';
import rawLevels from './levels.json';
import './waypoints.css';
import { payloadSchema, engine } from './model';
import { metadataSchema, cellName } from '../../shared/lib/engine';
import { useSession } from '../../features/game-session/use-session';
import { GameFrame } from '../../shared/ui/GameFrame';
import { Grid } from '../../shared/ui/Grid';
const levels = z.array(metadataSchema.extend({ payload: payloadSchema })).parse(rawLevels);
export default function Game({ levelId }: { levelId: string }) {
  const level = levels.find((l) => l.id === levelId)!;
  const game = useSession('waypoints', level, engine);
  const { session, play, solved } = game;
  const p = level.payload,
    path = session.state.path;
  const dragging = useRef(false);
  function visit(cell: number) {
    if (solved || p.blocked.includes(cell)) return;
    play({ type: 'addCell', cell });
  }
  return (
    <GameFrame gameId="waypoints" level={level} {...game}>
      <div className="board-topline">
        <span>Every carrot, any order</span>
        <span>
          {p.waypoints.filter((w) => path.includes(w)).length} / {p.waypoints.length} collected
        </span>
      </div>
      <div
        className="route-board"
        onPointerDown={(event) => {
          if (event.button !== 0) return;
          const target = (event.target as HTMLElement).closest<HTMLElement>('[data-cell]');
          if (!target || p.blocked.includes(Number(target.dataset.cell)) || solved) return;
          dragging.current = true;
          event.currentTarget.setPointerCapture(event.pointerId);
          visit(Number(target.dataset.cell));
        }}
        onPointerMove={(event) => {
          if (!dragging.current || !event.buttons) return;
          const element = document
            .elementFromPoint(event.clientX, event.clientY)
            ?.closest<HTMLElement>('[data-cell]');
          if (element && event.currentTarget.contains(element)) visit(Number(element.dataset.cell));
        }}
        onPointerUp={() => {
          dragging.current = false;
        }}
        onPointerCancel={() => {
          dragging.current = false;
        }}
        onLostPointerCapture={() => {
          dragging.current = false;
        }}
        onKeyDown={(event) => {
          if (event.key === 'Escape') dragging.current = false;
        }}
      >
        <Grid
          rows={p.rows}
          cols={p.cols}
          label="Route grid. Use arrows to focus a cell and Enter to add it to your path."
        >
          {Array.from({ length: p.rows * p.cols }, (_, cell) => {
            const blocked = p.blocked.includes(cell),
              marker = p.waypoints.indexOf(cell),
              visited = path.includes(cell);
            return (
              <button
                key={cell}
                data-cell={cell}
                tabIndex={cell === p.start ? 0 : -1}
                aria-disabled={blocked || solved}
                className={`grid-cell route-cell ${blocked ? 'blocked' : ''} ${visited ? 'visited' : ''} ${path.at(-1) === cell ? 'route-end' : ''}`}
                aria-label={`${cellName(cell, p.cols)}${blocked ? ', blocked' : cell === p.start ? ', rabbit, start' : cell === p.target ? ', finish' : marker >= 0 ? `, carrot, ${visited ? 'collected' : 'not collected'}` : ''}${visited ? ', visited' : ''}${path.at(-1) === cell ? ', end of path' : ''}`}
                onClick={(event) => {
                  if (event.detail === 0) visit(cell);
                }}
              >
                {blocked ? null : cell === p.target ? (
                  <Flag size={22} />
                ) : cell === p.start ? (
                  <Rabbit className="route-start-icon" size={28} aria-hidden="true" />
                ) : marker >= 0 ? (
                  <span className={`route-carrot ${visited ? 'collected' : ''}`} aria-hidden="true">
                    <Carrot className="carrot-icon" size={28} />
                    {visited && <Check className="carrot-check" size={14} />}
                  </span>
                ) : null}
              </button>
            );
          })}
        </Grid>
        <svg
          className="route-overlay"
          viewBox={`0 0 ${p.cols * 100} ${p.rows * 100}`}
          aria-hidden="true"
        >
          <polyline
            points={path
              .map((c) => `${(c % p.cols) * 100 + 50},${Math.floor(c / p.cols) * 100 + 50}`)
              .join(' ')}
            fill="none"
            stroke="currentColor"
            strokeWidth="14"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </div>
      <p className="input-help">
        Help the rabbit collect every carrot and reach the flag. Tap or trace neighbouring cells;
        striped squares are off limits.
      </p>
    </GameFrame>
  );
}
