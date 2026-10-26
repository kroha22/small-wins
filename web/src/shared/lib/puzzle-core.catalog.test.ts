import { describe, expect, it } from 'vitest';
import { puzzleCore } from './puzzle-core';

type CatalogEntry = {
  mechanicId: string;
  stage: string;
  capabilities: string[];
};

describe('portable puzzle core catalog', () => {
  it('exposes every implemented public and private-web mechanic', () => {
    const entries = puzzleCore.catalog<CatalogEntry[]>();
    const implemented = entries
      .filter((entry) => entry.stage === 'pilot')
      .map((entry) => entry.mechanicId)
      .sort();

    expect(implemented).toEqual([
      'color-neighbors',
      'habitat-search',
      'network',
      'packing',
      'purrdoku',
      'route',
      'shikaku',
      'sokoban',
      'untangle',
    ]);
    expect(entries.find((entry) => entry.mechanicId === 'packing')?.capabilities)
      .toContain('solution-witness');
    expect(entries.find((entry) => entry.mechanicId === 'sokoban')?.capabilities)
      .toContain('solver');
  });
});
