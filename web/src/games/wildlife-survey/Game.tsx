import { useState, type CSSProperties } from 'react';
import { Fish, Rabbit, Sprout, Squirrel, Turtle, Waves } from 'lucide-react';
import { z } from 'zod';
import rawLevels from './levels.json';
import { payloadSchema, engine, localEngine, type Action } from './model';
import { metadataSchema } from '../../shared/lib/engine';
import { useSession } from '../../features/game-session/use-session';
import { GameFrame } from '../../shared/ui/GameFrame';
import { Grid } from '../../shared/ui/Grid';
import './wildlife-survey.css';

const levels = z.array(metadataSchema.extend({ payload: payloadSchema })).parse(rawLevels);
const coordinateSymbols = ['✦', '•', '○', '△', '◇', '✚', '✳', '⬢'];
const chainColour = (length: number) => `chain-size-${Math.min(length, 4)}`;
function Board({ rows, cols, theme, marks, solutionCells, speciesByCell, groupByCell, inputMode, notes, onNote, onMark, label }: { rows:number; cols:number; theme:'Burrows'|'Pond Life'; marks:string[]; solutionCells:number[]; speciesByCell: Record<number, 'fish'|'turtle'|'squirrel'|'rabbit'>; groupByCell: Record<number, number>; inputMode:'observe'|'note'; notes:Set<number>; onNote:(cell:number)=>void; onMark:(action:Action)=>void; label:string }) {
  return <div className="wildlife-coordinate-wrap"><div className="wildlife-column-symbols" aria-hidden="true">{coordinateSymbols.slice(0, cols).map((symbol) => <span key={symbol}>{symbol}</span>)}</div><div className="wildlife-grid-row"><div className="wildlife-row-numbers" aria-hidden="true">{Array.from({ length: rows }, (_, index) => <span key={index}>{index + 1}</span>)}</div><Grid rows={rows} cols={cols} label={label}>{marks.map((mark, cell) => {
    const hasHabitat = solutionCells.includes(cell);
    const next = mark === 'unknown' ? (hasHabitat ? 'found' : 'empty') : 'unknown';
    const checked = mark !== 'unknown';
    const noted = notes.has(cell);
    const Habitat = speciesByCell[cell] === 'fish' ? Fish : speciesByCell[cell] === 'turtle' ? Turtle : speciesByCell[cell] === 'rabbit' ? Rabbit : Squirrel;
    const Ambient = theme === 'Pond Life' ? Waves : Sprout;
    return <button type="button" data-cell={cell} key={cell} className={`grid-cell wildlife-cell ${theme === 'Pond Life' ? 'pond-cell' : 'burrow-cell'} ${speciesByCell[cell] ? `species-${speciesByCell[cell]}` : ''} ${mark} ${noted ? 'noted' : ''} ${groupByCell[cell] !== undefined ? `group-color-${groupByCell[cell] % 4}` : ''} ${checked ? (mark === 'found' ? 'correct' : 'miss') : ''}`} onClick={() => inputMode === 'note' ? onNote(cell) : mark === 'unknown' ? onMark({type:'mark', cell, mark: next}) : undefined} aria-label={`row ${Math.floor(cell / cols)+1}, column ${(cell%cols)+1}, ${noted ? 'noted' : mark === 'unknown' ? 'unobserved' : mark === 'found' ? 'found' : 'empty'}`}><span className="wildlife-mark">{noted ? '×' : checked ? (mark === 'found' ? <Habitat size={28} strokeWidth={2} /> : <Ambient size={25} strokeWidth={1.9} />) : <Ambient size={19} strokeWidth={1.6} />}</span></button>;
  })}</Grid></div></div>;
}
function ChainLegend({ chains, theme }: { chains: { id: string; length: number; species: 'fish'|'turtle'|'squirrel'|'rabbit' }[]; theme: 'Burrows' | 'Pond Life' }) {
  const counts = chains.reduce<Record<string, number>>((result, chain) => ({ ...result, [`${chain.species}-${chain.length}`]: (result[`${chain.species}-${chain.length}`] ?? 0) + 1 }), {});
  const icons = { fish: Fish, turtle: Turtle, squirrel: Squirrel, rabbit: Rabbit };
  const names = { fish: 'fish', turtle: 'turtles', squirrel: 'squirrels', rabbit: 'rabbits' };
  return <div className={`wildlife-chain-legend ${theme === 'Pond Life' ? 'pond-legend' : 'burrow-legend'}`} aria-label="Hidden habitats"><strong>{theme === 'Pond Life' ? 'Find these pond groups' : 'Find these wild groups'}</strong>{Object.entries(counts).sort(([a], [b]) => Number(a.split('-').at(-1)) - Number(b.split('-').at(-1))).map(([key, count]) => {
    const [species, length] = key.split('-') as [keyof typeof icons, string]; const size = Number(length); const Icon = icons[species];
    return <span className={`chain-key ${chainColour(size)} species-${species}`} key={key}><i className="chain-shape">{Array.from({ length: Math.min(size, 4) }, (_, index) => <b key={index}><Icon size={19} strokeWidth={2} /></b>)}</i><span>{size} {names[species]}</span><small>× {count}</small></span>;
  })}</div>;
}
function speciesByCell(occupied: number[], chains: { length: number; species: 'fish'|'turtle'|'squirrel'|'rabbit' }[]) {
  return chains.reduce<{ map: Record<number, 'fish'|'turtle'|'squirrel'|'rabbit'>; offset: number }>((result, chain) => {
    occupied.slice(result.offset, result.offset + chain.length).forEach((cell) => { result.map[cell] = chain.species; });
    return { map: result.map, offset: result.offset + chain.length };
  }, { map: {}, offset: 0 }).map;
}
function groupByCell(occupied: number[], chains: { length: number }[]) {
  return chains.reduce<{ map: Record<number, number>; offset: number }>((result, chain, index) => { occupied.slice(result.offset, result.offset + chain.length).forEach((cell) => { result.map[cell] = index; }); return { map: result.map, offset: result.offset + chain.length }; }, { map: {}, offset: 0 }).map;
}
function InputMode({ mode, setMode }: { mode: 'observe' | 'note'; setMode: (mode: 'observe' | 'note') => void }) {
  return <div className="wildlife-input-mode" aria-label="Cell input mode"><button className={mode === 'observe' ? 'active' : ''} onClick={() => setMode('observe')}>Observe</button><button className={mode === 'note' ? 'active' : ''} onClick={() => setMode('note')}>Note ×</button></div>;
}
export default function Game({ levelId }: { levelId: string }) {
  const level = levels.find((item) => item.id === levelId)!;
  const [mode, setMode] = useState<'solo'|'local'>('solo');
  const [inputMode, setInputMode] = useState<'observe'|'note'>('observe');
  const [notes, setNotes] = useState<Set<number>>(new Set());
  const solo = useSession('wildlife-survey', level, engine);
  const local = useSession('wildlife-survey', level, localEngine);
  const p = level.payload;
  const soloSpecies = speciesByCell(p.occupied, p.chains);
  const soloGroups = groupByCell(p.occupied, p.chains);
  const toggleNote = (cell: number) => setNotes((previous) => { const next = new Set(previous); if (next.has(cell)) next.delete(cell); else next.add(cell); return next; });
  if (mode === 'solo') return <GameFrame gameId="wildlife-survey" level={level} {...solo}>
    <div className="wildlife-mode"><button className="mode-tab active">Solo Search</button><button className="mode-tab" onClick={() => setMode('local')}>Play Together</button></div>
    <div className="wildlife-topline"><span>{p.theme}</span><span>Found {solo.session.state.marks.filter((m, i) => m === 'found' && p.occupied.includes(i)).length} / {p.occupied.length}</span></div>
    <ChainLegend chains={p.chains} theme={p.theme} />
    <InputMode mode={inputMode} setMode={setInputMode} />
    <div className="wildlife-board" style={{'--rows': p.rows, '--cols': p.cols} as CSSProperties}><Board rows={p.rows} cols={p.cols} theme={p.theme} marks={solo.session.state.marks} solutionCells={p.occupied} speciesByCell={soloSpecies} groupByCell={soloGroups} inputMode={inputMode} notes={notes} onNote={toggleNote} onMark={solo.play} label="Wildlife survey grid"/></div>
    <p className="input-help">Tap a square to observe it. A green check means something was found; a red cross means the space is empty.</p>
  </GameFrame>;
  const active = local.session.state.activePlayer;
  const board = local.session.state.players[active]!;
  return <GameFrame gameId="wildlife-survey" level={level} {...local} canHint={false}>
    <div className="wildlife-mode"><button className="mode-tab" onClick={() => setMode('solo')}>Solo Search</button><button className="mode-tab active">Play Together</button></div>
    <div className="pass-card"><strong>Player {active + 1}'s survey</strong><span>Pass the device when you are ready.</span><button className="button secondary" onClick={() => local.play({type:'pass'})}>Pass device</button></div>
    <div className="wildlife-topline"><span>{p.theme}</span><span>Found {board.marks.filter((m, i) => m === 'found' && (p.localOccupied?.[active] ?? p.occupied).includes(i)).length} / {(p.localOccupied?.[active] ?? p.occupied).length}</span></div>
    <ChainLegend chains={p.chains} theme={p.theme} />
    <InputMode mode={inputMode} setMode={setInputMode} />
    <div className="wildlife-board" style={{'--rows': p.rows, '--cols': p.cols} as CSSProperties}><Board rows={p.rows} cols={p.cols} theme={p.theme} marks={board.marks} solutionCells={p.localOccupied?.[active] ?? p.occupied} speciesByCell={soloSpecies} groupByCell={soloGroups} inputMode={inputMode} notes={notes} onNote={toggleNote} onMark={(action)=>local.play(action)} label={`Player ${active+1} wildlife survey grid`}/></div>
    <p className="input-help">Each player has a private hidden map. Take turns observing squares, then pass the device. Find every habitat first.</p>
  </GameFrame>;
}
