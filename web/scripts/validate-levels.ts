import assert from 'node:assert/strict';
import manifest from '../src/content/manifest.json';
assert.equal(manifest.schemaVersion, 1);
assert.equal(manifest.rulesVersion, 1);
assert.equal(manifest.generatorVersion, 2);
import { z } from 'zod';
import { metadataSchema, type Engine, type Level } from '../src/shared/lib/engine';
import * as purr from '../src/games/purrdoku/model';
import * as pipes from '../src/games/pipes/model';
import * as untangle from '../src/games/untangle/model';
import * as waypoints from '../src/games/waypoints/model';
import * as shikaku from '../src/games/shikaku/model';
import purrLevels from '../src/games/purrdoku/levels.json';
import pipeLevels from '../src/games/pipes/levels.json';
import untangleLevels from '../src/games/untangle/levels.json';
import waypointLevels from '../src/games/waypoints/levels.json';
import shikakuLevels from '../src/games/shikaku/levels.json';
import wildlifeLevels from '../src/games/wildlife-survey/levels.json';
import * as wildlife from '../src/games/wildlife-survey/model';
import witnesses from './fixtures/witnesses.json';
import { catalog } from '../src/content/catalog';

function validate<P, S, A>(
  name: string,
  raw: unknown,
  schema: z.ZodType<P>,
  engine: Engine<P, S, A>,
  actions: A[][],
) {
  const levels = z.array(metadataSchema.extend({ payload: schema })).parse(raw) as Level<P>[];
  assert.equal(levels.length, 7, name);
  assert.equal(new Set(levels.map((l) => l.id)).size, 7);
  assert.deepEqual(
    levels.map((l) => l.id),
    ['tutorial', ...Array.from({ length: 6 }, (_, i) => `level-${i + 1}`)],
  );
  assert.deepEqual(
    levels.map((l) => l.difficulty),
    ['tutorial', 'easy', 'easy', 'medium', 'medium', 'hard', 'hard'],
  );
  for (const [i, level] of levels.entries()) {
    assert.equal(level.contentVersion, catalog.find((game) => game.id === name)!.contentVersion);
    let state = engine.initial(level.payload);
    assert.ok(engine.validState(level.payload, state), `${name}/${level.id} initial`);
    assert.ok(!engine.solved(level.payload, state), `${name}/${level.id} begins unsolved`);
    for (const action of actions[i]!) {
      assert.ok(!engine.solved(level.payload, state), 'Witness continues after solved');
      const before = JSON.stringify(state);
      const t = engine.apply(level.payload, state, action);
      assert.ok(
        t.accepted && t.changed,
        `${name}/${level.id}: ${JSON.stringify(action)} — ${t.reason}`,
      );
      assert.equal(JSON.stringify(state), before, 'Engine mutated its input');
      assert.ok(engine.validState(level.payload, t.state));
      state = t.state;
    }
    assert.ok(engine.solved(level.payload, state), `${name}/${level.id} witness must solve`);
  }
  return levels;
}
const purrPack = validate(
  'purrdoku',
  purrLevels,
  purr.payloadSchema,
  purr.engine,
  witnesses.purrdoku as purr.Action[][],
);
for (const l of purrPack) {
  const result = purr.solve(l.payload);
  assert.equal(result.exceeded, false);
  assert.equal(result.solutions.length, 1, `${l.id} unique`);
  assert.ok(
    l.payload.clues.some((q) => q.type === 'inZone'),
    `${l.id} has a room clue`,
  );
  assert.ok(
    l.payload.clues.some((q) => q.type === 'adjacentToFeature'),
    `${l.id} has an object clue`,
  );
  assert.equal(new Set(l.payload.zones.flatMap((room) => room.cells)).size, l.payload.size ** 2);
  for (let i = 0; i < l.payload.clues.length; i++) {
    const reduced = purr.solve({ ...l.payload, clues: l.payload.clues.filter((_, j) => i !== j) });
    assert.equal(reduced.exceeded, false);
    // A clue may be logically implied by the structural one-cat-per-room rule.
    assert.ok(reduced.solutions.length >= 1 && reduced.solutions.length <= 2, `${l.id} clue ${i} leaves an invalid solution count`);
  }
}
validate(
  'pipes',
  pipeLevels,
  pipes.payloadSchema,
  pipes.engine,
  witnesses.pipes as pipes.Action[][],
);
validate(
  'untangle',
  untangleLevels,
  untangle.payloadSchema,
  untangle.engine,
  witnesses.untangle as untangle.Action[][],
);
validate(
  'waypoints',
  waypointLevels,
  waypoints.payloadSchema,
  waypoints.engine,
  witnesses.waypoints as waypoints.Action[][],
);
validate(
  'shikaku',
  shikakuLevels,
  shikaku.payloadSchema,
  shikaku.engine,
  witnesses.shikaku as shikaku.Action[][],
);
for (const level of z.array(metadataSchema.extend({ payload: shikaku.payloadSchema })).parse(shikakuLevels)) {
  assert.equal(shikaku.solutions(level.payload, 2).length, 1, `${level.id} unique`);
}
const wildlifePack = z.array(metadataSchema.extend({ payload: wildlife.payloadSchema })).parse(wildlifeLevels);
assert.equal(wildlifePack.length, 7);
for (const level of wildlifePack) {
  let state = wildlife.engine.initial(level.payload);
  for (let cell = 0; cell < level.payload.rows * level.payload.cols; cell++) {
    state = wildlife.engine.apply(level.payload, state, { type: 'mark', cell, mark: level.payload.occupied.includes(cell) ? 'found' : 'empty' }).state;
  }
  assert.ok(wildlife.engine.solved(level.payload, state), `${level.id} wildlife witness must solve`);
}
console.log(
  '42 level schemas, initial states and legal solution replays passed. All 7 Purrdoku and 7 Shikaku puzzles have exactly one solution.',
);
