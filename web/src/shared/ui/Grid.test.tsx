// @vitest-environment jsdom
import { afterEach, it, expect } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Grid } from './Grid';
afterEach(cleanup);
it('moves roving keyboard focus and invokes native button activation', async () => {
  const user = userEvent.setup();
  let selected = -1;
  render(
    <Grid rows={2} cols={2} label="Test board">
      {[0, 1, 2, 3].map((n) => (
        <button key={n} data-cell={n} tabIndex={n === 0 ? 0 : -1} onClick={() => (selected = n)}>
          Cell {n}
        </button>
      ))}
    </Grid>,
  );
  await user.tab();
  expect(document.activeElement).toBe(screen.getByText('Cell 0'));
  await user.keyboard('{ArrowDown}{ArrowRight}{Enter}');
  expect(document.activeElement).toBe(screen.getByText('Cell 3'));
  expect(selected).toBe(3);
  expect(screen.getByText('Cell 0').tabIndex).toBe(-1);
});
