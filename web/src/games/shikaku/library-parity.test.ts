import { describe, expect, it } from 'vitest';
import rawLevels from './levels.json';
import * as current from './model';
import * as legacy from './legacy-model';

const levels = rawLevels.map((level) => current.payloadSchema.parse(level.payload));

describe('Kotlin rectangle-partition core parity', () => {
  it.each(levels.map((level, index) => [rawLevels[index]!.id, level] as const))(
    'matches the legacy solver and solution trace for %s',
    (_, level) => {
      expect(current.solutions(level)).toEqual(legacy.solutions(level));
      const solution = legacy.solutions(level, 1)[0]!;
      let currentState = current.engine.initial(level);
      let legacyState = legacy.engine.initial(level);
      level.clues.forEach((_, clue) => {
        const cells = current.regionCells(solution, clue);
        const rows = cells.map((cell) => Math.floor(cell / level.cols));
        const columns = cells.map((cell) => cell % level.cols);
        const action = {
          type: 'place' as const,
          clue,
          top: Math.min(...rows),
          left: Math.min(...columns),
          bottom: Math.max(...rows),
          right: Math.max(...columns),
        };
        const currentTransition = current.engine.apply(level, currentState, action);
        const legacyTransition = legacy.engine.apply(level, legacyState, action);
        expect(currentTransition).toMatchObject({
          accepted: legacyTransition.accepted,
          changed: legacyTransition.changed,
          state: legacyTransition.state,
        });
        currentState = currentTransition.state;
        legacyState = legacyTransition.state;
      });
      expect(current.engine.solved(level, currentState)).toBe(true);
      expect(current.engine.solved(level, currentState)).toBe(legacy.engine.solved(level, legacyState));
    },
  );
});
