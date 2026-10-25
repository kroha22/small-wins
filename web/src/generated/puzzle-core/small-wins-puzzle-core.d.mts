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