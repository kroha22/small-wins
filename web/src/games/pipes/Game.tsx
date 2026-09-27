import { z } from 'zod';
import rawLevels from './levels.json';
import { payloadSchema, engine, connections } from './model';
import { metadataSchema, cellName } from '../../shared/lib/engine';
import { useSession } from '../../features/game-session/use-session';
import { GameFrame } from '../../shared/ui/GameFrame';
import { Grid } from '../../shared/ui/Grid';
const levels = z.array(metadataSchema.extend({ payload: payloadSchema })).parse(rawLevels);
export default function Game({ levelId }: { levelId: string }) {
  const level = levels.find((l) => l.id === levelId)!;
  const game = useSession('pipes', level, engine);
  const { session, solved, play } = game;
  const p = level.payload,
    c = connections(p, session.state);
  return (
    <GameFrame gameId="pipes" level={level} {...game}>
      <div className="board-topline">
        <span>
          <i className="water-dot" />
          Water source
        </span>
        <span>
          {c.reached.size} / {p.ports.length} connected
        </span>
      </div>
      <Grid
        rows={p.rows}
        cols={p.cols}
        label="Pipe grid. Arrow keys move; Enter rotates clockwise. Shift Enter rotates counterclockwise."
      >
        {p.ports.map((_, cell) => {
          const ports = p.ports[cell]!;
          const rotation = session.state.rotations[cell]! * 90;
          const connected = c.reached.has(cell);
          return (
            <button
              className={`grid-cell pipe-cell ${connected ? 'flowing' : ''}`}
              key={cell}
              data-cell={cell}
              tabIndex={cell === 0 ? 0 : -1}
              aria-disabled={solved}
              aria-label={`${cellName(cell, p.cols)}, openings ${ports.map((d) => ['up', 'right', 'down', 'left'][d]).join(' and ')}${cell === p.sourceCell ? ', source' : ''}, ${connected ? 'connected' : 'not connected'}`}
              onClick={(event) =>
                play({ type: 'rotate', cell, quarterTurns: event.shiftKey ? -1 : 1 })
              }
              onKeyDown={(event) => {
                if (event.key === 'Enter' && event.shiftKey) {
                  event.preventDefault();
                  play({ type: 'rotate', cell, quarterTurns: -1 });
                }
              }}
            >
              <svg className="pipe-art" style={{ transform: `rotate(${rotation}deg)` }} viewBox="0 0 100 100" aria-hidden="true">
                {ports.map((d) => (
                  <path
                    key={`under-${d}`}
                    className="pipe-outline"
                    d={`M50 50 L${[50, 100, 50, 0][d]} ${[0, 50, 100, 50][d]}`}
                  />
                ))}
                {ports.map((d) => (
                  <path
                    key={d}
                    className="pipe-line"
                    d={`M50 50 L${[50, 100, 50, 0][d]} ${[0, 50, 100, 50][d]}`}
                  />
                ))}
                <circle
                  cx="50"
                  cy="50"
                  r={cell === p.sourceCell ? 11 : 6}
                  className={cell === p.sourceCell ? 'source-circle' : 'pipe-centre'}
                />
                {c.leaks
                  .filter((l) => l.cell === cell)
                  .map((l) => (
                    <circle
                      key={`leak-${l.dir}`}
                      cx={[50, 90, 50, 10][l.dir]}
                      cy={[10, 50, 90, 50][l.dir]}
                      r="3"
                      className="leak-mark"
                    />
                  ))}
              </svg>
            </button>
          );
        })}
      </Grid>
      <p className="input-help">
        Tap a pipe to turn it. Blue pipes have water; dots mark open ends.
      </p>
    </GameFrame>
  );
}
