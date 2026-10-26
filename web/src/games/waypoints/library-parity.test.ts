import { describe, expect, it } from 'vitest';
import rawLevels from './levels.json';
import * as current from './model';
import * as legacy from './legacy-model';

const levels = rawLevels.map((level) => current.payloadSchema.parse(level.payload));

describe('Kotlin graph Route core parity', () => {
  it.each(levels.map((level, index) => [rawLevels[index]!.id, level] as const))(
    'matches the legacy completion and action trace for %s',
    (_, level) => {
      const currentInitial = current.engine.initial(level);
      const legacyInitial = legacy.engine.initial(level);
      const currentAnswer = current.routeCompletion(level, currentInitial);
      const legacyAnswer = legacy.routeCompletion(level, legacyInitial);
      expect(currentAnswer.exceeded).toBe(legacyAnswer.exceeded);
      expect(currentAnswer.path).not.toBeNull();
      expect(legacyAnswer.path).not.toBeNull();

      let ownState = currentInitial;
      for (const cell of currentAnswer.path!.slice(1))
        ownState = current.engine.apply(level, ownState, { type: 'addCell', cell }).state;
      expect(current.engine.solved(level, ownState)).toBe(true);

      let currentState = currentInitial;
      let legacyState = legacyInitial;
      for (const cell of legacyAnswer.path!.slice(1)) {
        const action = { type: 'addCell' as const, cell };
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
      for (const used of [0, 1, 2])
        expect(current.engine.hint!(level, currentInitial, used)).toBe(
          legacy.routeHint(level, legacyInitial, used),
        );
    },
  );
});
