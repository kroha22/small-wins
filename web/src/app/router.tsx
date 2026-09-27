import { createRootRoute, createRoute, createRouter } from '@tanstack/react-router';
import { z } from 'zod';
import { Shell, NotFound, Home, GameOverview, Play } from './pages';
const rootRoute = createRootRoute({ component: Shell, notFoundComponent: NotFound });
const indexRoute = createRoute({ getParentRoute: () => rootRoute, path: '/', component: Home });
const overviewRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/games/$gameId',
  validateSearch: (search) => ({
    difficulty: z.enum(['all', 'easy', 'medium', 'hard']).catch('all').parse(search.difficulty),
  }),
  component: GameOverview,
});
const playRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/games/$gameId/levels/$levelId',
  component: Play,
});
export const router = createRouter({
  routeTree: rootRoute.addChildren([indexRoute, overviewRoute, playRoute]),
  scrollRestoration: true,
  defaultPreload: 'intent',
});
declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router;
  }
}
