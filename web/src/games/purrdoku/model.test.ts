import { describe, it, expect } from 'vitest';
import { engine, payloadSchema, solve, clueStatus, solverHint, placementConflicts } from './model';
import levels from './levels.json';
const p = payloadSchema.parse(levels[0]!.payload);
describe('Purrdoku', () => {
  it('has a unique tutorial assignment', () => {
    const r = solve(p);
    expect(r.exceeded).toBe(false);
    expect(r.solutions).toEqual([{ placements: { ginger: 0, black: 4, white: 8 } }]);
  });
  it('accepts a hypothesis violating a clue, without mutating input', () => {
    const s = engine.initial(p);
    const t = engine.apply(p, s, { type: 'placeCat', cat: 'ginger', cell: 6 });
    expect(t.accepted).toBe(true);
    expect(s.placements.ginger).toBeNull();
    expect(clueStatus(p, t.state, p.clues[0]!)).toBe('violated');
  });
  it('allows free hypotheses and atomically swaps occupied cells', () => {
    const s = { placements: { ginger: 0, black: null, white: null } };
    expect(engine.apply(p, s, { type: 'placeCat', cat: 'black', cell: 2 }).accepted).toBe(true);
    const swapped = engine.apply(p, { placements: { ginger: 0, black: 2, white: null } }, { type: 'placeCat', cat: 'ginger', cell: 2 });
    expect(swapped.state.placements).toEqual({ ginger: 2, black: 0, white: null });
  });
  it('keeps two-cat clues unresolved until both cats are placed', () => {
    const s = { placements: { ginger: 0, black: 4, white: null } };
    expect(clueStatus(p, s, { type: 'below', cat: 'white', other: 'black' })).toBe('unresolved');
  });
  it('distinguishes ambiguous, impossible and budget exhausted', () => {
    expect(solve({ ...p, clues: [] }).solutions).toHaveLength(2);
    expect(
      solve({ ...p, clues: [...p.clues, { type: 'inRow', cat: 'ginger', value: 2 }] }).solutions,
    ).toHaveLength(0);
    expect(solve(p, engine.initial(p), 2, 1).exceeded).toBe(true);
  });
  it('does not announce solved with unplaced cats', () =>
    expect(engine.solved(p, engine.initial(p))).toBe(false));
  it('starts with an extra clue rather than an exact answer', () =>
    expect(solverHint(p, engine.initial(p))).toMatchObject({
      status: 'ok',
      text: expect.stringContaining('Extra clue 1'),
    }));
  it('rejects corrupt placement records', () => {
    expect(engine.validState(p, { placements: { ginger: 0, black: 1, white: null } })).toBe(false);
    expect(
      engine.validState(p, { placements: { ginger: 0, black: 0, white: 8, gray: null } }),
    ).toBe(false);
  });
  it('uses Manhattan adjacency, not diagonals or the feature cell itself', () => {
    const l = { ...p, features: [{ id: 'window', label: 'window', cell: 4 }] };
    const q = { type: 'adjacentToFeature' as const, cat: 'ginger' as const, feature: 'window' };
    for (const cell of [0, 4])
      expect(clueStatus(l, { placements: { ginger: cell } }, q)).toBe('violated');
    expect(clueStatus(l, { placements: { ginger: 1 } }, q)).toBe('satisfied');
  });
  it('checks room membership separately from row occupancy and object adjacency', () => {
    const l = payloadSchema.parse(levels[3]!.payload);
    const q = { type: 'inZone' as const, cat: 'ginger' as const, zone: 'kitchen' };
    expect(clueStatus(l, engine.initial(l), q)).toBe('unresolved');
    expect(clueStatus(l, { placements: { ginger: 5 } }, q)).toBe('satisfied');
    expect(clueStatus(l, { placements: { ginger: 6 } }, q)).toBe('violated');
    // Room membership is feedback; an object cell is the only blocked spot.
    const beside = {
      type: 'adjacentToFeature' as const,
      cat: 'ginger' as const,
      feature: 'window',
    };
    const placed = engine.apply(l, engine.initial(l), { type: 'placeCat', cat: 'ginger', cell: 0 });
    expect(placed.accepted).toBe(true);
    expect(clueStatus(l, placed.state, beside)).toBe('violated');
  });
  it('marks base-rule conflicts without treating clue violations as conflicts', () => {
    const l = payloadSchema.parse(levels[3]!.payload);
    const state = { placements: { ginger: 0, black: 7, white: 1, gray: null } };
    expect([...placementConflicts(l, state)]).toEqual(['ginger', 'white']);
    expect(placementConflicts(l, { placements: { ginger: 10, black: 7, white: 0, gray: 13 } }).size).toBe(0);
  });
  it('keeps object cells unavailable to cats', () => {
    const l = payloadSchema.parse(levels[1]!.payload);
    const blocked = engine.apply(l, engine.initial(l), { type: 'placeCat', cat: 'ginger', cell: 1 });
    expect(blocked.accepted).toBe(false);
    expect(blocked.reason).toContain('object');
  });
  it('rejects overlapping or empty rooms and colliding objects', () => {
    expect(
      payloadSchema.safeParse({
        ...p,
        zones: [...p.zones, { id: 'extra', label: 'extra', cells: [0] }],
      }).success,
    ).toBe(false);
    expect(
      payloadSchema.safeParse({ ...p, zones: [{ ...p.zones[0], cells: [] }, ...p.zones.slice(1)] })
        .success,
    ).toBe(false);
    expect(
      payloadSchema.safeParse({
        ...p,
        features: [...p.features, { id: 'bowl', label: 'food bowl', cell: 1 }],
      }).success,
    ).toBe(false);
    expect(
      payloadSchema.safeParse({
        ...p,
        features: p.features.slice(0, 2),
      }).success,
    ).toBe(false);
    expect(
      payloadSchema.safeParse({
        ...p,
        features: [{ ...p.features[0], cell: 2 }, { ...p.features[1], cell: 1 }, ...p.features.slice(2)],
      }).success,
    ).toBe(false);
  });
});
