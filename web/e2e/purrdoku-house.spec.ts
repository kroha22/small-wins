import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import witnesses from '../scripts/fixtures/witnesses.json' with { type: 'json' };

test('house rooms and objects remain readable and object cells stay blocked', async ({
  page,
}, testInfo) => {
  await page.goto('/games/purrdoku/levels/level-3');
  await expect(page.getByLabel('Rooms and objects')).toContainText('kitchen');
  await expect(page.getByLabel('Rooms and objects')).toContainText('study');
  const window = page.locator('[data-cell="6"]');
  await window.focus();
  await page.keyboard.press('Enter');
  await expect(window).toHaveAccessibleName(/empty, window, living room/);
  await expect(window.locator('.house-feature')).toBeVisible();
  await expect(page.locator('.moves')).toHaveText('0moves');
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.locator('.house-legend').scrollIntoViewIfNeeded();
  await page.screenshot({ path: testInfo.outputPath('house.png'), fullPage: true });
  await page.getByRole('button', { name: 'Undo', exact: true }).click();
  await expect(window).toBeVisible();
  await page.getByRole('button', { name: 'How to play' }).click();
  await expect(page.getByRole('dialog')).toContainText('Keep each cat in a different room');
  await expect(page.getByRole('dialog')).toContainText('Room boundaries do not change adjacency');
});

test('old completion is retained but the new house puzzle needs a new completion', async ({
  page,
}) => {
  await page.addInitScript(() => {
    if (!localStorage.getItem('small-wins:v1:progress')) {
      localStorage.setItem(
        'small-wins:v1:progress',
        JSON.stringify({
          'purrdoku/level-1/1': { moves: 3, assisted: false },
          'pipes/level-1/1': { moves: 12, assisted: false },
        }),
      );
      localStorage.setItem(
        'small-wins:v1:active-attempt',
        JSON.stringify({
          schemaVersion: 1,
          gameId: 'purrdoku',
          levelId: 'level-1',
          contentVersion: 1,
          attemptId: 'old-content',
          state: { placements: { ginger: 8, black: 4, white: null } },
          history: [],
          moveCount: 2,
          hintsUsed: 0,
          revision: 2,
        }),
      );
    }
  });
  await page.goto('/games/purrdoku?difficulty=all');
  await expect(page.getByText('0 / 6 little wins')).toBeVisible();
  await expect(page.getByRole('link', { name: /Puzzle 01/ })).not.toContainText('Complete');
  await page.getByRole('link', { name: /Puzzle 01/ }).click();
  await expect(page.locator('.moves')).toHaveText('0moves');
  await expect(page.getByRole('status')).toContainText('fresh attempt');
  for (const action of witnesses.purrdoku[1]!) {
    const name = `${action.cat[0]!.toUpperCase()}${action.cat.slice(1)} cat`;
    await page.getByRole('button', { name: new RegExp(`^${name}, mark`) }).click();
    await page.locator(`[data-cell="${action.cell}"]`).click();
  }
  await expect(page.getByRole('region', { name: 'Puzzle complete' })).toBeVisible();
  await page.getByRole('link', { name: 'All Purrdoku puzzles' }).click();
  await expect(page.getByText('1 / 6 little wins')).toBeVisible();
  await expect(page.getByRole('link', { name: /Puzzle 01/ })).toContainText('Complete');
  const progress = await page.evaluate(() =>
    JSON.parse(localStorage.getItem('small-wins:v1:progress')!),
  );
  expect(progress).toHaveProperty('purrdoku/level-1/1');
  expect(progress).toHaveProperty('purrdoku/level-1/4');
  expect(progress).toHaveProperty('pipes/level-1/1');
});

test('cats can move between cells and be dragged off the board', async ({ page }) => {
  await page.goto('/games/purrdoku/levels/level-3');
  const source = page.locator('[data-cell="2"]');
  await source.focus();
  await page.keyboard.press('Enter');
  const target = page.locator('[data-cell="6"]');
  const from = (await source.boundingBox())!;
  const to = (await target.boundingBox())!;
  await page.mouse.move(from.x + from.width / 2, from.y + from.height / 2);
  await page.mouse.down();
  await page.mouse.move(to.x + to.width / 2, to.y + to.height / 2, { steps: 5 });
  await page.mouse.up();
  await expect(target).toHaveAccessibleName(/Ginger cat, mark 1/);
  const board = (await page.locator('.house-board').boundingBox())!;
  await page.mouse.move(to.x + to.width / 2, to.y + to.height / 2);
  await page.mouse.down();
  await page.mouse.move(board.x - 20, board.y + board.height / 2, { steps: 5 });
  await page.mouse.up();
  await expect(target).toHaveAccessibleName(/empty/);
});
