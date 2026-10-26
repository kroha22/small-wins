import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import { puzzleCore, type MechanicId } from './puzzle-core';

type FixtureCase = {
  mechanicId: MechanicId;
  level: { identity: { contentVersion: number }; payload: unknown };
  invalidLevel: unknown;
  actions: unknown[];
  expectedStates: unknown[];
  solved: boolean;
  generationRequest: unknown;
  expectedGeneration: unknown;
};

const fixturePath = fileURLToPath(new URL('../../../../library/conformance/core-v1.json', import.meta.url));
const fixtures = JSON.parse(readFileSync(fixturePath, 'utf8')) as { cases: FixtureCase[] };

describe('Kotlin/JS public boundary conformance', () => {
  it.each(fixtures.cases.map((fixture) => [fixture.mechanicId, fixture] as const))(
    'runs the portable %s trace and session contract',
    (mechanicId, fixture) => {
      expect(puzzleCore.validateLevel<{ valid: boolean }>(mechanicId, fixture.level).valid).toBe(true);
      expect(puzzleCore.validateLevel<{ valid: boolean }>(mechanicId, fixture.invalidLevel).valid).toBe(false);

      let state = puzzleCore.initialState<unknown>(mechanicId, fixture.level);
      fixture.actions.forEach((action, index) => {
        const transition = puzzleCore.transition<{
          accepted: boolean;
          changed: boolean;
          state: unknown;
        }>(mechanicId, fixture.level, state, action);
        expect(transition.accepted).toBe(true);
        expect(transition.changed).toBe(true);
        state = transition.state;
        expect(state).toEqual(fixture.expectedStates[index]);
      });
      expect(puzzleCore.evaluate<{ solved: boolean }>(mechanicId, fixture.level, state).solved).toBe(
        fixture.solved,
      );
      expect(puzzleCore.generate(mechanicId, fixture.generationRequest)).toEqual(
        fixture.expectedGeneration,
      );

      const session = {
        attemptId: 'js-fixture-attempt',
        state,
        history: [puzzleCore.initialState<unknown>(mechanicId, fixture.level)],
        moveCount: fixture.actions.length,
        hintsUsed: 1,
        revision: fixture.actions.length,
      };
      const encoded = puzzleCore.encodeSession<unknown>(mechanicId, fixture.level, session);
      expect(
        puzzleCore.restoreSession<{ restored: boolean; session: typeof session }>(
          mechanicId,
          fixture.level,
          encoded,
          'fallback',
        ),
      ).toMatchObject({ restored: true, session });

      const changed = {
        ...fixture.level,
        identity: {
          ...fixture.level.identity,
          contentVersion: fixture.level.identity.contentVersion + 1,
        },
      };
      expect(
        puzzleCore.restoreSession<{ restored: boolean; reason: string }>(
          mechanicId,
          changed,
          encoded,
          'fallback',
        ),
      ).toMatchObject({ restored: false, reason: 'incompatible-level' });
    },
  );
});
