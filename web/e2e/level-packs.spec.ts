import { test, expect } from '@playwright/test';
import witnesses from '../scripts/fixtures/witnesses.json' with { type: 'json' };
const games = ['purrdoku', 'pipes', 'untangle', 'waypoints'] as const;
for (const game of games)
  for (let index = 0; index < 7; index++) {
    const levelId = index === 0 ? 'tutorial' : `level-${index}`;
    test(`${game}/${levelId} is playable to completion`, async ({ page }) => {
      await page.goto(`/games/${game}/levels/${levelId}`);
      await expect(page.locator('.board-panel')).toBeVisible();
      if (game === 'purrdoku')
        for (const action of witnesses.purrdoku[index]!) {
          await page
            .getByRole('button', { name: new RegExp(`^${action.cat} cat, mark`, 'i') })
            .click();
          await page.locator(`[data-cell="${action.cell}"]`).click();
        }
      if (game === 'pipes')
        for (const action of witnesses.pipes[index]!)
          await page.locator(`[data-cell="${action.cell}"]`).click();
      if (game === 'waypoints')
        for (const action of witnesses.waypoints[index]!)
          await page.locator(`[data-cell="${action.cell}"]`).click();
      if (game === 'untangle')
        for (const action of witnesses.untangle[index]!) {
          if (await page.getByRole('region', { name: 'Puzzle complete' }).isVisible()) break;
          await page.locator(`[data-node="${action.id}"]`).click();
          const board = page.locator('.untangle-board');
          const box = (await board.boundingBox())!;
          await board.click({
            position: { x: (box.width * action.x) / 100, y: (box.height * action.y) / 100 },
          });
        }
      await expect(page.getByRole('region', { name: 'Puzzle complete' })).toBeVisible();
      const next = page
        .locator('.board-toolbar')
        .getByRole('link', { name: index === 6 ? 'More puzzles' : 'Next puzzle', exact: true });
      await expect(next).toBeVisible();
      await expect(next).toHaveAttribute(
        'href',
        index === 6 ? '/' : `/games/${game}/levels/level-${index + 1}`,
      );
      await expect(page.getByRole('button', { name: 'Hint', exact: true })).toHaveCount(0);
    });
  }
