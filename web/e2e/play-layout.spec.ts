import { test, expect } from '@playwright/test';
import witnesses from '../scripts/fixtures/witnesses.json' with { type: 'json' };

test('completion shares the toolbar and next navigation works without scrolling down', async ({
  page,
  isMobile,
}) => {
  await page.goto('/games/pipes/levels/tutorial');
  await page.locator('[data-cell="0"]').click();
  const next = page.locator('.board-toolbar').getByRole('link', { name: 'Next puzzle' });
  await expect(next).toBeVisible();
  if (isMobile) {
    const box = (await next.boundingBox())!;
    expect(box.y).toBeGreaterThanOrEqual(0);
    expect(box.y + box.height).toBeLessThanOrEqual(page.viewportSize()!.height);
  }
  await next.click();
  await expect(page).toHaveURL(/pipes\/levels\/level-1$/);
  await expect(page.getByRole('button', { name: 'Hint', exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Next puzzle' })).toHaveCount(0);
  await page.getByRole('button', { name: 'Hint', exact: true }).click();
  const hint = page.locator('.game-actions').getByRole('region', { name: 'Hint', exact: true });
  await expect(hint).toBeVisible();
  if (isMobile) {
    const box = (await hint.boundingBox())!;
    expect(box.y).toBeGreaterThanOrEqual(0);
    expect(box.y + box.height).toBeLessThanOrEqual(page.viewportSize()!.height);
  }
});

test('Purrdoku keeps a browsable clue between the board and cats, with the full list before controls', async ({
  page,
  isMobile,
}) => {
  await page.goto('/games/purrdoku/levels/level-3');
  const focused = page.getByLabel('Clue beside the board');
  await expect(focused).toContainText('Clue 1 /');
  await focused.getByRole('button', { name: 'Next clue' }).click();
  await expect(focused).toContainText('Clue 2 /');
  await focused.getByRole('button', { name: 'Previous clue' }).click();
  await expect(focused).toContainText('Clue 1 /');
  await expect(
    page.locator('.board-panel').getByRole('complementary', { name: 'Cat clues' }),
  ).toBeVisible();
  expect(
    await page.evaluate(() => {
      const board = document.querySelector('.house-board')!;
      const clue = document.querySelector('.focus-clue')!;
      const cats = document.querySelector('.cat-tray')!;
      const list = document.querySelector('.clues-panel')!;
      const actions = document.querySelector('.game-actions')!;
      return (
        !!(board.compareDocumentPosition(clue) & Node.DOCUMENT_POSITION_FOLLOWING) &&
        !!(clue.compareDocumentPosition(cats) & Node.DOCUMENT_POSITION_FOLLOWING) &&
        !!(list.compareDocumentPosition(actions) & Node.DOCUMENT_POSITION_FOLLOWING)
      );
    }),
  ).toBe(true);
  if (isMobile) {
    await page.locator('.house-board').evaluate((el) => el.scrollIntoView({ block: 'start' }));
    const board = (await page.locator('.house-board').boundingBox())!;
    const clue = (await focused.boundingBox())!;
    const cats = (await page.locator('.cat-tray').boundingBox())!;
    const bar = (await page.locator('.game-actions').boundingBox())!;
    expect(clue.y - (board.y + board.height)).toBeLessThan(20);
    expect(cats.y + cats.height).toBeLessThan(bar.y);
  }
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: test.info().outputPath('purrdoku-layout.png'), fullPage: true });
});

test('Waypoints uses a rabbit and striped blocked cells ignore pointer input while retaining keyboard focus', async ({
  page,
  isMobile,
}) => {
  await page.goto('/games/waypoints/levels/tutorial');
  await expect(page.locator('[data-cell="0"] .route-start-icon')).toBeVisible();
  const carrot = page.locator('[data-cell="3"]');
  await expect(carrot).toHaveAccessibleName(/carrot, not collected/);
  await expect(carrot.locator('.carrot-icon')).toBeVisible();
  await expect(page.locator('.board-topline')).toContainText('0 / 2 collected');
  await carrot.click();
  await expect(carrot).toHaveAccessibleName(/carrot, collected/);
  await expect(carrot.locator('.carrot-check')).toBeVisible();
  await expect(page.locator('.board-topline')).toContainText('1 / 2 collected');
  await page.getByRole('button', { name: 'Undo', exact: true }).click();
  await expect(carrot).toHaveAccessibleName(/carrot, not collected/);
  await expect(carrot.locator('.carrot-check')).toHaveCount(0);
  await expect(page.locator('.board-topline')).toContainText('0 / 2 collected');
  await page.getByRole('button', { name: 'Restart', exact: true }).click();
  const blocked = page.locator('[data-cell="1"]');
  await expect(blocked.locator('svg')).toHaveCount(0);
  if (!isMobile) {
    await blocked.hover();
    await expect(blocked).toHaveCSS('outline-style', 'none');
    await expect(blocked).toHaveCSS('cursor', 'default');
  }
  const box = (await blocked.boundingBox())!;
  if (isMobile) await page.touchscreen.tap(box.x + box.width / 2, box.y + box.height / 2);
  else await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
  await expect(page.locator('.moves')).toHaveText('0moves');
  await page.locator('[data-cell="0"]').focus();
  await page.keyboard.press('ArrowRight');
  await expect(blocked).toBeFocused();
  await expect(blocked).not.toHaveCSS('outline-style', 'none');
  await page.keyboard.press('Enter');
  await expect(page.locator('.moves')).toHaveText('0moves');
  for (const action of witnesses.waypoints[0]!)
    await page.locator(`[data-cell="${action.cell}"]`).click();
  await expect(
    page.locator('.board-toolbar').getByRole('link', { name: 'Next puzzle' }),
  ).toBeVisible();
  await page.screenshot({ path: test.info().outputPath('waypoints-layout.png'), fullPage: true });
});
