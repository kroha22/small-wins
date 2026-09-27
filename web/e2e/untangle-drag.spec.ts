import { test, expect, type Page } from '@playwright/test';
import { readFile } from 'node:fs/promises';

async function geometry(page: Page) {
  await page.locator('.untangle-board').scrollIntoViewIfNeeded();
  return page.locator('.untangle-board').evaluate((el) => {
    const r = el.getBoundingClientRect();
    return {
      x: r.left + el.clientLeft,
      y: r.top + el.clientTop,
      width: el.clientWidth,
      height: el.clientHeight,
    };
  });
}

test('a secondary pointer cannot steal a drag and release coordinates commit without a move event', async ({
  page,
  isMobile,
}) => {
  test.skip(isMobile, 'Controlled pointer lifecycle uses an active native mouse gesture.');
  await page.goto('/games/untangle/levels/level-4');
  const board = await geometry(page);
  const node = page.locator('[data-node="B"]');
  const box = (await node.boundingBox())!;
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  await page.mouse.down();
  await page.locator('[data-node="C"]').dispatchEvent('pointerdown', {
    pointerId: 99,
    pointerType: 'touch',
    isPrimary: false,
    button: 0,
  });
  await node.dispatchEvent('pointerup', {
    pointerId: 99,
    pointerType: 'touch',
    isPrimary: false,
    clientX: board.x,
    clientY: board.y,
  });
  await expect(page.locator('.moves')).toHaveText('0moves');
  await node.dispatchEvent('pointerup', {
    pointerId: 1,
    pointerType: 'mouse',
    isPrimary: true,
    clientX: board.x + board.width * 0.5,
    clientY: board.y + board.height * 0.5,
  });
  await page.mouse.up();
  await expect(page.locator('.moves')).toHaveText('1moves');
  await expect(node).toHaveAccessibleName('Node B, x 50, y 50');
});

test('off-centre dragging preserves the grab offset, commits once and survives reload', async ({
  page,
  isMobile,
}) => {
  test.skip(
    isMobile,
    'Native mouse dragging runs in Chromium and WebKit; taps have separate mobile coverage.',
  );
  await page.goto('/games/untangle/levels/level-4');
  const board = await geometry(page);
  const node = page.locator('[data-node="B"]');
  const box = (await node.boundingBox())!;
  await page.mouse.move(box.x + box.width / 2 + 12, box.y + box.height / 2);
  await page.mouse.down();
  await page.mouse.move(board.x + board.width * 0.5 + 12, board.y + board.height * 0.5, {
    steps: 6,
  });
  await page.mouse.up();
  await expect(node).toHaveCSS('left', `${board.width * 0.5}px`);
  await expect(page.locator('.moves')).toHaveText('1moves');
  await expect(page.locator('.untangle-feedback')).toHaveText('Node B placed.');
  await page.reload();
  await expect(node).toHaveAccessibleName('Node B, x 50, y 50');
  await expect(page.locator('.moves')).toHaveText('1moves');
});

test('an invalid drop is marked before release and leaves history unchanged', async ({
  page,
  isMobile,
}) => {
  test.skip(isMobile, 'Native mouse dragging runs in desktop projects.');
  await page.goto('/games/untangle/levels/level-4');
  await geometry(page);
  const node = page.locator('[data-node="B"]');
  const original = await node.getAttribute('aria-label');
  const from = (await node.boundingBox())!;
  const target = (await page.locator('[data-node="A"]').boundingBox())!;
  await page.mouse.move(from.x + from.width / 2, from.y + from.height / 2);
  await page.mouse.down();
  await page.mouse.move(target.x + target.width / 2, target.y + target.height / 2, { steps: 5 });
  await expect(node).toHaveClass(/invalid-drop/);
  await page.screenshot({ path: test.info().outputPath('invalid-drop.png'), fullPage: true });
  await expect(page.locator('.untangle-feedback')).toContainText('before releasing');
  await page.mouse.up();
  await expect(node).toHaveAccessibleName(original!);
  await expect(page.locator('.moves')).toHaveText('0moves');
  await expect(page.locator('.untangle-feedback')).toContainText('Node B was not moved');
  await expect(page.getByRole('button', { name: 'Undo', exact: true })).toBeDisabled();
});

test('D on level 2 continues after actual capture loss and commits exactly once', async ({
  page,
  isMobile,
}) => {
  test.skip(isMobile, 'Native mouse capture is exercised in desktop projects.');
  await page.goto('/games/untangle/levels/level-2');
  const board = await geometry(page);
  await page.locator('[data-node="A"]').click();
  await page.mouse.click(board.x + board.width * 0.371, board.y + board.height * 0.532);
  const node = page.locator('[data-node="D"]');
  const from = (await node.boundingBox())!;
  await page.mouse.move(from.x + from.width / 2, from.y + from.height / 2);
  await page.mouse.down();
  await page.mouse.move(board.x + board.width * 0.2, board.y + board.height * 0.4);
  const surface = page.locator('.untangle-board');
  await expect.poll(() => surface.evaluate((el) => el.hasPointerCapture(1))).toBe(true);
  await surface.evaluate((el) => el.releasePointerCapture(1));
  await page.mouse.move(board.x + board.width * 0.2, board.y + board.height * 0.2);
  await expect(page.locator('.moves')).toHaveText('1moves');
  await expect(node).toHaveAccessibleName('Node D, x 20, y 20');
  await page.mouse.up();
  await expect(page.locator('.moves')).toHaveText('2moves');
  await expect(page.locator('.untangle-feedback')).toHaveText('Node D placed.');
  await page.reload();
  await expect(node).toHaveAccessibleName('Node D, x 20, y 20');
});

test('explicit pointer cancellation still discards the preview and ignores a late release', async ({
  page,
  isMobile,
}) => {
  test.skip(isMobile, 'Native mouse gesture with an injected cancellation.');
  await page.goto('/games/untangle/levels/level-2');
  const board = await geometry(page);
  const node = page.locator('[data-node="D"]');
  const original = await node.getAttribute('aria-label');
  const from = (await node.boundingBox())!;
  await page.mouse.move(from.x + from.width / 2, from.y + from.height / 2);
  await page.mouse.down();
  await page.mouse.move(board.x + board.width * 0.5, board.y + board.height * 0.5);
  await page
    .locator('.untangle-board')
    .dispatchEvent('pointercancel', { pointerId: 1, pointerType: 'mouse' });
  await page.mouse.up();
  await expect(node).toHaveAccessibleName(original!);
  await expect(page.locator('.moves')).toHaveText('0moves');
  await expect(page.locator('.untangle-feedback')).toContainText('Drag cancelled');
});

test('missing release uses the last preview rather than the later cursor position', async ({
  page,
  isMobile,
}) => {
  test.skip(isMobile, 'Mouse-button recovery is specific to mouse input.');
  await page.goto('/games/untangle/levels/level-2');
  const board = await geometry(page);
  const node = page.locator('[data-node="D"]');
  const from = (await node.boundingBox())!;
  await page.mouse.move(from.x + from.width / 2, from.y + from.height / 2);
  await page.mouse.down();
  await page.mouse.move(board.x + board.width * 0.2, board.y + board.height * 0.2);
  await page.locator('.untangle-board').dispatchEvent('pointermove', {
    pointerId: 1,
    pointerType: 'mouse',
    buttons: 0,
    clientX: board.x + board.width * 0.9,
    clientY: board.y + board.height * 0.9,
  });
  await page.mouse.up();
  await expect(node).toHaveAccessibleName('Node D, x 20, y 20');
  await expect(page.locator('.moves')).toHaveText('1moves');
});

test('tap rejection can be exported and clearing diagnostics keeps progress', async ({
  page,
  isMobile,
}) => {
  await page.goto('/games/untangle/levels/level-4');
  const node = page.locator('[data-node="B"]');
  if (isMobile) await node.tap();
  else await node.click();
  // Selecting a low node can scroll the viewport; measure after that focus change.
  const board = await geometry(page);
  // Outside A's visible circle but within the required 12-unit clearance.
  const target = await page
    .locator('[data-node="A"]')
    .evaluate((el) => ({ x: parseFloat(el.style.left), y: parseFloat(el.style.top) }));
  const destination = {
    x: board.x + (board.width * (target.x + 10)) / 100,
    y: board.y + (board.height * target.y) / 100,
  };
  expect(
    await page.evaluate(
      ({ x, y }) => !!document.elementFromPoint(x, y)?.closest('.untangle-board'),
      destination,
    ),
  ).toBe(true);
  if (isMobile) await page.touchscreen.tap(destination.x, destination.y);
  else await page.mouse.click(destination.x, destination.y);
  await expect(page.locator('.untangle-feedback')).toContainText('Node B was not moved');
  await page.getByRole('button', { name: 'Settings', exact: true }).click();
  await page.screenshot({ path: test.info().outputPath('diagnostics.png'), fullPage: true });
  const downloading = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Download diagnostics' }).click();
  const download = await downloading;
  const report = JSON.parse(await readFile((await download.path())!, 'utf8'));
  expect(report.events).toContainEqual(
    expect.objectContaining({
      event: 'move-rejected',
      nodeId: 'B',
      input: 'tap',
      reason: 'Leave a little space between the nodes.',
    }),
  );
  expect(report).not.toHaveProperty('url');
  await page.getByRole('button', { name: 'Clear diagnostics' }).click();
  await expect(page.getByRole('button', { name: 'Download diagnostics' })).toBeDisabled();
  await page.getByRole('button', { name: 'Close settings' }).click();
  await expect(page.locator('.moves')).toHaveText('0moves');
});
