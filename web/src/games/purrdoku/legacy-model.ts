import { accept, reject, type Engine } from '../../shared/lib/engine';
import type { Action, CatId, Clue, Payload, State } from './model';

const roomAt = (payload: Payload, cell: number) =>
  payload.zones.find((zone) => zone.cells.includes(cell))?.id;
const featureAt = (payload: Payload, cell: number) =>
  payload.features.find((feature) => feature.cell === cell);

export function clueStatus(payload: Payload, state: State, clue: Clue) {
  const first = state.placements[clue.cat];
  if (first == null) return 'unresolved' as const;
  const row = Math.floor(first / payload.size);
  const column = first % payload.size;
  let matches: boolean;
  if ('value' in clue) matches = clue.type === 'inRow' ? row === clue.value : column === clue.value;
  else if ('other' in clue) {
    const second = state.placements[clue.other];
    if (second == null) return 'unresolved' as const;
    const otherRow = Math.floor(second / payload.size);
    const otherColumn = second % payload.size;
    matches =
      clue.type === 'leftOf'
        ? column < otherColumn
        : clue.type === 'rightOf'
          ? column > otherColumn
          : clue.type === 'above'
            ? row < otherRow
            : row > otherRow;
  } else if ('zone' in clue) {
    const inside = roomAt(payload, first) === clue.zone;
    matches = clue.type === 'inZone' ? inside : !inside;
  } else {
    const feature = payload.features.find((candidate) => candidate.id === clue.feature)!.cell;
    const adjacent =
      Math.abs(row - Math.floor(feature / payload.size)) + Math.abs(column - (feature % payload.size)) === 1;
    matches = clue.type === 'adjacentToFeature' ? adjacent : !adjacent;
  }
  return matches ? ('satisfied' as const) : ('violated' as const);
}

export function placementConflicts(payload: Payload, state: State) {
  const groups = new Map<string, CatId[]>();
  for (const cat of payload.cats) {
    const cell = state.placements[cat.id];
    if (cell == null) continue;
    const keys = [`row:${Math.floor(cell / payload.size)}`, `column:${cell % payload.size}`];
    const room = roomAt(payload, cell);
    if (room) keys.push(`room:${room}`);
    for (const key of keys) groups.set(key, [...(groups.get(key) ?? []), cat.id]);
  }
  return new Set([...groups.values()].filter((entities) => entities.length > 1).flat());
}

const structured = (state: State, payload: Payload) => placementConflicts(payload, state).size === 0;

export const engine: Engine<Payload, State, Action> = {
  initial: (payload) => ({ placements: Object.fromEntries(payload.cats.map((cat) => [cat.id, null])) }),
  validState: (payload, state): state is State => {
    if (typeof state !== 'object' || state == null || !('placements' in state)) return false;
    const placements = (state as State).placements;
    const entries = Object.entries(placements);
    const cells = entries.flatMap(([, cell]) => (cell == null ? [] : [cell]));
    return (
      entries.length === payload.cats.length &&
      entries.every(([id]) => payload.cats.some((cat) => cat.id === id)) &&
      cells.every((cell) => Number.isInteger(cell) && cell >= 0 && cell < payload.size ** 2 && !featureAt(payload, cell)) &&
      new Set(cells).size === cells.length
    );
  },
  apply(payload, state, action) {
    if (!payload.cats.some((cat) => cat.id === action.cat)) return reject(state, 'unknown');
    if (action.type === 'removeCat') {
      if (state.placements[action.cat] == null) return accept(state, false);
      return accept({ placements: { ...state.placements, [action.cat]: null } });
    }
    if (!Number.isInteger(action.cell) || action.cell < 0 || action.cell >= payload.size ** 2)
      return reject(state, 'invalid');
    if (featureAt(payload, action.cell)) return reject(state, 'blocked');
    if (state.placements[action.cat] === action.cell) return accept(state, false);
    const occupant = Object.entries(state.placements).find(
      ([entity, cell]) => entity !== action.cat && cell === action.cell,
    )?.[0] as CatId | undefined;
    return accept({
      placements: {
        ...state.placements,
        [action.cat]: action.cell,
        ...(occupant ? { [occupant]: state.placements[action.cat] ?? null } : {}),
      },
    });
  },
  solved: (payload, state) =>
    payload.cats.every((cat) => state.placements[cat.id] != null) &&
    structured(state, payload) &&
    payload.clues.every((clue) => clueStatus(payload, state, clue) === 'satisfied'),
};

export function solve(payload: Payload, partial: State = engine.initial(payload), limit = 2, budget = 50_000) {
  const solutions: State[] = [];
  let nodes = 0;
  let exceeded = false;
  const visit = (state: State) => {
    if (solutions.length >= limit || exceeded) return;
    nodes += 1;
    if (nodes > budget) {
      exceeded = true;
      return;
    }
    if (!structured(state, payload) || payload.clues.some((clue) => clueStatus(payload, state, clue) === 'violated')) return;
    const entity = payload.cats.find((cat) => state.placements[cat.id] == null);
    if (!entity) {
      solutions.push(state);
      return;
    }
    for (let cell = 0; cell < payload.size ** 2; cell += 1) {
      if (
        Object.entries(state.placements).some(
          ([id, placed]) => id !== entity.id && placed === cell,
        )
      )
        continue;
      const transition = engine.apply(payload, state, { type: 'placeCat', cat: entity.id, cell });
      if (transition.accepted && transition.changed) visit(transition.state);
      if (solutions.length >= limit || exceeded) break;
    }
  };
  if (engine.validState(payload, partial)) visit(partial);
  return { solutions, nodes, exceeded };
}
