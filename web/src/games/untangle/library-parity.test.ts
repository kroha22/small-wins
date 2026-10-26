import { describe, expect, it } from 'vitest';
import rawLevels from './levels.json';
import * as current from './model';
import * as legacy from './legacy-model';

const levels = rawLevels.map((level) => current.payloadSchema.parse(level.payload));

describe('Kotlin Untangle core parity', () => {
  it.each(levels.map((level, index) => [rawLevels[index]!.id, level] as const))(
    'matches legacy geometry and transition outcomes for %s',
    (_, level) => {
      const currentState = current.engine.initial(level);
      const legacyState = legacy.engine.initial(level);
      expect(currentState).toEqual(legacyState);
      expect(current.conflicts(level, currentState)).toEqual(legacy.conflicts(level, legacyState));
      expect(current.engine.solved(level, currentState)).toBe(legacy.engine.solved(level, legacyState));

      const movable = level.nodes.find((node) => !node.fixed)!;
      const noOp = { type: 'moveNode' as const, id: movable.id, x: movable.x, y: movable.y };
      expect(current.engine.apply(level, currentState, noOp)).toMatchObject(
        legacy.engine.apply(level, legacyState, noOp),
      );
      const outside = { type: 'moveNode' as const, id: movable.id, x: level.bounds.max + 1, y: movable.y };
      expect(current.engine.apply(level, currentState, outside).accepted).toBe(
        legacy.engine.apply(level, legacyState, outside).accepted,
      );
    },
  );
});
