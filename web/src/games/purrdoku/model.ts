import { z } from 'zod';
import { accept, reject, cellName, type Engine } from '../../shared/lib/engine';
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
export function clueStatus(p: Payload, s: State, q: Clue): 'unresolved' | 'satisfied' | 'violated' {
  const a = s.placements[q.cat];
  if (a == null) return 'unresolved';
  const r = Math.floor(a / p.size),
    c = a % p.size;
  let ok: boolean;
  if ('value' in q) ok = q.type === 'inRow' ? r === q.value : c === q.value;
  else if ('other' in q) {
    const b = s.placements[q.other];
    if (b == null) return 'unresolved';
    const br = Math.floor(b / p.size),
      bc = b % p.size;
    ok =
      q.type === 'leftOf'
        ? c < bc
        : q.type === 'rightOf'
          ? c > bc
          : q.type === 'above'
            ? r < br
            : r > br;
  } else if ('zone' in q) {
    const inside = p.zones.find((z) => z.id === q.zone)!.cells.includes(a);
    ok = q.type === 'inZone' ? inside : !inside;
  } else {
    const f = p.features.find((v) => v.id === q.feature)!.cell;
    const adjacent = Math.abs(r - Math.floor(f / p.size)) + Math.abs(c - (f % p.size)) === 1;
    ok = q.type === 'adjacentToFeature' ? adjacent : !adjacent;
  }
  return ok ? 'satisfied' : 'violated';
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
function roomAt(p: Payload, cell: number) {
  return p.zones.find((zone) => zone.cells.includes(cell))?.id;
}
function featureAt(p: Payload, cell: number) {
  return p.features.find((feature) => feature.cell === cell);
}
export function placementConflicts(p: Payload, s: State) {
  const groups = new Map<string, CatId[]>();
  for (const cat of p.cats) {
    const cell = s.placements[cat.id];
    if (cell == null) continue;
    const room = roomAt(p, cell);
    const keys = [`row:${Math.floor(cell / p.size)}`, `column:${cell % p.size}`];
    if (room) keys.push(`room:${room}`);
    for (const key of keys) groups.set(key, [...(groups.get(key) ?? []), cat.id]);
  }
  return new Set([...groups.values()].filter((cats) => cats.length > 1).flat());
}
function structured(s: State, p: Payload) {
  const cells = Object.values(s.placements).filter((cell): cell is number => cell != null);
  const rows = cells.map((cell) => Math.floor(cell / p.size));
  const columns = cells.map((cell) => cell % p.size);
  const rooms = cells.map((cell) => roomAt(p, cell)).filter((room): room is string => room != null);
  return new Set(rows).size === rows.length && new Set(columns).size === columns.length && new Set(rooms).size === rooms.length;
}
const stateSchema = z.object({ placements: z.record(z.string(), z.number().int().nullable()) });
export const engine: Engine<Payload, State, Action> = {
  initial: (p) => ({ placements: Object.fromEntries(p.cats.map((c) => [c.id, null])) }),
  validState: (p, s): s is State => {
    const v = stateSchema.safeParse(s);
    if (!v.success) return false;
    const entries = Object.entries(v.data.placements);
    const cells = entries.flatMap(([, n]) => (n == null ? [] : [n]));
    return (
      entries.length === p.cats.length &&
      entries.every(([id]) => p.cats.some((c) => c.id === id)) &&
      cells.every((n) => n >= 0 && n < p.size * p.size && !featureAt(p, n)) &&
      new Set(cells).size === cells.length
    );
  },
  apply(p, s, a) {
    if (!p.cats.some((c) => c.id === a.cat)) return reject(s, 'Choose a cat from the tray.');
    if (a.type === 'removeCat') {
      if (s.placements[a.cat] == null) return accept(s, false);
      return accept({ placements: { ...s.placements, [a.cat]: null } });
    }
    if (
      a.type !== 'placeCat' ||
      !Number.isInteger(a.cell) ||
      a.cell < 0 ||
      a.cell >= p.size * p.size
    )
      return reject(s, 'Choose a cell on the board.');
    if (s.placements[a.cat] === a.cell) return accept(s, false);
    if (featureAt(p, a.cell)) return reject(s, 'That spot belongs to an object. Choose an empty spot.');
    const occupant = Object.entries(s.placements).find(([cat, cell]) => cat !== a.cat && cell === a.cell)?.[0] as CatId | undefined;
    if (!occupant) return accept({ placements: { ...s.placements, [a.cat]: a.cell } });
    return accept({ placements: { ...s.placements, [a.cat]: a.cell, [occupant]: s.placements[a.cat] ?? null } });
  },
  solved: (p, s) =>
    p.cats.every((c) => s.placements[c.id] != null) &&
    structured(s, p) &&
    p.clues.every((q) => clueStatus(p, s, q) === 'satisfied'),
  hint: (p, s, used) => {
    const reply = solverHint(p, s, used);
    return reply.status === 'ok' ? reply.text : null;
  },
};
export function solve(p: Payload, partial: State = engine.initial(p), limit = 2, budget = 50000) {
  const solutions: State[] = [];
  let nodes = 0,
    exceeded = false;
  if (!engine.validState(p, partial)) return { solutions, nodes, exceeded };
  const visit = (s: State) => {
    if (solutions.length >= limit || exceeded) return;
    if (++nodes > budget) {
      exceeded = true;
      return;
    }
    if (p.clues.some((q) => clueStatus(p, s, q) === 'violated') || !structured(s, p)) return;
    const cat = p.cats.find((c) => s.placements[c.id] == null);
    if (!cat) {
      solutions.push(s);
      return;
    }
    for (let cell = 0; cell < p.size * p.size; cell++) {
      if (Object.entries(s.placements).some(([id, placed]) => id !== cat.id && placed === cell)) continue;
      const t = engine.apply(p, s, { type: 'placeCat', cat: cat.id, cell });
      if (t.accepted && t.changed) visit(t.state);
      if (solutions.length >= limit || exceeded) break;
    }
  };
  visit(partial);
  return { solutions, nodes, exceeded };
}
export function solverHint(p: Payload, s: State, used = 0) {
  // Extra clues reveal facts about the puzzle, never validate a wrong hypothesis.
  const result = solve(p);
  if (result.exceeded) return { status: 'budget-exceeded' as const };
  if (result.solutions.length !== 1) return { status: 'unavailable' as const };
  const answer = result.solutions[0]!;
  const remaining = p.cats.filter((c) => s.placements[c.id] !== answer.placements[c.id]);
  if (!remaining.length) return { status: 'unavailable' as const };
  const cat = remaining[used < 4 ? 0 : (used - 4) % remaining.length]!;
  const cell = answer.placements[cat.id]!;
  const room = p.zones.find((z) => z.cells.includes(cell));
  const area = room ? `inside the ${room.label}` : `in row ${Math.floor(cell / p.size) + 1}`;
  const lead = `The ${cat.id} cat`;
  if (used === 0)
    return {
      status: 'ok' as const,
      text: `Extra clue 1: ${lead} belongs ${area}. Look for its spot there.`,
    };
  if (used === 1) {
    const candidates = (room?.cells ?? []).filter(
      (candidate) => candidate % p.size === cell % p.size && !featureAt(p, candidate),
    );
    const countText = candidates.length > 1
      ? ` This still leaves ${candidates.length} possible squares; the next clues separate them.`
      : ' This identifies the only available square in that room and column.';
    return {
      status: 'ok' as const,
      text: `Extra clue 2: ${lead} belongs ${area}, in column ${(cell % p.size) + 1}.${countText}`,
    };
  }
  const row = Math.floor(cell / p.size),
    column = cell % p.size;
  const areaCells = room?.cells ?? Array.from({ length: p.size * p.size }, (_, i) => i);
  const rows = [...new Set(areaCells.map((c) => Math.floor(c / p.size)))].sort((a, b) => a - b);
  const rowCells = areaCells.filter((c) => Math.floor(c / p.size) === row).sort((a, b) => a - b);
  const rowIndex = rows.indexOf(row),
    columnIndex = rowCells.indexOf(cell);
  const rowWords =
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
  const columnWords =
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
  const areaName = room ? `the ${room.label}` : 'the board';
  if (used === 2) {
    // Relate the answer to a visible object, without revealing numeric coordinates.
    const feature = [...p.features].sort((a, b) => {
      const distance = (c: number) =>
        Math.abs(row - Math.floor(c / p.size)) + Math.abs(column - (c % p.size));
      return distance(a.cell) - distance(b.cell);
    })[0];
    const relation = feature
      ? [
          row < Math.floor(feature.cell / p.size)
            ? 'above'
            : row > Math.floor(feature.cell / p.size)
              ? 'below'
              : '',
          column < feature.cell % p.size
            ? 'to the left of'
            : column > feature.cell % p.size
              ? 'to the right of'
              : '',
        ]
          .filter(Boolean)
          .join(' and ')
      : '';
    return {
      status: 'ok' as const,
      text: feature
        ? `Closer clue: ${lead} belongs ${relation ? `${relation} the ${feature.label}` : `on the square with the ${feature.label}`}. Use that object as your landmark.`
        : `Closer clue: look in the ${rowWords} row of ${areaName} for the ${cat.id} cat.`,
    };
  }
  if (used === 3)
    return {
      status: 'ok' as const,
      text: `One more clue: in ${areaName}, find its ${rowWords} row. Choose the ${columnWords} square in that row for the ${cat.id} cat.`,
    };
  const blockers = p.cats.filter((c) => {
    const other = s.placements[c.id];
    return (
      c.id !== cat.id &&
      other != null &&
      (Math.floor(other / p.size) === Math.floor(cell / p.size) || other % p.size === cell % p.size)
    );
  });
  return {
    status: 'ok' as const,
    text: `Exact spot: place the ${cat.id} cat at ${cellName(cell, p.size)}.${blockers.length ? ` First remove ${blockers.map((c) => `the ${c.id} cat`).join(' and ')} to free its row and column.` : ''}`,
  };
}
