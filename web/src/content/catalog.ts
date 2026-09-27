import { Cat, Spline, Network, Route, Grid3X3, ScanSearch } from 'lucide-react';
import { type GameId } from '../shared/lib/engine';
export const catalog = [
  {
    id: 'purrdoku',
    contentVersion: 4,
    name: 'Purrdoku',
    eyebrow: 'A place for every cat',
    description: 'A cosy house. A few clues. Find where every cat belongs.',
    rule: 'Place one cat in each row and column, and satisfy every clue.',
    icon: Cat,
    tags: 'Logic · Deduction',
    colour: 'apricot',
  },
  {
    id: 'pipes',
    contentVersion: 1,
    name: 'Burrow Network',
    eyebrow: 'Find the way through',
    description: 'Turn the tunnel pieces until every burrow connects.',
    rule: 'Rotate the tunnel pieces to connect every burrow to the source, with no open ends.',
    icon: Spline,
    tags: 'Patterns · Connection',
    colour: 'blue',
  },
  {
    id: 'untangle',
    contentVersion: 1,
    name: 'Untangle',
    eyebrow: 'Make a little space',
    description: 'Move a point. Shift your perspective. Find the calm in the tangle.',
    rule: 'Move the nodes until no lines cross, overlap, or touch another node.',
    icon: Network,
    tags: 'Spatial · Perspective',
    colour: 'pink',
  },
  {
    id: 'waypoints',
    contentVersion: 1,
    name: 'Waypoints',
    eyebrow: 'Enjoy the little detours',
    description: 'A rabbit, a few carrots, and a path to find.',
    rule: 'Collect every carrot, then reach the flag. No diagonal moves or revisiting cells.',
    icon: Route,
    tags: 'Planning · Discovery',
    colour: 'green',
  },
  {
    id: 'shikaku',
    contentVersion: 1,
    name: 'Shikaku',
    eyebrow: 'Make room for every clue',
    description: 'Draw tidy rectangles and give each clue exactly the space it needs.',
    rule: 'Divide the whole grid into rectangles. Each rectangle contains one clue and matches its area.',
    icon: Grid3X3,
    tags: 'Spatial · Packing',
    colour: 'yellow',
  },
  {
    id: 'wildlife-survey',
    contentVersion: 1,
    name: 'Habitat Search',
    eyebrow: 'A pond or a burrow',
    description: 'Look carefully, find what is hidden, and keep a little map of your search.',
    rule: 'Observe the board and find every hidden fish or burrow.',
    icon: ScanSearch,
    tags: 'Observation · Logic',
    colour: 'teal',
  },
] as const;
export function findGame(id: string) {
  return catalog.find((g) => g.id === id);
}
export const levelMetadata = [
  { id: 'tutorial', difficulty: 'tutorial', label: 'Start here' },
  { id: 'level-1', difficulty: 'easy', label: '01' },
  { id: 'level-2', difficulty: 'easy', label: '02' },
  { id: 'level-3', difficulty: 'medium', label: '03' },
  { id: 'level-4', difficulty: 'medium', label: '04' },
  { id: 'level-5', difficulty: 'hard', label: '05' },
  { id: 'level-6', difficulty: 'hard', label: '06' },
] as const;
export const loaders: Record<
  GameId,
  () => Promise<{ default: React.ComponentType<{ levelId: string }> }>
> = {
  purrdoku: () => import('../games/purrdoku/Game'),
  pipes: () => import('../games/pipes/Game'),
  untangle: () => import('../games/untangle/Game'),
  waypoints: () => import('../games/waypoints/Game'),
  shikaku: () => import('../games/shikaku/Game'),
  'wildlife-survey': () => import('../games/wildlife-survey/Game'),
};
