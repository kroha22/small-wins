import { useState } from 'react';
import { type Meta, type StoryObj } from '@storybook/react-vite';
import { BoardToolbar } from './GameFrame';
const meta = {
  title: 'Play/Board toolbar',
  component: BoardToolbar,
  args: { canUndo: false, solved: false, undo: () => {}, restart: () => {}, hint: () => {} },
} satisfies Meta<typeof BoardToolbar>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Fresh: Story = {};
export const WithoutHints: Story = { args: { canHint: false } };
export const InProgress: Story = { args: { canUndo: true } };
export const Solved: Story = {
  args: {
    canUndo: true,
    solved: true,
    nextAction: (
      <a className="button primary" href="#next">
        Next puzzle →
      </a>
    ),
  },
};
export const LastPuzzle: Story = {
  args: {
    canUndo: true,
    solved: true,
    nextAction: (
      <a className="button primary" href="#collection">
        More puzzles →
      </a>
    ),
  },
};
export const HintPending: Story = { args: { pending: true } };
export const Interactive: Story = {
  render: function InteractiveToolbar() {
    const [moves, setMoves] = useState(0);
    return (
      <div className="board-panel apricot">
        <p>{moves} moves</p>
        <button className="button primary" onClick={() => setMoves(moves + 1)}>
          Make a move
        </button>
        <BoardToolbar
          undo={() => setMoves(Math.max(0, moves - 1))}
          restart={() => setMoves(0)}
          hint={() => {}}
          canUndo={moves > 0}
          solved={false}
        />
      </div>
    );
  },
};
