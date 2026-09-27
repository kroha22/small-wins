import { useCallback, useEffect, useEffectEvent, useRef, useState } from 'react';
import { z } from 'zod';
import { LockKeyhole } from 'lucide-react';
import rawLevels from './levels.json';
import { payloadSchema, engine, conflicts, type Point } from './model';
import { metadataSchema } from '../../shared/lib/engine';
import { useSession } from '../../features/game-session/use-session';
import { GameFrame } from '../../shared/ui/GameFrame';
import { logDiagnostic } from '../../features/diagnostics/log';
import './interaction.css';
const levels = z.array(metadataSchema.extend({ payload: payloadSchema })).parse(rawLevels);
export default function Game({ levelId }: { levelId: string }) {
  const level = levels.find((l) => l.id === levelId)!;
  const game = useSession('untangle', level, engine);
  const { session, play, solved } = game;
  const p = level.payload;
  const board = useRef<HTMLDivElement>(null);
  const gesture = useRef<{
    id: string;
    pointerId: number;
    start: Point;
    offset: Point;
    dragged: boolean;
    target: HTMLDivElement;
    last: Point;
    captureLost: boolean;
  } | null>(null);
  const [feedback, setFeedback] = useState('');
  const [selected, setSelected] = useState<string | null>(null);
  const [preview, setPreview] = useState<{ id: string; position: Point } | null>(null);
  const visual = preview
    ? { positions: { ...session.state.positions, [preview.id]: preview.position } }
    : session.state;
  const crossed = conflicts(p, visual);
  const previewResult = preview
    ? engine.apply(p, session.state, { type: 'moveNode', id: preview.id, ...preview.position })
    : null;
  const previewError = previewResult && !previewResult.accepted ? previewResult.reason : null;
  function point(
    event: { clientX: number; clientY: number },
    offset: Point = { x: 0, y: 0 },
    clamp = true,
  ): Point {
    const element = board.current!;
    const rect = element.getBoundingClientRect();
    // Node percentages use the inner board, excluding its border.
    const x =
      ((event.clientX - rect.left - element.clientLeft) / element.clientWidth) * 100 + offset.x;
    const y =
      ((event.clientY - rect.top - element.clientTop) / element.clientHeight) * 100 + offset.y;
    return clamp
      ? {
          x: Math.min(p.bounds.max, Math.max(p.bounds.min, x)),
          y: Math.min(p.bounds.max, Math.max(p.bounds.min, y)),
        }
      : { x, y };
  }
  const cancel = useCallback(
    (reason: 'escape' | 'pointer-cancel' | 'blur' | 'hidden' | 'undo' | 'restart' | 'unmount') => {
      const g = gesture.current;
      gesture.current = null;
      if (g) {
        if (g.target.hasPointerCapture(g.pointerId)) g.target.releasePointerCapture(g.pointerId);
        logDiagnostic({
          event: 'drag-cancel',
          levelId: level.id,
          contentVersion: level.contentVersion,
          nodeId: g.id,
          reason,
        });
      }
      if (reason !== 'unmount') {
        setPreview(null);
        setFeedback(g ? 'Drag cancelled. The node stayed in its last saved position.' : '');
      }
    },
    [level.id, level.contentVersion],
  );
  function commit(id: string, pos: Point, input: 'drag' | 'keyboard' | 'tap') {
    const action = { type: 'moveNode' as const, id, ...pos };
    const result = engine.apply(p, session.state, action);
    logDiagnostic({
      event: !result.accepted
        ? 'move-rejected'
        : result.changed
          ? 'move-accepted'
          : 'move-unchanged',
      levelId: level.id,
      contentVersion: level.contentVersion,
      nodeId: id,
      input,
      from: session.state.positions[id]!,
      to: pos,
      positions: session.state.positions,
      ...(!result.accepted ? { reason: result.reason } : {}),
    });
    play(action);
    setPreview(null);
    setFeedback(
      !result.accepted
        ? `${result.reason} Node ${id} was not moved.`
        : result.changed
          ? `Node ${id} placed.`
          : `Node ${id} is already there.`,
    );
  }
  function finish(event: globalThis.PointerEvent, source: 'pointerup' | 'buttons-released') {
    const g = gesture.current;
    if (!g || g.pointerId !== event.pointerId) return;
    const pos = source === 'pointerup' ? point(event, g.offset) : g.last;
    gesture.current = null;
    if (g.target.hasPointerCapture(g.pointerId)) g.target.releasePointerCapture(g.pointerId);
    if (
      g.dragged ||
      (source === 'pointerup' &&
        Math.hypot(event.clientX - g.start.x, event.clientY - g.start.y) >= 4)
    ) {
      logDiagnostic({
        event: 'drag-release',
        levelId: level.id,
        contentVersion: level.contentVersion,
        nodeId: g.id,
        source,
        to: pos,
      });
      commit(g.id, pos, 'drag');
    }
  }
  const onMove = useEffectEvent((event: globalThis.PointerEvent) => {
    const g = gesture.current;
    if (!g || g.pointerId !== event.pointerId) return;
    if (event.pointerType === 'mouse' && (event.buttons & 1) === 0) {
      finish(event, 'buttons-released');
      return;
    }
    if (Math.hypot(event.clientX - g.start.x, event.clientY - g.start.y) >= 4) g.dragged = true;
    g.last = point(event, g.offset);
    if (g.dragged) setPreview({ id: g.id, position: g.last });
  });
  const onUp = useEffectEvent((event: globalThis.PointerEvent) => finish(event, 'pointerup'));
  const onCancel = useEffectEvent((event: globalThis.PointerEvent) => {
    if (gesture.current?.pointerId === event.pointerId) cancel('pointer-cancel');
  });
  const onLost = useEffectEvent((event: globalThis.PointerEvent) => {
    const g = gesture.current;
    if (!g || g.pointerId !== event.pointerId || g.captureLost) return;
    g.captureLost = true;
    logDiagnostic({
      event: 'drag-capture-lost',
      levelId: level.id,
      contentVersion: level.contentVersion,
      nodeId: g.id,
      position: g.last,
    });
    // Capture is a delivery mechanism, not an instruction to undo the gesture.
    // Window listeners still receive moves/releases outside the moving node.
  });
  useEffect(() => {
    const move = (event: globalThis.PointerEvent) => onMove(event);
    const up = (event: globalThis.PointerEvent) => onUp(event);
    const pointerCancel = (event: globalThis.PointerEvent) => onCancel(event);
    const lost = (event: globalThis.PointerEvent) => onLost(event);
    const blur = () => cancel('blur');
    const hidden = () => {
      if (document.hidden) cancel('hidden');
    };
    window.addEventListener('pointermove', move, true);
    window.addEventListener('pointerup', up, true);
    window.addEventListener('pointercancel', pointerCancel, true);
    window.addEventListener('lostpointercapture', lost, true);
    window.addEventListener('blur', blur);
    document.addEventListener('visibilitychange', hidden);
    return () => {
      window.removeEventListener('pointermove', move, true);
      window.removeEventListener('pointerup', up, true);
      window.removeEventListener('pointercancel', pointerCancel, true);
      window.removeEventListener('lostpointercapture', lost, true);
      window.removeEventListener('blur', blur);
      document.removeEventListener('visibilitychange', hidden);
      cancel('unmount');
    };
  }, [cancel]);
  return (
    <GameFrame
      gameId="untangle"
      level={level}
      {...game}
      undo={() => {
        cancel('undo');
        game.undo();
      }}
      restart={() => {
        cancel('restart');
        setSelected(null);
        game.restart();
      }}
    >
      <div className="board-topline">
        <span>Give every line some space</span>
        <span>{crossed.count === 0 ? 'All clear' : `${crossed.edges.size} lines to untangle`}</span>
      </div>
      <div
        ref={board}
        className="untangle-board"
        role="group"
        aria-label="Untangle board. Select a node, then tap open space; or use arrow keys and Enter."
        onPointerDown={(event) => {
          if (
            event.target === event.currentTarget &&
            selected &&
            !solved &&
            event.button === 0 &&
            event.isPrimary &&
            !gesture.current
          )
            commit(selected, point(event), 'tap');
        }}
        onKeyDown={(event) => {
          if (event.key === 'Escape') {
            cancel('escape');
            setSelected(null);
          }
        }}
      >
        <svg viewBox="0 0 100 100" aria-hidden="true">
          {preview && previewError && (
            <circle
              className="drop-exclusion"
              cx={preview.position.x}
              cy={preview.position.y}
              r={p.minNodeDistance}
            />
          )}
          {p.edges.map(([a, b], i) => (
            <line
              key={`${a}-${b}`}
              x1={visual.positions[a]!.x}
              y1={visual.positions[a]!.y}
              x2={visual.positions[b]!.x}
              y2={visual.positions[b]!.y}
              className={crossed.edges.has(i) ? 'crossed-edge' : 'clear-edge'}
            />
          ))}
        </svg>
        {p.nodes.map((n) => {
          const pos = visual.positions[n.id]!;
          return (
            <button
              key={n.id}
              className={`graph-node ${n.fixed ? 'fixed' : ''} ${selected === n.id ? 'selected' : ''} ${preview?.id === n.id && previewError ? 'invalid-drop' : ''}`}
              draggable={false}
              data-node={n.id}
              aria-disabled={n.fixed || solved}
              aria-pressed={selected === n.id}
              aria-label={`Node ${n.id}, ${n.fixed ? 'pinned, ' : ''}x ${Math.round(pos.x)}, y ${Math.round(pos.y)}`}
              style={{ left: `${pos.x}%`, top: `${pos.y}%` }}
              onClick={(event) => {
                if (event.detail === 0 && !n.fixed && !solved && !gesture.current) {
                  if (preview?.id === n.id) commit(n.id, preview.position, 'keyboard');
                  else setSelected(n.id);
                }
              }}
              onPointerDown={(event) => {
                if (n.fixed || solved || event.button !== 0 || !event.isPrimary || gesture.current)
                  return;
                event.stopPropagation();
                board.current!.setPointerCapture(event.pointerId);
                const pointer = point(event, { x: 0, y: 0 }, false);
                const origin = session.state.positions[n.id]!;
                gesture.current = {
                  id: n.id,
                  pointerId: event.pointerId,
                  start: { x: event.clientX, y: event.clientY },
                  offset: { x: origin.x - pointer.x, y: origin.y - pointer.y },
                  dragged: false,
                  target: board.current!,
                  last: origin,
                  captureLost: false,
                };
                setPreview(null);
                setFeedback('');
                setSelected(n.id);
                logDiagnostic({
                  event: 'drag-start',
                  levelId: level.id,
                  contentVersion: level.contentVersion,
                  nodeId: n.id,
                  pointerType: event.pointerType,
                  from: origin,
                });
              }}
              onKeyDown={(event) => {
                if (n.fixed || solved || gesture.current) return;
                const delta = event.shiftKey ? 5 : 1;
                const moves: Record<string, Point> = {
                  ArrowLeft: { x: -delta, y: 0 },
                  ArrowRight: { x: delta, y: 0 },
                  ArrowUp: { x: 0, y: -delta },
                  ArrowDown: { x: 0, y: delta },
                };
                const movement = moves[event.key];
                if (movement) {
                  event.preventDefault();
                  setSelected(n.id);
                  setPreview({
                    id: n.id,
                    position: {
                      x: Math.min(p.bounds.max, Math.max(p.bounds.min, pos.x + movement.x)),
                      y: Math.min(p.bounds.max, Math.max(p.bounds.min, pos.y + movement.y)),
                    },
                  });
                }
              }}
            >
              <span>{n.id}</span>
              {n.fixed && <LockKeyhole size={11} />}
            </button>
          );
        })}
      </div>
      <p
        className={`untangle-feedback ${previewError ? 'blocked' : ''}`}
        aria-live="polite"
        aria-atomic="true"
      >
        {previewError
          ? `${previewError} Keep other nodes outside the dashed circle before releasing.`
          : feedback || 'Leave a little space between nodes. Crossed lines do not block a move.'}
      </p>
      <p className="input-help">
        Select a node, then tap open space. Or drag. With keys: arrows to preview, Enter to place,
        Escape to cancel.
      </p>
      <p className="sr-only">
        {crossed.count ? `${crossed.count} conflicts remain.` : 'No lines cross.'}
      </p>
    </GameFrame>
  );
}
