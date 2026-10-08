import type { OperatingMode } from './types';

/** Minimal hash router: #/setup/patch, #/show, #/party, … */

export type Route =
  | '/setup/projects'
  | '/setup/universes'
  | '/setup/patch'
  | '/setup/programmer'
  | '/setup/stage'
  | '/setup/looks'
  | '/setup/compositions'
  | '/setup/moves'
  | '/setup/scenes'
  | '/setup/party'
  | '/setup/dmx'
  | '/setup/settings'
  | '/show'
  | '/party'
  | '/view'
  | '/print/cues';

const ROUTES: Route[] = [
  '/setup/projects',
  '/setup/universes',
  '/setup/patch',
  '/setup/programmer',
  '/setup/stage',
  '/setup/looks',
  '/setup/compositions',
  '/setup/moves',
  '/setup/scenes',
  '/setup/party',
  '/setup/dmx',
  '/setup/settings',
  '/show',
  '/party',
  '/view',
  '/print/cues',
];

function parse(hash: string): Route | null {
  const path = hash.replace(/^#/, '');
  return (ROUTES as string[]).includes(path) ? (path as Route) : null;
}

export const router = $state({ route: parse(location.hash) ?? ('/setup/patch' as Route) });

window.addEventListener('hashchange', () => {
  router.route = parse(location.hash) ?? '/setup/patch';
});

export function navigate(route: Route) {
  location.hash = route;
}

/** The operating mode a page belongs to: show and party have one page each, everything else is setup. */
export function modeOfRoute(route: Route): OperatingMode {
  if (route === '/show') return 'show';
  if (route === '/party') return 'party';
  return 'setup';
}

/** Pages for printing: shown without the app around them, in both modes. */
export function isPrintPage(route: Route): boolean {
  return route.startsWith('/print/');
}

/**
 * Pages standing on their own, outside setup, show and party: the print pages and the view-only stage (#/view) for
 * phones. They do not follow the operating mode and have no controls of the app.
 */
export function isStandalonePage(route: Route): boolean {
  return isPrintPage(route) || route === '/view';
}
