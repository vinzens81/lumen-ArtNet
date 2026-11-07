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
  | '/view/script'
  | '/view/cues'
  | '/remote'
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
  '/view/script',
  '/view/cues',
  '/remote',
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

/**
 * The operating mode a page belongs to (and whose colors it wears): show and party have one page each, the show
 * remote belongs to the show, everything else is setup.
 */
export function modeOfRoute(route: Route): OperatingMode {
  if (route === '/show' || route === '/remote') return 'show';
  if (route === '/party') return 'party';
  return 'setup';
}

/** Pages for printing: shown without the app around them, in both modes. */
export function isPrintPage(route: Route): boolean {
  return route.startsWith('/print/');
}

/**
 * Pages standing on their own, outside setup, show and party: the print pages, the view-only stage (#/view) and the
 * view-only script (#/view/script) for phones and tablets, the view-only cue list (#/view/cues) for a phone, and the
 * show remote for a tablet (#/remote). They do not follow the operating mode and have no controls of the app around
 * them.
 */
export function isStandalonePage(route: Route): boolean {
  return isPrintPage(route) || route === '/view' || route === '/view/script' || route === '/view/cues'
    || route === '/remote';
}
