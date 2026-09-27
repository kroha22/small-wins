import { test, expect } from '@playwright/test';

test('Purrdoku adds four clues before exact spots, keeps the board unchanged and resumes the hint stage', async ({
  page,
}) => {
  await page.goto('/games/purrdoku/levels/tutorial');
  const hint = page.getByRole('button', { name: 'Hint', exact: true });
  const text = page.getByRole('region', { name: 'Hint', exact: true });
  await hint.click();
  await expect(text).toContainText('Extra clue 1');
  await expect(text).toContainText('kitchen');
  await hint.click();
  await expect(text).toContainText('Extra clue 2');
  await expect(text).toContainText('column 1');
  await hint.click();
  await expect(text).toContainText('Closer clue');
  await expect(text).toContainText('to the left of the window');
  await hint.click();
  await expect(text).toContainText('One more clue');
  await expect(text).toContainText('leftmost square');
  await expect(text).not.toContainText('Exact spot');
  await hint.click();
  await expect(text).toContainText('ginger cat at row 1, column 1');
  await expect(page.locator('.moves')).toHaveText('0moves');
  await expect(page.locator('[data-cell="0"]')).toHaveAccessibleName(/empty/);
  await page.reload();
  await hint.click();
  await expect(text).toContainText('black cat at row 2, column 2');
  await page.getByRole('button', { name: 'Restart', exact: true }).click();
  await hint.click();
  await expect(text).toContainText('Extra clue 1');
});

test('Pipes and Waypoints narrow the search before giving a concrete action', async ({ page }) => {
  for (const [game, stages] of [
    ['pipes', ['Look in row', 'Focus on the pipe', 'One working arrangement: turn']],
    ['waypoints', ['Look toward row', 'Aim for', 'Next step: add']],
  ] as const) {
    await page.goto(`/games/${game}/levels/tutorial`);
    for (const stage of stages) {
      await page.getByRole('button', { name: 'Hint', exact: true }).click();
      await expect(page.getByRole('region', { name: 'Hint', exact: true })).toContainText(stage);
    }
    await expect(page.locator('.moves')).toHaveText('0moves');
  }
});

test('Untangle keeps rules and recovery controls without a placeholder hint button', async ({
  page,
}) => {
  await page.goto('/games/untangle/levels/tutorial');
  await expect(page.getByRole('button', { name: 'Hint', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Restart', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'How to play' }).click();
  await expect(page.getByRole('dialog')).toContainText('Undo and restart');
});
