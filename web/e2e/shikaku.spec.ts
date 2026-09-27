import { test, expect } from '@playwright/test';

test('draws a rectangle by dragging between opposite corners', async ({ page }) => {
  await page.goto('/games/shikaku/levels/tutorial');
  const cells = page.locator('.shikaku-cell');
  const from = (await cells.nth(3).boundingBox())!;
  const to = (await cells.nth(6).boundingBox())!;
  await page.mouse.move(from.x + from.width / 2, from.y + from.height / 2);
  await page.mouse.down();
  await page.mouse.move(to.x + to.width / 2, to.y + to.height / 2, { steps: 4 });
  await page.mouse.up();
  await expect(page.locator('.moves')).toHaveText('1moves');
  await expect(page.locator('.shikaku-cell.region-0')).toHaveCount(2);
});

test('cancels a pending corner selection without adding a move', async ({ page }) => {
  await page.goto('/games/shikaku/levels/tutorial');
  const first = page.locator('.shikaku-cell').first();
  await first.click();
  await expect(first).toHaveClass(/corner-selected/);
  await page.getByRole('button', { name: 'Cancel selection', exact: true }).click();
  await expect(first).not.toHaveClass(/corner-selected/);
  await expect(page.locator('.moves')).toHaveText('0moves');
});
