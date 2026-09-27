import { useEffect, useRef, useState, type CSSProperties, type PointerEvent as ReactPointerEvent } from 'react';
import { z } from 'zod';
import { Check, Minus, X, ChevronLeft, ChevronRight } from 'lucide-react';
import rawLevels from './levels.json';
import { HouseToken } from './HouseToken';
import './house.css';
import { payloadSchema, engine, clueStatus, clueText, placementConflicts, type CatId } from './model';
import { requestHint } from './worker-client';
import { metadataSchema, cellName } from '../../shared/lib/engine';
import { useSession } from '../../features/game-session/use-session';
import { GameFrame } from '../../shared/ui/GameFrame';
import { Grid } from '../../shared/ui/Grid';
import { CatToken } from '../../shared/ui/CatToken';
const levels = z.array(metadataSchema.extend({ payload: payloadSchema })).parse(rawLevels);
export default function Game({ levelId }: { levelId: string }) {
  const level = levels.find((l) => l.id === levelId)!;
  const game = useSession('purrdoku', level, engine);
  const { session, play, dispatch, solved } = game;
  const p = level.payload;
  const [selected, setSelected] = useState<CatId>('ginger');
  const [pinned, setPinned] = useState(0);
  const [pending, setPending] = useState<{ attemptId: string; revision: number } | null>(null);
  const boardRef = useRef<HTMLDivElement>(null);
  const drag = useRef<{ cat: CatId; pointerId: number } | null>(null);
  const [dragging, setDragging] = useState<CatId | null>(null);
  const conflicts = placementConflicts(p, session.state);
  const suppressClick = useRef(false);
  const cancel = useRef<(() => void) | null>(null);
  useEffect(
    () => () => {
      cancel.current?.();
      cancel.current = null;
    },
    [session.revision, session.attemptId],
  );
  function hint() {
    cancel.current?.();
    setPending({ attemptId: session.attemptId, revision: session.revision });
    const failed = () => {
      setPending(null);
      dispatch({
        type: 'message',
        text: 'The hint could not finish. Your board is safe; try again or read the clues.',
      });
    };
    cancel.current = requestHint(
      {
        requestId: crypto.randomUUID(),
        attemptId: session.attemptId,
        revision: session.revision,
        contentVersion: level.contentVersion,
        level: p,
        state: session.state,
        used: session.hintsUsed,
      },
      (reply) => {
        setPending(null);
        if (reply.status === 'ok')
          dispatch({ type: 'hint', text: reply.text, revision: reply.revision });
        else failed();
      },
      failed,
    );
  }
  function startDrag(event: ReactPointerEvent<HTMLButtonElement>, cat: CatId) {
    if (solved || event.button !== 0 || !event.isPrimary) return;
    event.preventDefault();
    drag.current = { cat, pointerId: event.pointerId };
    suppressClick.current = true;
    setDragging(cat);
  }
  useEffect(() => {
    const finish = (event: PointerEvent) => {
      if (drag.current?.pointerId !== event.pointerId) return;
      const target = document.elementFromPoint(event.clientX, event.clientY)?.closest<HTMLElement>('[data-cell]');
      if (target && boardRef.current?.contains(target)) play({ type: 'placeCat', cat: drag.current.cat, cell: Number(target.dataset.cell) });
      else play({ type: 'removeCat', cat: drag.current.cat });
      drag.current = null;
      setDragging(null);
      window.setTimeout(() => { suppressClick.current = false; }, 0);
    };
    const cancelDrag = (event: PointerEvent) => {
      if (drag.current?.pointerId !== event.pointerId) return;
      drag.current = null;
      setDragging(null);
      window.setTimeout(() => { suppressClick.current = false; }, 0);
    };
    window.addEventListener('pointerup', finish);
    window.addEventListener('pointercancel', cancelDrag);
    return () => {
      window.removeEventListener('pointerup', finish);
      window.removeEventListener('pointercancel', cancelDrag);
    };
  }, [play]);
  const clueList = (
    <aside className="clues-panel" aria-label="Cat clues">
      <h2>Follow the clues.</h2>
      <p className="clue-help">Choose a clue to show it beside your cats.</p>
      <ol className="clue-list">
        {p.clues.map((q, i) => {
          const status = clueStatus(p, session.state, q);
          return (
            <li key={i}>
              <button
                className={`clue-card ${status} ${pinned === i ? 'pinned' : ''}`}
                aria-pressed={pinned === i}
                onClick={() => setPinned(i)}
              >
                <span className="clue-state">
                  {status === 'satisfied' ? (
                    <Check size={17} />
                  ) : status === 'violated' ? (
                    <X size={17} />
                  ) : (
                    <Minus size={17} />
                  )}
                </span>
                <span>
                  {clueText(p, q)}
                  <small className="sr-only">
                    {status === 'satisfied'
                      ? 'Fits so far'
                      : status === 'violated'
                        ? 'Does not fit yet'
                        : 'Waiting for a cat'}
                  </small>
                </span>
              </button>
            </li>
          );
        })}
      </ol>
    </aside>
  );
  return (
    <GameFrame
      gameId="purrdoku"
      level={level}
      {...game}
      hint={hint}
      pending={pending?.attemptId === session.attemptId && pending.revision === session.revision}
      aside={clueList}
    >
      <div className="board-topline">
        <span>Find their favourite spots</span>
        <span>
          {Object.values(session.state.placements).filter((v) => v != null).length} / {p.size} cats
        </span>
      </div>
      {conflicts.size > 0 && (
        <p className="placement-warning" role="status">Cats sharing a row, column, or room are highlighted. Move one of the highlighted cats to continue.</p>
      )}
      <div className="house-legend" aria-label="Rooms and objects">
        <div className="house-rooms">
          {p.zones.map((room, i) => (
            <span className="house-room-key" data-room={room.id} key={room.id}>
              <b>{String.fromCharCode(65 + i)}</b> {room.label}
            </span>
          ))}
        </div>
        <div className="house-objects">
          {p.features.map((feature) => (
            <span key={feature.id}>
              <HouseToken id={feature.id} size={16} />
              {feature.label}
            </span>
          ))}
        </div>
      </div>
      <div className={`house-board ${dragging ? 'is-dragging' : ''}`} ref={boardRef}>
        <Grid
          rows={p.size}
          cols={p.size}
          label="Cat grid. Arrow keys move; Enter places your selected cat."
        >
          {Array.from({ length: p.size * p.size }, (_, cell) => {
            const cat = p.cats.find((c) => session.state.placements[c.id] === cell),
              feature = p.features.find((f) => f.cell === cell),
              zone = p.zones.find((z) => z.cells.includes(cell));
            const zoneIndex = p.zones.findIndex((room) => room.id === zone?.id);
            const edge = (neighbour: number, outside: boolean) =>
              outside || !zone?.cells.includes(neighbour) ? '3px' : '1px';
            const roomStyle = {
              borderTopWidth: edge(cell - p.size, cell < p.size),
              borderBottomWidth: edge(cell + p.size, cell >= p.size * (p.size - 1)),
              borderLeftWidth: edge(cell - 1, cell % p.size === 0),
              borderRightWidth: edge(cell + 1, cell % p.size === p.size - 1),
            } satisfies CSSProperties;
            return (
              <button
                className={`grid-cell cat-cell ${zone ? 'room-cell' : ''} ${cat?.id === selected ? 'selected-cat-cell' : ''} ${cat && conflicts.has(cat.id) ? 'placement-conflict' : ''}`}
                data-cell={cell}
                data-room={zone?.id}
                style={roomStyle}
                tabIndex={cell === 0 ? 0 : -1}
                key={cell}
                aria-disabled={solved}
                onPointerDown={(event) => cat && startDrag(event, cat.id)}
                aria-label={`${cellName(cell, p.size)}, ${cat ? `${cat.label}, mark ${cat.mark}` : 'empty'}${feature ? `, ${feature.label}` : ''}${zone ? `, ${zone.label}` : ''}`}
                onClick={() => {
                  if (suppressClick.current) return;
                  if (cat) {
                    setSelected(cat.id);
                    return;
                  }
                  play({ type: 'placeCat', cat: selected, cell });
                }}
                onKeyDown={(event) => {
                  if (event.key === 'Delete' || event.key === 'Backspace') {
                    event.preventDefault();
                    if (cat) play({ type: 'removeCat', cat: cat.id });
                  }
                }}
              >
                {feature && (
                  <span className={`house-feature ${cat ? 'with-cat' : ''}`} title={feature.label}>
                    <HouseToken id={feature.id} />
                  </span>
                )}
                <span className="cell-coordinate" aria-hidden="true">
                  {Math.floor(cell / p.size) + 1}·{(cell % p.size) + 1}
                </span>
                {zone && (
                  <span className="cell-room-mark" aria-hidden="true">
                    {String.fromCharCode(65 + zoneIndex)}
                  </span>
                )}
                {cat && (
                  <>
                    <CatToken id={cat.id} mark={cat.mark} />
                    {cat.id === selected && (
                      <button
                        className="remove-cat"
                        aria-label={`Remove ${cat.label} from grid`}
                        onPointerDown={(event) => event.stopPropagation()}
                        onClick={(event) => {
                          event.stopPropagation();
                          play({ type: 'removeCat', cat: cat.id });
                        }}
                      >
                        <X size={13} />
                      </button>
                    )}
                  </>
                )}
              </button>
            );
          })}
        </Grid>
      </div>
      <div
        className="focus-clue"
        aria-label="Clue beside the board"
        data-status={clueStatus(p, session.state, p.clues[pinned]!)}
      >
        <button
          aria-label="Previous clue"
          onClick={() => setPinned((pinned + p.clues.length - 1) % p.clues.length)}
        >
          <ChevronLeft size={18} />
        </button>
        <div aria-live="polite">
          <small>
            Clue {pinned + 1} / {p.clues.length}
          </small>
          <p>{clueText(p, p.clues[pinned]!)}</p>
        </div>
        <button aria-label="Next clue" onClick={() => setPinned((pinned + 1) % p.clues.length)}>
          <ChevronRight size={18} />
        </button>
      </div>
      <div className="cat-tray" aria-label="Choose a cat">
        {p.cats.map((c) => (
          <button
            key={c.id}
            className={`cat-choice ${selected === c.id ? 'selected' : ''}`}
            aria-label={`${c.label}, mark ${c.mark}`}
            aria-pressed={selected === c.id}
            onClick={() => setSelected(c.id)}
          >
            <CatToken id={c.id} mark={c.mark} small />
            <span>{c.label}</span>
            {selected === c.id && <Check size={13} className="choice-check" />}
          </button>
        ))}
      </div>
      <div className="cat-controls">
        <p className="input-help">Choose a cat, then a square.</p>
        <button
          className="text-button"
          disabled={solved || session.state.placements[selected] == null}
          onClick={() => play({ type: 'removeCat', cat: selected })}
        >
          Remove {selected} cat
        </button>
      </div>
    </GameFrame>
  );
}
