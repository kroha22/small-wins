import { writeFileSync, mkdirSync } from 'node:fs';
import { format } from 'prettier';
import { houseLevel } from './purrdoku-house';
import { seededRandom, shuffled } from '../src/shared/lib/random';
import { neighbours, type Metadata } from '../src/shared/lib/engine';
import * as purr from '../src/games/purrdoku/model';
import * as pipes from '../src/games/pipes/model';
import * as untangle from '../src/games/untangle/model';
import manifest from '../src/content/manifest.json';
import * as waypoints from '../src/games/waypoints/model';

// Authoring only: versioned deterministic construction and replayable witnesses.
const seed = manifest.seed;
const meta = (i: number, title: string): Metadata => ({
  id: i === 0 ? 'tutorial' : `level-${i}`,
  title,
  difficulty: i === 0 ? 'tutorial' : i <= 2 ? 'easy' : i <= 4 ? 'medium' : 'hard',
  contentVersion: 1,
});
const pack: { [key: string]: unknown[] } = { purrdoku: [], pipes: [], untangle: [], waypoints: [] };
const witnesses: { [key: string]: unknown[][] } = {
  purrdoku: [],
  pipes: [],
  untangle: [],
  waypoints: [],
};
const names = {
  purrdoku: [
    'Meet the cats',
    'Sunny spots',
    'Quiet corners',
    'Window seat',
    'A little company',
    'House rules',
    'Perfect neighbours',
  ],
  pipes: [
    'First flow',
    'Around the bend',
    'Little loop',
    'Branching out',
    'Water garden',
    'Hidden current',
    'Everything connects',
  ],
  untangle: [
    'Loose ends',
    'A simple twist',
    'Crossed paths',
    'Pinned in place',
    'Room to move',
    'A closer look',
    'Clear thinking',
  ],
  waypoints: [
    'Take a little walk',
    'Scenic route',
    'Three small stops',
    'The long way',
    'A garden path',
    'Room for a detour',
    'Homeward bound',
  ],
};
for (let i = 0; i < 7; i++) {
  const random = seededRandom(`${seed}/${i}`);
  // Cat clues are predicates, selected against a full assignment, then checked
  // for uniqueness. The authored witness never enters player hints.
  const size = i < 3 ? 3 : 4;
  const rows = shuffled(
      Array.from({ length: size }, (_, n) => n),
      random,
    ),
    cols = shuffled(
      Array.from({ length: size }, (_, n) => n),
      random,
    ),
    cs = purr.cats.slice(0, size);
  const answer: purr.State = {
    placements: Object.fromEntries(cs.map((c, j) => [c.id, rows[j]! * size + cols[j]!])),
  };
  const p: purr.Payload = {
    size,
    cats: cs,
    zones:
      i >= 3
        ? [
            {
              id: 'rug',
              label: 'striped rug',
              cells: Array.from({ length: size * size }, (_, n) => n).filter((n) => n % size < 2),
            },
          ]
        : [],
    features: i >= 4 ? [{ id: 'window', label: 'window', cell: Math.floor(size / 2) }] : [],
    clues: [],
  };
  if (i === 0) {
    p.clues = [
      { type: 'inRow', cat: 'ginger', value: 0 },
      { type: 'inColumn', cat: 'black', value: 1 },
      { type: 'below', cat: 'white', other: 'black' },
      { type: 'rightOf', cat: 'white', other: 'black' },
    ];
  } else {
    const options: purr.Clue[] = [];
    for (const c of cs) {
      for (let value = 0; value < size; value++)
        options.push({ type: 'inRow', cat: c.id, value }, { type: 'inColumn', cat: c.id, value });
      for (const d of cs)
        if (c.id !== d.id)
          for (const type of ['leftOf', 'rightOf', 'above', 'below'] as const)
            options.push({ type, cat: c.id, other: d.id });
      for (const zone of p.zones)
        options.push(
          { type: 'inZone', cat: c.id, zone: zone.id },
          { type: 'notInZone', cat: c.id, zone: zone.id },
        );
      for (const feature of p.features)
        options.push(
          { type: 'adjacentToFeature', cat: c.id, feature: feature.id },
          { type: 'notAdjacentToFeature', cat: c.id, feature: feature.id },
        );
    }
    const valid = shuffled(
      options.filter((q) => purr.clueStatus(p, answer, q) === 'satisfied'),
      random,
    );
    // Hard grids prefer relational clues before direct coordinates.
    if (i >= 5) valid.sort((a, b) => Number('value' in a) - Number('value' in b));
    for (const q of valid) {
      p.clues.push(q);
      if (purr.solve(p).solutions.length === 1) break;
    }
    for (let j = p.clues.length - 1; j >= 0; j--) {
      const reduced = { ...p, clues: p.clues.filter((_, k) => k !== j) };
      if (purr.solve(reduced).solutions.length === 1) p.clues = reduced.clues;
    }
  }
  const solution = purr.solve(p).solutions[0]!;
  pack.purrdoku!.push({
    ...meta(i, names.purrdoku[i]!),
    contentVersion: 2,
    payload: houseLevel(i, solution, seed),
  });
  witnesses.purrdoku!.push(
    cs.map((c) => ({ type: 'placeCat', cat: c.id, cell: solution.placements[c.id] })),
  );
  // A spanning tree gives a complete connected pipe witness with no leaks.
  const n = [2, 3, 3, 4, 4, 5, 5][i]!;
  const ports: number[][] = Array.from({ length: n * n }, () => []),
    seen = new Set([0]),
    stack = [0];
  while (stack.length) {
    const cell = stack.at(-1)!;
    const options = shuffled(
      neighbours(cell, n, n).filter((c) => !seen.has(c)),
      random,
    );
    if (!options.length) {
      stack.pop();
      continue;
    }
    const next = options[0]!;
    const d = next === cell - n ? 0 : next === cell + 1 ? 1 : next === cell + n ? 2 : 3;
    ports[cell]!.push(d);
    ports[next]!.push((d + 2) % 4);
    seen.add(next);
    stack.push(next);
  }
  const pipe: pipes.Payload = {
    rows: n,
    cols: n,
    sourceCell: 0,
    ports,
    initialRotations: ports.map(() => Math.floor(random() * 4)),
  };
  if (i === 0) pipe.initialRotations = [3, 0, 0, 0];
  if (pipes.engine.solved(pipe, pipes.engine.initial(pipe)))
    pipe.initialRotations[0] = (pipe.initialRotations[0]! + 1) % 4;
  let ps = pipes.engine.initial(pipe);
  const pa: pipes.Action[] = [];
  for (let cell = 0; cell < n * n; cell++) {
    while (
      !pipes.engine.solved(pipe, ps) &&
      pipes.portsAt(pipe, ps, cell).sort().join() !== [...ports[cell]!].sort().join()
    ) {
      const a: pipes.Action = { type: 'rotate', cell, quarterTurns: 1 };
      const t = pipes.engine.apply(pipe, ps, a);
      if (!t.changed) break;
      pa.push(a);
      ps = t.state;
    }
  }
  pack.pipes!.push({ ...meta(i, names.pipes[i]!), payload: pipe });
  witnesses.pipes!.push(pa);
  // Scramble a planar circle by swapping node positions. A legal two-stage
  // move through a vacant centre gives a short, replayable solution.
  const count = [4, 5, 5, 6, 6, 7, 8][i]!;
  const positions = Array.from({ length: count }, (_, j) => ({
    id: String.fromCharCode(65 + j),
    x: Math.round((50 + 37 * Math.cos(-Math.PI / 2 + (j * 2 * Math.PI) / count)) * 10) / 10,
    y: Math.round((50 + 37 * Math.sin(-Math.PI / 2 + (j * 2 * Math.PI) / count)) * 10) / 10,
    fixed: i >= 3 && j === 0,
  }));
  const edges: [string, string][] = positions.map((v, j) => [v.id, positions[(j + 1) % count]!.id]);
  if (i >= 4) for (let j = 2; j < count - 1; j++) edges.push(['A', positions[j]!.id]);
  const pairs =
    i < 3
      ? [[1, 2]]
      : i === 3
        ? [[1, 3]]
        : i === 4
          ? [
              [1, 3],
              [2, 4],
            ]
          : i === 5
            ? [
                [1, 3],
                [2, 5],
              ]
            : [
                [1, 4],
                [2, 6],
                [3, 5],
              ];
  const scrambled = positions.map((n) => ({ ...n }));
  const moves: untangle.Action[] = [];
  for (const [first, second] of pairs) {
    const a = positions[first!]!,
      b = positions[second!]!;
    scrambled[first!] = { ...a, x: b.x, y: b.y };
    scrambled[second!] = { ...b, x: a.x, y: a.y };
    moves.push(
      { type: 'moveNode', id: a.id, x: 50, y: 50 },
      { type: 'moveNode', id: b.id, x: b.x, y: b.y },
      { type: 'moveNode', id: a.id, x: a.x, y: a.y },
    );
  }
  const graph: untangle.Payload = {
    nodes: scrambled,
    edges,
    bounds: { min: 8, max: 92 },
    minNodeDistance: 12,
  };
  let gs = untangle.engine.initial(graph);
  const replay: untangle.Action[] = [];
  for (const move of moves) {
    if (untangle.engine.solved(graph, gs)) break;
    const t = untangle.engine.apply(graph, gs, move);
    if (!t.accepted) throw new Error('Illegal graph witness');
    gs = t.state;
    replay.push(move);
  }
  pack.untangle!.push({ ...meta(i, names.untangle[i]!), payload: graph });
  witnesses.untangle!.push(replay);
  // Seeded self-avoiding walk: block only outside the witness.
  const wn = [3, 4, 4, 5, 5, 6, 6][i]!;
  let route: number[] = [];
  for (let retry = 0; retry < 300; retry++) {
    const path = [0];
    while (path.length < Math.ceil(wn * wn * 0.65)) {
      const options = shuffled(
        neighbours(path.at(-1)!, wn, wn).filter((c) => !path.includes(c)),
        random,
      );
      if (!options.length) break;
      path.push(options[0]!);
    }
    if (path.length >= Math.ceil(wn * wn * 0.6)) {
      route = path;
      break;
    }
  }
  if (!route.length) throw new Error('No route');
  const markerCount = i < 3 ? 2 : i < 5 ? 3 : 4;
  const markers = Array.from(
    { length: markerCount },
    (_, j) => route[Math.floor(((j + 1) * (route.length - 1)) / (markerCount + 1))]!,
  );
  const routePayload: waypoints.Payload = {
    rows: wn,
    cols: wn,
    start: route[0]!,
    target: route.at(-1)!,
    waypoints: markers,
    blocked: Array.from({ length: wn * wn }, (_, j) => j).filter(
      (c) => !route.includes(c) && random() < 0.65,
    ),
  };
  pack.waypoints!.push({ ...meta(i, names.waypoints[i]!), payload: routePayload });
  witnesses.waypoints!.push(route.slice(1).map((cell) => ({ type: 'addCell', cell })));
}
for (const game of Object.keys(pack))
  writeFileSync(
    new URL(`../src/games/${game}/levels.json`, import.meta.url),
    await format(JSON.stringify(pack[game], null, 2), { parser: 'json', printWidth: 100 }),
  );
mkdirSync(new URL('./fixtures/', import.meta.url), { recursive: true });
writeFileSync(
  new URL('./fixtures/witnesses.json', import.meta.url),
  await format(JSON.stringify(witnesses, null, 2), { parser: 'json', printWidth: 100 }),
);
console.log('Generated 28 levels with seed ' + seed);
