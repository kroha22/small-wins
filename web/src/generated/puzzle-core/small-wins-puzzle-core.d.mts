type Nullable<T> = T | null | undefined
declare function KtSingleton<T>(): T & (abstract new() => any);
export declare function mechanicCatalogJson(): string;
export declare function generate(mechanicId: string, requestJson: string): string;
export declare function validateLevel(mechanicId: string, levelJson: string): string;
export declare function initialState(mechanicId: string, levelJson: string): string;
export declare function validateState(mechanicId: string, levelJson: string, stateJson: string): string;
export declare function transition(mechanicId: string, levelJson: string, stateJson: string, actionJson: string): string;
export declare function evaluate(mechanicId: string, levelJson: string, stateJson: string): string;
export declare function inspect(mechanicId: string, levelJson: string, stateJson: string): string;
export declare function solve(mechanicId: string, levelJson: string): string;
export declare function solveWithBudget(mechanicId: string, levelJson: string, budget: number): string;
export declare function hint(mechanicId: string, levelJson: string, stateJson: string, used: number): string;
export declare function freshSession(mechanicId: string, levelJson: string, attemptId: string): string;
export declare function encodeSession(mechanicId: string, levelJson: string, attemptId: string, stateJson: string, historyJson: string, moveCount: number, hintsUsed: number, revision: number): string;
export declare function restoreSession(mechanicId: string, levelJson: string, sessionJson: Nullable<string>, fallbackAttemptId: string): string;