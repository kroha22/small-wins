import { z } from 'zod';
import { accept, reject, type Engine } from '../../shared/lib/engine';

const markSchema = z.enum(['unknown', 'found', 'empty']);
export const payloadSchema = z
  .object({
    rows: z.number().int().min(4).max(8),
    cols: z.number().int().min(4).max(8),
    theme: z.enum(['Burrows', 'Pond Life']),
    rowCounts: z.array(z.number().int().min(0)),
    colCounts: z.array(z.number().int().min(0)),
    chains: z.array(z.object({ id: z.string(), length: z.number().int().min(1).max(4), species: z.enum(['fish', 'turtle', 'squirrel', 'rabbit']) })).min(1),
    occupied: z.array(z.number().int().nonnegative()),
    localOccupied: z.array(z.array(z.number().int().nonnegative())).length(2).optional(),
  })
  .superRefine((p, ctx) => {
    const size = p.rows * p.cols;
    if (p.rowCounts.length !== p.rows || p.colCounts.length !== p.cols)
      ctx.addIssue({ code: 'custom', message: 'Edge clues must match the board.' });
    if (p.occupied.some((cell) => cell >= size) || new Set(p.occupied).size !== p.occupied.length)
      ctx.addIssue({ code: 'custom', message: 'Occupied cells must be unique and inside the board.' });
    if (p.occupied.length !== p.chains.reduce((sum, chain) => sum + chain.length, 0))
      ctx.addIssue({ code: 'custom', message: 'Chain lengths must match occupied cells.' });
    if (p.rowCounts.reduce((a, b) => a + b, 0) !== p.occupied.length || p.colCounts.reduce((a, b) => a + b, 0) !== p.occupied.length)
      ctx.addIssue({ code: 'custom', message: 'Edge clues must match the occupied total.' });
    if (p.localOccupied && p.localOccupied.some((board) => board.some((cell) => cell >= size) || new Set(board).size !== board.length))
      ctx.addIssue({ code: 'custom', message: 'Local boards must contain unique cells inside the board.' });
  });

export type Payload = z.infer<typeof payloadSchema>;
export type Mark = z.infer<typeof markSchema>;
export type State = { marks: Mark[] };
export type Action = { type: 'mark'; cell: number; mark: Mark };

export type LocalState = { players: State[]; activePlayer: 0 | 1 };
export type LocalAction = Action | { type: 'pass' } | { type: 'restart' };

export const engine: Engine<Payload, State, Action> = {
  initial: (p) => ({ marks: Array(p.rows * p.cols).fill('unknown') }),
  validState: (p, state): state is State =>
    z.object({ marks: z.array(markSchema).length(p.rows * p.cols) }).safeParse(state).success,
  apply(p, state, action) {
    if (action.type !== 'mark' || !Number.isInteger(action.cell) || action.cell < 0 || action.cell >= p.rows * p.cols)
      return reject(state, 'Choose a cell on the board.');
    if (!markSchema.safeParse(action.mark).success)
      return reject(state, 'Choose found, empty or clear.');
    const marks = [...state.marks];
    marks[action.cell] = marks[action.cell] === action.mark ? 'unknown' : action.mark;
    return accept({ marks });
  },
  solved: (p, state) =>
    p.occupied.every((cell) => state.marks[cell] === 'found'),
  hint: (p, state, used) => {
    const cell = p.occupied.find((candidate) => state.marks[candidate] !== 'found') ??
      Array.from({ length: p.rows * p.cols }, (_, candidate) => candidate).find(
        (candidate) => !p.occupied.includes(candidate) && state.marks[candidate] !== 'empty',
      );
    if (cell === undefined) return null;
    const row = Math.floor(cell / p.cols) + 1;
    const col = (cell % p.cols) + 1;
    return used === 0
      ? `Scan row ${row}. One marked cell belongs to a hidden ${p.theme.toLowerCase()} chain.`
      : `Mark row ${row}, column ${col} as ${p.occupied.includes(cell) ? 'found' : 'empty'}.`;
  },
};

export const localEngine: Engine<Payload, LocalState, LocalAction> = {
  initial: (p) => ({ players: [engine.initial(p), engine.initial(p)], activePlayer: 0 }),
  validState: (p, state): state is LocalState => {
    if (!z.object({ activePlayer: z.union([z.literal(0), z.literal(1)]), players: z.array(z.unknown()).length(2) }).safeParse(state).success) return false;
    const value = state as LocalState;
    return value.players.every((player) => engine.validState(p, player));
  },
  apply(p, state, action) {
    if (action.type === 'pass') return accept({ ...state, activePlayer: state.activePlayer === 0 ? 1 : 0 });
    if (action.type === 'restart') return accept({ players: [engine.initial(p), engine.initial(p)], activePlayer: 0 });
    const board = p.localOccupied?.[state.activePlayer];
    const playerPayload = board ? { ...p, occupied: board } : p;
    const next = engine.apply(playerPayload, state.players[state.activePlayer]!, action);
    if (!next.accepted) return reject(state, next.reason ?? 'That mark could not be placed.');
    const players = [...state.players] as [State, State];
    players[state.activePlayer] = next.state;
    return { ...next, state: { players, activePlayer: state.activePlayer } };
  },
  solved: (p, state) => {
    const target = p.localOccupied?.[state.activePlayer];
    return engine.solved(target ? { ...p, occupied: target } : p, state.players[state.activePlayer]!);
  },
};
