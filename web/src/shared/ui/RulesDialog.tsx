import * as Dialog from '@radix-ui/react-dialog';
import { BookOpen, X } from 'lucide-react';
import { type GameId } from '../lib/engine';
const rules: Record<GameId, { name: string; items: string[] }> = {
  purrdoku: {
    name: 'Purrdoku',
    items: [
      'Place one cat in each row and column. Most squares stay empty. Choose a cat, then choose a square.',
      'A cat may move freely to any empty square, or return to the tray with Remove. The clues are checked when you finish the puzzle.',
      'Left and right compare columns; above and below compare rows. The cats do not need to be in the same row or column.',
      'Named rooms have matching floors and outlined boundaries. Each room has one furniture object, and each cat belongs in a different room. Windows, chairs and other objects occupy their squares. Next to an object means one square up, down, left or right, never diagonally or on the object itself. Room boundaries do not change adjacency.',
      'A clue marked “Fits so far” only describes the cats currently placed. All cats and all clues must fit to finish. Number marks identify cats, not positions.',
      'Arrow keys move between squares; Enter or Space places a cat. Delete removes the cat in the focused square. Tap a clue to pin it between the board and your cats.',
    ],
  },
  pipes: {
    name: 'Burrow Network',
    items: [
      'Tap a pipe to turn it clockwise. Connect every pipe to the water source, marked with a white circle.',
      'Blue pipes are connected to the source. Dots mark open ends: every opening needs a matching neighbour, and none may point outside the board.',
      'A loop is allowed. There is no separate drain. You finish when the whole network is connected and no open ends remain.',
      'Arrow keys move between pipes. Enter or Space turns a pipe; Shift Enter turns it counterclockwise.',
    ],
  },
  untangle: {
    name: 'Untangle',
    items: [
      'Move the lettered nodes until the lines no longer cross. Dashed lines are involved in a conflict; clear lines are solid.',
      'Lines may meet at their shared endpoint, but cannot overlap or pass through another node. Pinned nodes cannot move.',
      'Select a node, then tap an open part of the board. You can also drag a node and release it to place it. Leave a little space between nodes.',
      'With a node focused, use arrow keys to preview a move, Shift with arrows for larger steps, and Enter or Space to place it. Escape cancels.',
      'Crossings are allowed while you work. One placed node counts as one move, even after a long drag.',
    ],
  },
  waypoints: {
    name: 'Waypoints',
    items: [
      'Begin at the rabbit, collect every carrot in any order, then reach the flag. You do not need to visit every empty square.',
      'Tap a square directly beside the end of the path, or trace neighbouring squares with a finger. Diagonal moves, blocked squares and revisiting squares are not allowed.',
      'The flag is only available once all carrots have been collected. Dead ends are part of the puzzle: Undo removes one step.',
      'Use arrow keys to focus a square and Enter or Space to add it. Each added square is a move. Canceling a drag keeps the steps already placed.',
    ],
  },
  shikaku: {
    name: 'Shikaku',
    items: [
      'Choose two opposite corners to draw one rectangle.',
      'Every rectangle must contain exactly one number.',
      'The number is the required area: a 4 covers four squares, a 6 covers six squares.',
      'Rectangles cannot overlap, and the whole board must be covered to finish.',
      'Choose a different pair of corners if the rectangle is rejected. Undo removes the last region.',
    ],
  },
  'wildlife-survey': {
    name: 'Habitat Search',
    items: [
      'Search one of two habitats: Pond Life or Burrows.',
      'Choose Observe to check a square, or Note to place a private × without changing the puzzle.',
      'Solo Search gives you one board. Two players is a local pass-and-play match with a private board for each player.',
      'The first player to find every hidden object wins. No network connection is needed.',
    ],
  },
};
export function RulesDialog({ gameId }: { gameId: GameId }) {
  const game = rules[gameId];
  return (
    <Dialog.Root>
      <Dialog.Trigger className="rules-trigger">
        <BookOpen size={16} />
        How to play
      </Dialog.Trigger>
      <Dialog.Portal>
        <Dialog.Overlay className="dialog-overlay" />
        <Dialog.Content className="dialog-content">
          <Dialog.Title>How to play {game.name}</Dialog.Title>
          <Dialog.Description>
            {gameId === 'untangle'
              ? 'Take your time. Undo and restart are always here to help.'
              : 'Hints begin with a smaller search area, then reveal a specific spot or step. You make the move yourself.'}
          </Dialog.Description>
          <ol className="rules-list">
            {game.items.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ol>
          <Dialog.Close className="dialog-close" aria-label="Close rules">
            <X size={20} />
          </Dialog.Close>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
