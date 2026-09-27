import { test, expect, type Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import witnesses from '../scripts/fixtures/witnesses.json' with { type: 'json' };
import graphLevels from '../src/games/untangle/levels.json' with { type: 'json' };
async function cell(page: Page, index: number) {
  const target = page.locator(`[data-cell="${index}"]`);
  if (test.info().project.name === 'mobile') await target.tap();
  else await target.click();
}
test('catalog, filters, settings focus and deep links', async ({ page }) => {
  await page.goto('/');
  await expect(
    page.getByRole('heading', { name: 'Small puzzles. Good little moments.' }),
  ).toBeVisible();
  await expect(page.getByRole('link', { name: /Let’s play/ })).toHaveCount(4);
  const settings = page.getByRole('button', { name: 'Settings' });
  await settings.click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.getByRole('button', { name: 'Close settings' }).click();
  await expect(settings).toBeFocused();
  await page.goto('/games/pipes?difficulty=hard');
  await expect(page.getByRole('link', { name: /Puzzle 05/ })).toBeVisible();
  await expect(page.getByRole('link', { name: /Puzzle 01/ })).toHaveCount(0);
  await page.goto('/games/unknown/levels/tutorial');
  await expect(page.getByRole('heading', { name: 'This puzzle wandered off.' })).toBeVisible();
});
test('Purrdoku keyboard, invalid moves, hints, solve, undo and reload', async ({ page }) => {
  await page.goto('/games/purrdoku/levels/tutorial');
  await expect(page.getByRole('heading', { name: 'Meet the cats' })).toBeVisible();
  await page.locator('[data-cell="0"]').focus();
  await page.keyboard.press('Enter');
  await page.getByRole('button', { name: 'Black cat, mark 2', exact: true }).click();
  await cell(page, 1);
  await expect(page.getByRole('status')).toContainText('Only one cat');
  await cell(page, 4);
  await page.getByRole('button', { name: 'Hint', exact: true }).click();
  await page.getByRole('button', { name: 'Hint', exact: true }).click();
  await expect(page.getByRole('region', { name: 'Hint' })).toContainText('Extra clue 2');
  await page.getByRole('button', { name: 'White cat, mark 3', exact: true }).click();
  await cell(page, 8);
  await expect(page.getByRole('region', { name: 'Puzzle complete' })).toBeVisible();
  await page.reload();
  await expect(page.getByRole('region', { name: 'Puzzle complete' })).toBeVisible();
  await page.getByRole('button', { name: 'Undo', exact: true }).click();
  await expect(page.getByRole('region', { name: 'Puzzle complete' })).toHaveCount(0);
  await page.getByRole('button', { name: 'Restart', exact: true }).click();
  await expect(page.locator('.moves')).toHaveText('0moves');
});
test('Pipes tutorial and persistent completion', async ({ page }) => {
  await page.goto('/games/pipes/levels/tutorial');
  for (const action of witnesses.pipes[0]!) await cell(page, action.cell);
  await expect(page.getByRole('region', { name: 'Puzzle complete' })).toBeVisible();
  await page.getByRole('link', { name: 'All Pipes puzzles' }).click();
  await expect(page.getByRole('link', { name: /A gentle start.*Complete/ })).toBeVisible();
  await expect(page.getByText('0 / 6 little wins')).toBeVisible();
});
test('Waypoints tapping, history, resume and solution', async ({ page }) => {
  await page.goto('/games/waypoints/levels/tutorial');
  const moves = witnesses.waypoints[0]!;
  await cell(page, moves[0]!.cell);
  await page.reload();
  await expect(page.locator('.moves')).toHaveText('1moves');
  await page.getByRole('button', { name: 'Undo', exact: true }).click();
  for (const action of moves) await cell(page, action.cell);
  await expect(page.getByRole('region', { name: 'Puzzle complete' })).toBeVisible();
});
test('Untangle click-place alternative and keyboard cancellation', async ({ page }) => {
  await page.goto('/games/untangle/levels/tutorial');
  const first = page.locator('[data-node="B"]');
  await first.focus();
  await page.keyboard.press('ArrowRight');
  await page.keyboard.press('Escape');
  await expect(page.locator('.moves')).toHaveText('0moves');
  for (const action of witnesses.untangle[0]!) {
    await page.locator(`[data-node="${action.id}"]`).click();
    const board = page.locator('.untangle-board');
    const box = (await board.boundingBox())!;
    await board.click({
      position: { x: (box.width * action.x) / 100, y: (box.height * action.y) / 100 },
    });
  }
  await expect(page.getByRole('region', { name: 'Puzzle complete' })).toBeVisible();
});
test('Untangle drag cancellation never commits a preview', async ({ page, isMobile }) => {
  test.skip(isMobile, 'Mouse cancellation is covered on desktop; click placement runs on mobile.');
  await page.goto('/games/untangle/levels/tutorial');
  const n = graphLevels[0]!.payload.nodes[1]!;
  const board = (await page.locator('.untangle-board').boundingBox())!;
  await page.mouse.move(board.x + (board.width * n.x) / 100, board.y + (board.height * n.y) / 100);
  await page.mouse.down();
  await page.mouse.move(board.x + board.width * 0.5, board.y + board.height * 0.5, { steps: 5 });
  await page.keyboard.press('Escape');
  await page.mouse.up();
  await expect(page.locator('.moves')).toHaveText('0moves');
});
test('home and four game boards have no automated accessibility violations or overflow', async ({
  page,
}) => {
  test.setTimeout(120000);
  for (const path of [
    '/',
    '/games/purrdoku/levels/tutorial',
    '/games/pipes/levels/tutorial',
    '/games/untangle/levels/tutorial',
    '/games/waypoints/levels/tutorial',
  ]) {
    await page.goto(path);
    await expect(page.locator('h1')).toBeVisible();
    if (path !== '/') await expect(page.locator('.board-panel')).toBeVisible();
    const results = await new AxeBuilder({ page }).analyze();
    expect(results.violations).toEqual([]);
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true);
  }
});
test('blocked storage preserves play and shows an honest warning', async ({ page }) => {
  await page.addInitScript(() => {
    Storage.prototype.setItem = () => {
      throw new DOMException('Denied', 'SecurityError');
    };
  });
  await page.goto('/games/pipes/levels/tutorial');
  await expect(page.getByText(/Progress cannot be saved on this device/)).toBeVisible();
  await cell(page, 0);
  await expect(page.locator('.moves')).toHaveText('1moves');
});
