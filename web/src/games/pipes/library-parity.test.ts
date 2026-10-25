import { describe, expect, it } from 'vitest';
import rawLevels from './levels.json';
import * as current from './model';
import * as legacy from './legacy-model';

const levels = rawLevels.map((level) => current.payloadSchema.parse(level.payload));

describe('Kotlin Network core parity', () => {
  it.each(levels.map((level, index) => [rawLevels[index]!.id, level] as const))(
    'matches legacy transitions and inspection for %s',
    (_, level) => {
      let currentState = current.engine.initial(level);
      let legacyState = legacy.engine.initial(level);
      expect(currentState).toEqual(legacyState);

      const actions = level.ports.flatMap((__, cell) => [
        { type: 'rotate' as const, cell, quarterTurns: 1 as const },
        { type: 'rotate' as const, cell, quarterTurns: -1 as const },
      ]);
      for (const action of actions) {
        const currentTransition = current.engine.apply(level, currentState, action);
        const legacyTransition = legacy.engine.apply(level, legacyState, action);
        expect(currentTransition).toMatchObject({
          accepted: legacyTransition.accepted,
          changed: legacyTransition.changed,
          state: legacyTransition.state,
        });
        currentState = currentTransition.state;
        legacyState = legacyTransition.state;
        expect(current.engine.solved(level, currentState)).toBe(
          legacy.engine.solved(level, legacyState),
        );
      }

      const currentConnections = current.connections(level, currentState);
      const legacyConnections = legacy.connections(level, legacyState);
      expect([...currentConnections.reached]).toEqual([...legacyConnections.reached]);
      expect(currentConnections.leaks).toEqual(legacyConnections.leaks);
    },
  );

  it.each(levels.map((level, index) => [rawLevels[index]!.id, level] as const))(
    'finds a valid solution for %s',
    (_, level) => {
      const solution = current.pipeSolution(level);
      expect(solution).not.toBeNull();
      expect(current.engine.solved(level, solution!)).toBe(true);
    },
  );
});
