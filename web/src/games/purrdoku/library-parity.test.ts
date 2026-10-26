import { describe, expect, it } from 'vitest';
import rawLevels from './levels.json';
import * as current from './model';
import * as legacy from './legacy-model';

const levels = rawLevels.map((level) => current.payloadSchema.parse(level.payload));

describe('Kotlin entity-placement core parity', () => {
  it.each(levels.map((level, index) => [rawLevels[index]!.id, level] as const))(
    'matches legacy solutions, feedback and transitions for %s',
    (_, level) => {
      expect(current.solve(level).solutions).toEqual(legacy.solve(level).solutions);
      let currentState = current.engine.initial(level);
      let legacyState = legacy.engine.initial(level);
      const solution = current.solve(level, currentState, 1).solutions[0]!;
      for (const cat of level.cats) {
        const action = { type: 'placeCat' as const, cat: cat.id, cell: solution.placements[cat.id]! };
        const currentTransition = current.engine.apply(level, currentState, action);
        const legacyTransition = legacy.engine.apply(level, legacyState, action);
        expect(currentTransition).toMatchObject({
          accepted: legacyTransition.accepted,
          changed: legacyTransition.changed,
          state: legacyTransition.state,
        });
        currentState = currentTransition.state;
        legacyState = legacyTransition.state;
        expect(level.clues.map((clue) => current.clueStatus(level, currentState, clue))).toEqual(
          level.clues.map((clue) => legacy.clueStatus(level, legacyState, clue)),
        );
        expect([...current.placementConflicts(level, currentState)]).toEqual([
          ...legacy.placementConflicts(level, legacyState),
        ]);
      }
      expect(current.engine.solved(level, currentState)).toBe(legacy.engine.solved(level, legacyState));
    },
  );
});
