import { z } from 'zod';
import { accept, reject, type Engine, type Level, type PortableSession } from '../../shared/lib/engine';
import { puzzleCore } from '../../shared/lib/puzzle-core';
import {
  payloadSchema,
  type Action,
  type LocalAction,
  type LocalState,
  type Mark,
  type Payload,
  type State,
} from './legacy-model';

export { payloadSchema };
export type { Action, LocalAction, LocalState, Mark, Payload, State };

type CoreMark = 'UNKNOWN' | 'FOUND' | 'EMPTY';
type CoreState = { marks: CoreMark[] };
type CorePayload = {
  rows: number;
  cols: number;
  theme: string;
  rowCounts: number[];
  columnCounts: number[];
  groups: { id: string; length: number; species: string }[];
  occupied: number[];
};
type CoreLevel = {
  identity: {
    mechanicId: 'habitat-search';
    productGameId: 'wildlife-survey';
    levelPackId: string;
    levelId: string;
    schemaVersion: 1;
    rulesVersion: 1;
    contentVersion: number;
  };
  difficulty: {
    band: 'tutorial' | 'easy' | 'medium' | 'hard';
    normalized: number;
    parameters: Record<string, never>;
  };
  payload: CorePayload;
};
type CoreTransition = { accepted: boolean; changed: boolean; state: CoreState; reason?: string };
type CoreHint = {
  kind: 'scan-row' | 'exact-cell';
  data: { cell: number; row: number; column: number; mark: 'found' | 'empty' };
};
type CoreRestoreResult = { restored: boolean; session: PortableSession<CoreState>; reason?: string };

const markSchema = z.enum(['unknown', 'found', 'empty']);
const stateSchema = z.object({ marks: z.array(markSchema) });
const difficultyValue = (difficulty: Level<Payload>['difficulty']) =>
  difficulty === 'tutorial' ? 0 : difficulty === 'easy' ? 0.25 : difficulty === 'hard' ? 0.75 : 0.5;
const toCoreMark = (mark: Mark): CoreMark => mark.toUpperCase() as CoreMark;
const fromCoreMark = (mark: CoreMark): Mark => mark.toLowerCase() as Mark;
const toCoreState = (state: State): CoreState => ({ marks: state.marks.map(toCoreMark) });
const fromCoreState = (state: CoreState): State => ({ marks: state.marks.map(fromCoreMark) });

const withOccupied = (payload: Payload, occupied: number[]): Payload => ({
  ...payload,
  occupied,
  rowCounts: Array.from({ length: payload.rows }, (_, row) => occupied.filter((cell) => Math.floor(cell / payload.cols) === row).length),
  colCounts: Array.from({ length: payload.cols }, (_, column) => occupied.filter((cell) => cell % payload.cols === column).length),
});

const corePayload = (payload: Payload): CorePayload => ({
  rows: payload.rows,
  cols: payload.cols,
  theme: payload.theme,
  rowCounts: payload.rowCounts,
  columnCounts: payload.colCounts,
  groups: payload.chains,
  occupied: payload.occupied,
});

const coreLevel = (
  payload: Payload,
  metadata: Pick<Level<Payload>, 'id' | 'difficulty' | 'contentVersion'> = {
    id: 'legacy-adapter',
    difficulty: 'medium',
    contentVersion: 1,
  },
): CoreLevel => ({
  identity: {
    mechanicId: 'habitat-search',
    productGameId: 'wildlife-survey',
    levelPackId: 'web-authored',
    levelId: metadata.id,
    schemaVersion: 1,
    rulesVersion: 1,
    contentVersion: metadata.contentVersion,
  },
  difficulty: { band: metadata.difficulty, normalized: difficultyValue(metadata.difficulty), parameters: {} },
  payload: corePayload(payload),
});

export const engine: Engine<Payload, State, Action> = {
  initial: (payload) => fromCoreState(puzzleCore.initialState<CoreState>('habitat-search', coreLevel(payload))),
  validState: (payload, state): state is State => {
    if (!stateSchema.safeParse(state).success) return false;
    try {
      return puzzleCore.validateState<{ valid: boolean }>(
        'habitat-search',
        coreLevel(payload),
        toCoreState(state as State),
      ).valid;
    } catch {
      return false;
    }
  },
  apply(payload, state, action) {
    const result = puzzleCore.transition<CoreTransition>(
      'habitat-search',
      coreLevel(payload),
      toCoreState(state),
      { type: action.type, cell: action.cell, mark: toCoreMark(action.mark) },
    );
    return {
      ...result,
      state: result.changed ? fromCoreState(result.state) : state,
      reason: result.reason === 'cell-out-of-bounds' ? 'Choose a cell on the board.' : result.reason,
    };
  },
  solved: (payload, state) =>
    puzzleCore.evaluate<{ solved: boolean }>('habitat-search', coreLevel(payload), toCoreState(state)).solved,
  hint: (payload, state, used) => {
    const hint = puzzleCore.hint<CoreHint | null>(
      'habitat-search',
      coreLevel(payload),
      toCoreState(state),
      used,
    );
    if (!hint) return null;
    if (hint.kind === 'scan-row')
      return `Scan row ${hint.data.row + 1}. One marked cell belongs to a hidden ${payload.theme.toLowerCase()} chain.`;
    return `Mark row ${hint.data.row + 1}, column ${hint.data.column + 1} as ${hint.data.mark}.`;
  },
  persistence: {
    restore: (level, raw, fallbackAttemptId) => {
      if (raw == null) return null;
      try {
        const result = puzzleCore.restoreSession<CoreRestoreResult>(
          'habitat-search',
          coreLevel(level.payload, level),
          raw,
          fallbackAttemptId,
        );
        if (!result.restored) return null;
        const { attemptId, state, history, moveCount, hintsUsed, revision } = result.session;
        return {
          attemptId,
          state: fromCoreState(state),
          history: history.map(fromCoreState),
          moveCount,
          hintsUsed,
          revision,
        };
      } catch {
        return null;
      }
    },
    encode: (level, session) =>
      puzzleCore.encodeSession('habitat-search', coreLevel(level.payload, level), {
        ...session,
        state: toCoreState(session.state),
        history: session.history.map(toCoreState),
      }),
  },
};

export const localEngine: Engine<Payload, LocalState, LocalAction> = {
  initial: (payload) => ({ players: [engine.initial(payload), engine.initial(payload)], activePlayer: 0 }),
  validState: (payload, state): state is LocalState => {
    if (
      typeof state !== 'object' ||
      state == null ||
      !('activePlayer' in state) ||
      !('players' in state) ||
      ((state as LocalState).activePlayer !== 0 && (state as LocalState).activePlayer !== 1) ||
      !Array.isArray((state as LocalState).players) ||
      (state as LocalState).players.length !== 2
    ) return false;
    return (state as LocalState).players.every((player, index) =>
      engine.validState(withOccupied(payload, payload.localOccupied?.[index] ?? payload.occupied), player),
    );
  },
  apply(payload, state, action) {
    if (action.type === 'pass') return accept({ ...state, activePlayer: state.activePlayer === 0 ? 1 : 0 });
    if (action.type === 'restart') return accept({ players: [engine.initial(payload), engine.initial(payload)], activePlayer: 0 });
    const playerPayload = withOccupied(payload, payload.localOccupied?.[state.activePlayer] ?? payload.occupied);
    const next = engine.apply(playerPayload, state.players[state.activePlayer]!, action);
    if (!next.accepted) return reject(state, next.reason ?? 'That mark could not be placed.');
    const players = [...state.players] as [State, State];
    players[state.activePlayer] = next.state;
    return { ...next, state: { players, activePlayer: state.activePlayer } };
  },
  solved: (payload, state) => {
    const playerPayload = withOccupied(payload, payload.localOccupied?.[state.activePlayer] ?? payload.occupied);
    return engine.solved(playerPayload, state.players[state.activePlayer]!);
  },
};
