import {
  cats,
  clueStatus,
  payloadSchema,
  solve,
  type Clue,
  type Payload,
  type State,
} from '../src/games/purrdoku/model';
import { seededRandom, shuffled } from '../src/shared/lib/random';

// A separate stream keeps the other three packs stable when house content changes.
export function houseLevel(index: number, answer: State, seed: string): Payload {
  const size = index < 3 ? 3 : 4;
  const p: Payload = {
    size,
    cats: cats.slice(0, size),
    zones:
      size === 3
        ? [
            { id: 'kitchen', label: 'kitchen', cells: index === 0 ? [0, 1, 2] : [0, 1, 3, 4] },
            { id: 'living-room', label: 'living room', cells: index === 0 ? [3, 4, 5] : [2, 5] },
            { id: 'bedroom', label: 'bedroom', cells: [6, 7, 8] },
          ]
        : [
            { id: 'kitchen', label: 'kitchen', cells: [0, 1, 4, 5] },
            { id: 'living-room', label: 'living room', cells: [2, 3, 6, 7] },
            { id: 'bedroom', label: 'bedroom', cells: [8, 9, 12, 13] },
            { id: 'study', label: 'study', cells: [10, 11, 14, 15] },
          ],
    features: [],
    clues: [],
  };
  const occupied = new Set(Object.values(answer.placements).filter((cell): cell is number => cell != null));
  const furniture: readonly (readonly [string, string, string])[] = size === 3
    ? [
        ['window', 'window', 'kitchen'],
        ['armchair', 'armchair', 'living-room'],
        ['bed', 'bed', 'bedroom'],
      ]
    : [
        ['bowl', 'food bowl', 'kitchen'],
        ['window', 'window', 'living-room'],
        ['armchair', 'armchair', 'bedroom'],
        ['plant', 'plant', 'study'],
      ];
  p.features = furniture.map(([id, label, zoneId]) => {
    const zone = p.zones.find((item) => item.id === zoneId)!;
    const cell = zone.cells.find((candidate) => !occupied.has(candidate)) ?? zone.cells[0]!;
    occupied.add(cell);
    return { id, label, cell };
  });
  if (index === 0) {
    p.clues = [
      { type: 'inZone', cat: 'ginger', zone: 'kitchen' },
      { type: 'inColumn', cat: 'black', value: 1 },
      { type: 'adjacentToFeature', cat: 'white', feature: 'armchair' },
    ];
    return payloadSchema.parse(p);
  }
  const options: Clue[] = [];
  for (const cat of p.cats) {
    for (const zone of p.zones)
      for (const type of ['inZone', 'notInZone'] as const)
        options.push({ type, cat: cat.id, zone: zone.id });
    for (const feature of p.features)
      for (const type of ['adjacentToFeature', 'notAdjacentToFeature'] as const)
        options.push({ type, cat: cat.id, feature: feature.id });
    for (const other of p.cats)
      if (cat.id !== other.id)
        for (const type of ['leftOf', 'rightOf', 'above', 'below'] as const)
          options.push({ type, cat: cat.id, other: other.id });
    for (let value = 0; value < size; value++)
      options.push({ type: 'inRow', cat: cat.id, value }, { type: 'inColumn', cat: cat.id, value });
  }
  const trueClues = options.filter((q) => clueStatus(p, answer, q) === 'satisfied');
  const unique = (clues: Clue[]) => {
    const result = solve({ ...p, clues });
    return !result.exceeded && result.solutions.length === 1;
  };
  const random = seededRandom(`${seed}/purrdoku-house/2/${index}`);
  for (let attempt = 0; attempt < 200; attempt++) {
    const candidates = shuffled(trueClues, random);
    if (index >= 5) candidates.sort((a, b) => Number('value' in a) - Number('value' in b));
    let clues: Clue[] = [];
    for (const q of candidates) {
      clues.push(q);
      if (unique(clues)) break;
    }
    for (let i = clues.length - 1; i >= 0; i--) {
      const reduced = clues.filter((_, j) => i !== j);
      if (unique(reduced)) clues = reduced;
    }
    // Rooms and objects must contribute to the solution, not decorate redundant clues.
    if (clues.some((q) => q.type === 'inZone') && clues.some((q) => q.type === 'adjacentToFeature'))
      return payloadSchema.parse({ ...p, clues });
  }
  throw new Error(`Could not author a unique house puzzle with room and object clues: ${index}`);
}
