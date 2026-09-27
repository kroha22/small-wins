import { type ReactNode, type KeyboardEvent } from 'react';
export function Grid({
  rows,
  cols,
  label,
  children,
}: {
  rows: number;
  cols: number;
  label: string;
  children: ReactNode;
}) {
  function navigate(event: KeyboardEvent<HTMLDivElement>) {
    const current = (event.target as HTMLElement).closest<HTMLButtonElement>('[data-cell]');
    if (!current) return;
    const index = Number(current.dataset.cell);
    let next: number;
    if (event.key === 'ArrowRight') next = Math.min(rows * cols - 1, index + 1);
    else if (event.key === 'ArrowLeft') next = Math.max(0, index - 1);
    else if (event.key === 'ArrowDown') next = Math.min(rows * cols - 1, index + cols);
    else if (event.key === 'ArrowUp') next = Math.max(0, index - cols);
    else if (event.key === 'Home') next = Math.floor(index / cols) * cols;
    else if (event.key === 'End') next = Math.floor(index / cols) * cols + cols - 1;
    else return;
    event.preventDefault();
    const buttons = event.currentTarget.querySelectorAll<HTMLButtonElement>('[data-cell]');
    buttons.forEach((b) => (b.tabIndex = -1));
    const target = buttons[next];
    if (target) {
      target.tabIndex = 0;
      target.focus();
    }
  }
  return (
    <div
      className="puzzle-grid"
      role="group"
      aria-label={label}
      style={{ gridTemplateColumns: `repeat(${cols},minmax(0,1fr))` }}
      onKeyDown={navigate}
    >
      {children}
    </div>
  );
}
