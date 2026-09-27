import { describe, expect, it } from 'vitest';
import * as cats from './purrdoku/model';
import * as pipes from './pipes/model';
import * as route from './waypoints/model';
import { engine as untangle } from './untangle/model';
import catPack from './purrdoku/levels.json';
import pipePack from './pipes/levels.json';
import routePack from './waypoints/levels.json';

describe('Progressive hints', () => {
  it('adds two landmark clues before revealing a distinct exact spot for every unplaced cat', () => {
    for (const level of catPack) {
      const p = cats.payloadSchema.parse(level.payload),
        s = cats.engine.initial(p);
      const answer = cats.solve(p).solutions[0]!;
      const room = p.zones.find((z) => z.cells.includes(answer.placements.ginger!))!;
      expect(cats.solverHint(p, s, 0).text).toContain(room.label);
      expect(cats.solverHint(p, s, 0).text).not.toContain('column');
      expect(cats.solverHint(p, s, 1).text).toContain(
        `column ${(answer.placements.ginger! % p.size) + 1}`,
      );
      for (const stage of [2, 3]) {
        const text = cats.solverHint(p, s, stage).text!;
        expect(text).toContain('ginger cat');
        expect(text).not.toMatch(/row \d|column \d|Exact spot/);
      }
      for (let i = 0; i < p.cats.length; i++) {
        const cat = p.cats[i]!,
          cell = answer.placements[cat.id]!;
        expect(cats.solverHint(p, s, i + 4).text).toContain(
          `${cat.id} cat at row ${Math.floor(cell / p.size) + 1}, column ${(cell % p.size) + 1}`,
        );
      }
      expect(s).toEqual(cats.engine.initial(p));
    }
  });
  it('does not endorse wrong placements and identifies a blocking cat before exact placement', () => {
    const p = cats.payloadSchema.parse(catPack[0]!.payload);
    const s = { placements: { ginger: null, black: 1, white: null } };
    expect(cats.solverHint(p, s, 0).text).toContain('kitchen');
    expect(cats.solverHint(p, s, 4).text).toContain('First remove the black cat');
    expect(cats.solverHint({ ...p, clues: [] }, cats.engine.initial(p)).status).toBe('unavailable');
  });
  it('finds a legal full pipe arrangement for every pack level and narrows hints before giving a turn', () => {
    for (const level of pipePack) {
      const p = pipes.payloadSchema.parse(level.payload),
        s = pipes.engine.initial(p);
      const answer = pipes.pipeSolution(p)!;
      expect(answer).not.toBeNull();
      expect(pipes.engine.solved(p, answer)).toBe(true);
      expect(pipes.engine.hint!(p, s, 0)).toContain('Look in row');
      expect(pipes.engine.hint!(p, s, 1)).toContain('Focus on the pipe');
      // Execute only the described turn; repeated exact hints must reach a valid network.
      let state = s;
      for (let step = 0; step < p.ports.length && !pipes.engine.solved(p, state); step++) {
        const text = pipes.engine.hint!(p, state, 2)!;
        const match =
          /row (\d+), column (\d+) (clockwise|counterclockwise) (once|twice|three times)/.exec(
            text,
          )!;
        expect(match).not.toBeNull();
        const cell = (Number(match[1]) - 1) * p.cols + Number(match[2]) - 1;
        for (let i = 0; i < (match[4] === 'three times' ? 3 : match[4] === 'twice' ? 2 : 1); i++)
          state = pipes.engine.apply(p, state, {
            type: 'rotate',
            cell,
            quarterTurns: match[3] === 'clockwise' ? 1 : -1,
          }).state;
      }
      expect(pipes.engine.solved(p, state)).toBe(true);
      expect(s).toEqual(pipes.engine.initial(p));
    }
    expect(pipes.pipeSolution(pipes.payloadSchema.parse(pipePack[0]!.payload), 0)).toBeNull();
  });
  it('can finish every route using only the specific next-step hints', () => {
    for (const level of routePack) {
      const p = route.payloadSchema.parse(level.payload);
      let s = route.engine.initial(p);
      expect(route.engine.hint!(p, s, 0)).toContain('Look toward row');
      expect(route.engine.hint!(p, s, 1)).toContain('Aim for');
      for (let i = 0; i < p.rows * p.cols && !route.engine.solved(p, s); i++) {
        const match = /Next step: add row (\d+), column (\d+)/.exec(route.engine.hint!(p, s, 2)!)!;
        expect(match).not.toBeNull();
        const t = route.engine.apply(p, s, {
          type: 'addCell',
          cell: (Number(match[1]) - 1) * p.cols + Number(match[2]) - 1,
        });
        expect(t.accepted).toBe(true);
        s = t.state;
      }
      expect(route.engine.solved(p, s)).toBe(true);
    }
  });
  it('recognises a cut-off finish even when a next cell is open, and distinguishes budget exhaustion', () => {
    const p = route.payloadSchema.parse(routePack[1]!.payload);
    const s = { path: [0, 4, 8, 9, 5, 6, 2, 3] };
    expect(route.engine.validState(p, s)).toBe(true);
    expect(route.engine.hint!(p, s, 1)).toContain('Undo the last step');
    const initial = route.engine.initial(p);
    expect(route.routeCompletion(p, initial, 0)).toMatchObject({ path: null, exceeded: true });
    expect(initial.path).toEqual([p.start]);
  });
  it('does not offer a placeholder hint for Untangle', () => expect(untangle.hint).toBeUndefined());
});
