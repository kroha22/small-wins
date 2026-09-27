import { useRef, useState } from 'react';
import { z } from 'zod';
import rawLevels from './levels.json';
import { payloadSchema, engine, type Action } from './model';
import { metadataSchema, cellName } from '../../shared/lib/engine';
import { useSession } from '../../features/game-session/use-session';
import { GameFrame } from '../../shared/ui/GameFrame';
import { Grid } from '../../shared/ui/Grid';
import './shikaku.css';

const levels = z.array(metadataSchema.extend({ payload: payloadSchema })).parse(rawLevels);
export default function Game({ levelId }: { levelId: string }) {
  const level = levels.find((item) => item.id === levelId)!;
  const game = useSession('shikaku', level, engine);
  const { session, play, solved } = game;
  const [anchor, setAnchor] = useState<number | null>(null);
  const [previewCell, setPreviewCell] = useState<number | null>(null);
  const anchorRef = useRef<number | null>(null);
  const dragging = useRef(false);
  const suppressClick = useRef(false);
  const p = level.payload;

  function choose(cell: number) {
    if (solved) return;
    const activeAnchor = anchorRef.current;
    if (activeAnchor === null) {
      anchorRef.current = cell;
      setAnchor(cell);
      return;
    }
    if (activeAnchor === cell) {
      anchorRef.current = null;
      setAnchor(null);
      setPreviewCell(null);
      return;
    }
    const top = Math.min(Math.floor(activeAnchor / p.cols), Math.floor(cell / p.cols));
    const bottom = Math.max(Math.floor(activeAnchor / p.cols), Math.floor(cell / p.cols));
    const left = Math.min(activeAnchor % p.cols, cell % p.cols);
    const right = Math.max(activeAnchor % p.cols, cell % p.cols);
    const clue = p.clues.findIndex((item) => {
      const row = Math.floor(item.cell / p.cols), col = item.cell % p.cols;
      return row >= top && row <= bottom && col >= left && col <= right && session.state.regions[item.cell] === -1;
    });
    if (clue < 0) {
      anchorRef.current = null; setAnchor(null);
      return;
    }
    play({ type: 'place', clue, top, left, bottom, right } satisfies Action);
    anchorRef.current = null; setAnchor(null); setPreviewCell(null);
  }
  function inPreview(cell: number) {
    if (anchor === null || previewCell === null) return false;
    const ar = Math.floor(anchor / p.cols), ac = anchor % p.cols;
    const br = Math.floor(previewCell / p.cols), bc = previewCell % p.cols;
    const row = Math.floor(cell / p.cols), col = cell % p.cols;
    return row >= Math.min(ar, br) && row <= Math.max(ar, br) && col >= Math.min(ac, bc) && col <= Math.max(ac, bc);
  }

  return (
    <GameFrame gameId="shikaku" level={level} {...game}>
      <div className="board-topline">
        <span>{anchor === null ? 'Choose two corners' : 'Choose the opposite corner'}</span>
        <span>{session.state.regions.filter((region) => region >= 0).length} / {p.rows * p.cols} covered</span>
      </div>
      <div
        className="shikaku-board"
        onPointerDown={(event) => {
          if (event.button !== 0 || solved) return;
          const cell = (event.target as HTMLElement).closest<HTMLElement>('[data-cell]');
          if (!cell) return;
          dragging.current = true;
          anchorRef.current = Number(cell.dataset.cell);
          setAnchor(anchorRef.current);
          setPreviewCell(anchorRef.current);
          event.currentTarget.setPointerCapture(event.pointerId);
        }}
        onPointerUp={(event) => {
          if (!dragging.current) return;
          const cell = document.elementFromPoint(event.clientX, event.clientY)?.closest<HTMLElement>('[data-cell]');
          dragging.current = false;
          if (cell && Number(cell.dataset.cell) === anchorRef.current) {
            suppressClick.current = false;
            return;
          }
          suppressClick.current = true;
          if (cell && event.currentTarget.contains(cell)) choose(Number(cell.dataset.cell));
          else { anchorRef.current = null; setAnchor(null); setPreviewCell(null); }
        }}
        onPointerMove={(event) => {
          if (!dragging.current) return;
          const cell = document.elementFromPoint(event.clientX, event.clientY)?.closest<HTMLElement>('[data-cell]');
          if (cell && event.currentTarget.contains(cell)) setPreviewCell(Number(cell.dataset.cell));
        }}
        onKeyDown={(event) => {
          if (event.key === 'Escape' && anchorRef.current !== null) {
            event.preventDefault();
            anchorRef.current = null;
            setAnchor(null);
            setPreviewCell(null);
          }
        }}
        onPointerCancel={() => { dragging.current = false; anchorRef.current = null; setAnchor(null); setPreviewCell(null); }}
      >
      <Grid rows={p.rows} cols={p.cols} label="Shikaku grid. Drag between two opposite corners to draw a rectangle.">
        {Array.from({ length: p.rows * p.cols }, (_, cell) => {
          const region = session.state.regions[cell] ?? -1;
          const clue = p.clues.findIndex((item) => item.cell === cell);
          return (
            <button
              key={cell}
              className={`grid-cell shikaku-cell region-${region >= 0 ? region % 4 : 'empty'} ${anchor === cell ? 'corner-selected' : ''} ${inPreview(cell) ? 'preview-cell' : ''}`}
              data-cell={cell}
              onClick={() => {
                if (suppressClick.current) { suppressClick.current = false; return; }
                choose(cell);
              }}
              aria-label={`${cellName(cell, p.cols)}${clue >= 0 ? `, clue ${p.clues[clue]!.area}` : ''}${region >= 0 ? ', covered' : ', empty'}`}
            >
              {clue >= 0 ? <strong className={`clue-badge clue-${clue % 4}`}>{p.clues[clue]!.area}</strong> : null}
            </button>
          );
        })}
      </Grid>
      </div>
      <p className="input-help">Select two opposite corners. Every rectangle must contain one number and cover exactly that many cells.</p>
      {anchor !== null && (
        <button
          className="text-button shikaku-cancel"
          onClick={() => { anchorRef.current = null; setAnchor(null); setPreviewCell(null); }}
        >
          Cancel selection
        </button>
      )}
    </GameFrame>
  );
}
