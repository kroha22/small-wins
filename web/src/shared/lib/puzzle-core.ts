import * as generated from '../../generated/puzzle-core/small-wins-puzzle-core.mjs';

export type MechanicId = 'network' | 'purrdoku' | 'route' | 'shikaku';

const encode = (value: unknown) => JSON.stringify(value);
const decode = <T>(value: string): T => JSON.parse(value) as T;

export const puzzleCore = {
  catalog: <T>() => decode<T>(generated.mechanicCatalogJson()),
  generate: <T>(mechanicId: MechanicId, request: unknown) =>
    decode<T>(generated.generate(mechanicId, encode(request))),
  validateLevel: <T>(mechanicId: MechanicId, level: unknown) =>
    decode<T>(generated.validateLevel(mechanicId, encode(level))),
  initialState: <T>(mechanicId: MechanicId, level: unknown) =>
    decode<T>(generated.initialState(mechanicId, encode(level))),
  validateState: <T>(mechanicId: MechanicId, level: unknown, state: unknown) =>
    decode<T>(generated.validateState(mechanicId, encode(level), encode(state))),
  transition: <T>(mechanicId: MechanicId, level: unknown, state: unknown, action: unknown) =>
    decode<T>(generated.transition(mechanicId, encode(level), encode(state), encode(action))),
  evaluate: <T>(mechanicId: MechanicId, level: unknown, state: unknown) =>
    decode<T>(generated.evaluate(mechanicId, encode(level), encode(state))),
  inspect: <T>(mechanicId: MechanicId, level: unknown, state: unknown) =>
    decode<T>(generated.inspect(mechanicId, encode(level), encode(state))),
  solve: <T>(mechanicId: MechanicId, level: unknown) =>
    decode<T>(generated.solve(mechanicId, encode(level))),
  solveWithBudget: <T>(mechanicId: 'network', level: unknown, budget: number) =>
    decode<T>(generated.solveWithBudget(mechanicId, encode(level), budget)),
  solveDetails: <T>(
    mechanicId: 'purrdoku' | 'route' | 'shikaku',
    level: unknown,
    state: unknown | null,
    limit: number,
    budget: number,
  ) => decode<T>(generated.solveDetails(mechanicId, encode(level), state == null ? null : encode(state), limit, budget)),
  hint: <T>(mechanicId: MechanicId, level: unknown, state: unknown, used: number) =>
    decode<T>(generated.hint(mechanicId, encode(level), encode(state), used)),
  encodeSession: <T>(
    mechanicId: MechanicId,
    level: unknown,
    session: {
      attemptId: string;
      state: unknown;
      history: unknown[];
      moveCount: number;
      hintsUsed: number;
      revision: number;
    },
  ) =>
    decode<T>(
      generated.encodeSession(
        mechanicId,
        encode(level),
        session.attemptId,
        encode(session.state),
        encode(session.history),
        session.moveCount,
        session.hintsUsed,
        session.revision,
      ),
    ),
  restoreSession: <T>(
    mechanicId: MechanicId,
    level: unknown,
    saved: unknown,
    fallbackAttemptId: string,
  ) =>
    decode<T>(
      generated.restoreSession(
        mechanicId,
        encode(level),
        saved == null ? null : encode(saved),
        fallbackAttemptId,
      ),
    ),
};
