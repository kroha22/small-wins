import { z } from 'zod';
import { cellName, type Engine, type Level, type PortableSession } from '../../shared/lib/engine';
import { puzzleCore } from '../../shared/lib/puzzle-core';
export const catIds = ['ginger', 'black', 'white', 'gray'] as const;
export type CatId = (typeof catIds)[number];
export const cats = catIds.map((id, i) => ({
  id,
  label: `${id[0]!.toUpperCase()}${id.slice(1)} cat`,
  mark: i + 1,
}));
const catSchema = z.enum(catIds);
export const clueSchema = z.discriminatedUnion('type', [
  z.object({ type: z.literal('inRow'), cat: catSchema, value: z.number().int() }),
  z.object({ type: z.literal('inColumn'), cat: catSchema, value: z.number().int() }),
  z.object({
    type: z.enum(['leftOf', 'rightOf', 'above', 'below']),
    cat: catSchema,
    other: catSchema,
  }),
  z.object({ type: z.enum(['inZone', 'notInZone']), cat: catSchema, zone: z.string() }),
  z.object({
    type: z.enum(['adjacentToFeature', 'notAdjacentToFeature']),
    cat: catSchema,
    feature: z.string(),
  }),
]);
export type Clue = z.infer<typeof clueSchema>;
export const payloadSchema = z
  .object({
    size: z.union([z.literal(3), z.literal(4)]),
    cats: z.array(z.object({ id: catSchema, label: z.string(), mark: z.number().int() })),
    zones: z
      .array(z.object({ id: z.string(), label: z.string(), cells: z.array(z.number().int()) }))
      .default([]),
    features: z
      .array(z.object({ id: z.string(), label: z.string(), cell: z.number().int() }))
      .default([]),
    clues: z.array(clueSchema).min(1),
  })
  .superRefine((p, c) => {
    const ids = p.cats.map((v) => v.id),
      max = p.size * p.size;
    const invalid =
      p.cats.length !== p.size ||
      ids.some((v, i) => v !== catIds[i]) ||
      p.cats.some((v, i) => v.mark !== i + 1) ||
      p.zones.some((v) => v.cells.some((n) => n < 0 || n >= max)) ||
      p.features.some((v) => v.cell < 0 || v.cell >= max) ||
      p.zones.some((v) => v.cells.length === 0) ||
      new Set(p.zones.flatMap((v) => v.cells)).size !== p.zones.flatMap((v) => v.cells).length ||
      new Set(p.features.map((v) => v.cell)).size !== p.features.length ||
      new Set(p.zones.map((v) => v.id)).size !== p.zones.length ||
      new Set(p.features.map((v) => v.id)).size !== p.features.length ||
      p.features.length !== p.zones.length ||
      p.features.some((feature) => !p.zones.some((zone) => zone.cells.includes(feature.cell))) ||
      new Set(p.features.map((feature) => p.zones.find((zone) => zone.cells.includes(feature.cell))?.id)).size !== p.features.length ||
      p.clues.some(
        (q) =>
          !ids.includes(q.cat) ||
          ('other' in q && (!ids.includes(q.other) || q.other === q.cat)) ||
          ('value' in q && (q.value < 0 || q.value >= p.size)) ||
          ('zone' in q && !p.zones.some((v) => v.id === q.zone)) ||
          ('feature' in q && !p.features.some((v) => v.id === q.feature)),
      );
    if (invalid) c.addIssue({ code: 'custom', message: 'Invalid cat grid or clue references' });
  });
export type Payload = z.infer<typeof payloadSchema>;
export type State = { placements: Partial<Record<CatId, number | null>> };
export type Action =
  { type: 'placeCat'; cat: CatId; cell: number } | { type: 'removeCat'; cat: CatId };

type CorePayload = {
  size: number;
  entities: { id: string }[];
  zones: { id: string; cells: number[] }[];
  features: { id: string; cell: number }[];
  clues: { type: string; entity: string; value?: number; other?: string; zone?: string; feature?: string }[];
};
type CoreLevel = {
  identity: {
    mechanicId: 'purrdoku';
    productGameId: 'purrdoku';
    levelPackId: string;
    levelId: string;
    schemaVersion: 1;
    rulesVersion: 1;
    contentVersion: number;
  };
  difficulty: { band: 'tutorial' | 'easy' | 'medium' | 'hard'; normalized: number; parameters: Record<string, never> };
  payload: CorePayload;
};
type CoreTransition = { accepted: boolean; changed: boolean; state: State; reason?: string };
type CoreInspection = { clueStatuses: ('unresolved' | 'satisfied' | 'violated')[]; conflicts: CatId[] };
type CoreHint = {
  kind: 'zone' | 'column' | 'landmark' | 'relative-cell' | 'exact-cell';
  data: { entity: CatId; cell: number; row: number; column: number; zone?: string; feature?: string };
};
type CoreSolveResult = { solutions: State[]; nodes: number; exceeded: boolean };
type CoreRestoreResult = { restored: boolean; session: PortableSession<State>; reason?: string };

const corePayload = (payload: Payload, clues = payload.clues): CorePayload => ({
  size: payload.size,
  entities: payload.cats.map(({ id }) => ({ id })),
  zones: payload.zones.map(({ id, cells }) => ({ id, cells })),
  features: payload.features.map(({ id, cell }) => ({ id, cell })),
  clues: clues.map(({ cat, ...clue }) => ({ ...clue, entity: cat })),
});
const difficultyValue = (difficulty: Level<Payload>['difficulty']) =>
  difficulty === 'tutorial' ? 0 : difficulty === 'easy' ? 0.25 : difficulty === 'hard' ? 0.75 : 0.5;
const coreLevel = (
  payload: Payload,
  metadata: Pick<Level<Payload>, 'id' | 'difficulty' | 'contentVersion'> = {
    id: 'legacy-adapter', difficulty: 'medium', contentVersion: 1,
  },
  clues = payload.clues,
): CoreLevel => ({
  identity: {
    mechanicId: 'purrdoku', productGameId: 'purrdoku', levelPackId: 'web-authored',
    levelId: metadata.id, schemaVersion: 1, rulesVersion: 1, contentVersion: metadata.contentVersion,
  },
  difficulty: { band: metadata.difficulty, normalized: difficultyValue(metadata.difficulty), parameters: {} },
  payload: corePayload(payload, clues),
});

export function clueStatus(p: Payload, s: State, q: Clue): 'unresolved' | 'satisfied' | 'violated' {
  return puzzleCore.inspect<CoreInspection>('purrdoku', coreLevel(p, undefined, [q]), s).clueStatuses[0]!;
}
export function clueText(p: Payload, q: Clue) {
  const name = `The ${q.cat} cat`;
  if ('value' in q) return `${name} is in ${q.type === 'inRow' ? 'row' : 'column'} ${q.value + 1}.`;
  if ('other' in q) {
    const word = {
      leftOf: 'to the left of',
      rightOf: 'to the right of',
      above: 'above',
      below: 'below',
    }[q.type];
    return `${name} is ${word} the ${q.other} cat.`;
  }
  if ('zone' in q)
    return `${name} is ${q.type === 'inZone' ? 'inside' : 'outside'} the ${p.zones.find((v) => v.id === q.zone)!.label}.`;
  return `${name} is ${q.type === 'adjacentToFeature' ? 'next to' : 'not next to'} the ${p.features.find((v) => v.id === q.feature)!.label}.`;
}
export function placementConflicts(p: Payload, s: State) {
  return new Set(puzzleCore.inspect<CoreInspection>('purrdoku', coreLevel(p), s).conflicts);
}

const reasonText: Record<string, string> = {
  'unknown-entity': 'Choose a cat from the tray.',
  'invalid-cell': 'Choose a cell on the board.',
  'feature-occupied': 'That spot belongs to an object. Choose an empty spot.',
};
export const engine: Engine<Payload, State, Action> = {
  initial: (p) => puzzleCore.initialState<State>('purrdoku', coreLevel(p)),
  validState: (p, s): s is State => {
    try {
      return puzzleCore.validateState<{ valid: boolean }>('purrdoku', coreLevel(p), s).valid;
    } catch {
      return false;
    }
  },
  apply(p, s, a) {
    const action = a.type === 'placeCat'
      ? { type: 'place', entity: a.cat, cell: a.cell }
      : { type: 'remove', entity: a.cat };
    const result = puzzleCore.transition<CoreTransition>('purrdoku', coreLevel(p), s, action);
    return { ...result, state: result.changed ? result.state : s, reason: result.reason ? reasonText[result.reason] ?? result.reason : undefined };
  },
  solved: (p, s) => puzzleCore.evaluate<{ solved: boolean }>('purrdoku', coreLevel(p), s).solved,
  hint: (p, s, used) => {
    const reply = solverHint(p, s, used);
    return reply.status === 'ok' ? reply.text : null;
  },
  persistence: {
    restore: (level, raw, fallbackAttemptId) => {
      if (raw == null) return null;
      try {
        const result = puzzleCore.restoreSession<CoreRestoreResult>(
          'purrdoku', coreLevel(level.payload, level), raw, fallbackAttemptId,
        );
        if (!result.restored) return null;
        const { attemptId, state, history, moveCount, hintsUsed, revision } = result.session;
        return { attemptId, state, history, moveCount, hintsUsed, revision };
      } catch {
        return null;
      }
    },
    encode: (level, session) => puzzleCore.encodeSession('purrdoku', coreLevel(level.payload, level), session),
  },
};
export function solve(p: Payload, partial: State = engine.initial(p), limit = 2, budget = 50000) {
  return puzzleCore.solveDetails<CoreSolveResult>('purrdoku', coreLevel(p), partial, limit, budget);
}
export function solverHint(p: Payload, s: State, used = 0) {
  const solved = solve(p);
  if (solved.exceeded) return { status: 'budget-exceeded' as const };
  if (solved.solutions.length !== 1) return { status: 'unavailable' as const };
  const hint = puzzleCore.hint<CoreHint | null>('purrdoku', coreLevel(p), s, used);
  if (!hint) return { status: 'unavailable' as const };
  const cat = p.cats.find((candidate) => candidate.id === hint.data.entity)!;
  const room = p.zones.find((candidate) => candidate.id === hint.data.zone);
  const feature = p.features.find((candidate) => candidate.id === hint.data.feature);
  const lead = `The ${cat.id} cat`;
  if (hint.kind === 'zone')
    return { status: 'ok' as const, text: `Extra clue 1: ${lead} belongs ${room ? `inside the ${room.label}` : `in row ${hint.data.row + 1}`}. Look for its spot there.` };
  if (hint.kind === 'column')
    return { status: 'ok' as const, text: `Extra clue 2: ${lead} belongs ${room ? `inside the ${room.label}` : `in row ${hint.data.row + 1}`}, in column ${hint.data.column + 1}.` };
  if (hint.kind === 'landmark') {
    if (!feature)
      return { status: 'ok' as const, text: `Closer clue: use the nearest object as a landmark for the ${cat.id} cat.` };
    const featureRow = Math.floor(feature.cell / p.size);
    const featureColumn = feature.cell % p.size;
    const relation = [
      hint.data.row < featureRow ? 'above' : hint.data.row > featureRow ? 'below' : '',
      hint.data.column < featureColumn
        ? 'to the left of'
        : hint.data.column > featureColumn
          ? 'to the right of'
          : '',
    ]
      .filter(Boolean)
      .join(' and ');
    return {
      status: 'ok' as const,
      text: `Closer clue: the ${cat.id} cat belongs ${relation ? `${relation} the ${feature.label}` : `on the square with the ${feature.label}`}. Use that object as your landmark.`,
    };
  }
  if (hint.kind === 'relative-cell') {
    const areaCells = room?.cells ?? Array.from({ length: p.size * p.size }, (_, index) => index);
    const rows = [...new Set(areaCells.map((cell) => Math.floor(cell / p.size)))].sort((a, b) => a - b);
    const rowCells = areaCells
      .filter((cell) => Math.floor(cell / p.size) === hint.data.row)
      .sort((a, b) => a - b);
    const rowIndex = rows.indexOf(hint.data.row);
    const columnIndex = rowCells.findIndex((cell) => cell % p.size === hint.data.column);
    const rowWord =
      rows.length === 1
        ? 'only'
        : rowIndex === 0
          ? 'top'
          : rowIndex === rows.length - 1
            ? 'bottom'
            : rows.length === 3
              ? 'middle'
              : rowIndex === 1
                ? 'upper middle'
                : 'lower middle';
    const columnWord =
      rowCells.length === 1
        ? 'only'
        : columnIndex === 0
          ? 'leftmost'
          : columnIndex === rowCells.length - 1
            ? 'rightmost'
            : rowCells.length === 3
              ? 'middle'
              : columnIndex === 1
                ? 'left-hand middle'
                : 'right-hand middle';
    return {
      status: 'ok' as const,
      text: `One more clue: in ${room ? `the ${room.label}` : 'the board'}, find its ${rowWord} row. Choose the ${columnWord} square in that row for the ${cat.id} cat.`,
    };
  }
  const blockers = p.cats.filter((candidate) => {
    const other = s.placements[candidate.id];
    return candidate.id !== cat.id && other != null &&
      (Math.floor(other / p.size) === hint.data.row || other % p.size === hint.data.column);
  });
  return { status: 'ok' as const, text: `Exact spot: place the ${cat.id} cat at ${cellName(hint.data.cell, p.size)}.${blockers.length ? ` First remove ${blockers.map((candidate) => `the ${candidate.id} cat`).join(' and ')} to free its row and column.` : ''}` };
}
