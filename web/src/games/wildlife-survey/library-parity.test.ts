import { describe, expect, it } from 'vitest';
import rawLevels from './levels.json';
import * as current from './model';
import * as legacy from './legacy-model';

const levels = rawLevels.map((level) => current.payloadSchema.parse(level.payload));

describe('Kotlin Habitat Search core parity', () => {
  it.each(levels.map((level, index) => [rawLevels[index]!.id, level] as const))(
    'matches the legacy observation trace and hints for %s',
    (_, level) => {
      let currentState = current.engine.initial(level);
      let legacyState = legacy.engine.initial(level);
      for (let cell = 0; cell < level.rows * level.cols; cell += 1) {
        const action = {
          type: 'mark' as const,
          cell,
          mark: level.occupied.includes(cell) ? ('found' as const) : ('empty' as const),
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
      }
      expect(current.engine.solved(level, currentState)).toBe(legacy.engine.solved(level, legacyState));
      const initial = current.engine.initial(level);
      for (const used of [0, 1])
        expect(current.engine.hint!(level, initial, used)).toBe(legacy.engine.hint!(level, initial, used));
    },
  );
});
