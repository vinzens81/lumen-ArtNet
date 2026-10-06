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
  | '/setup/scenes'
  | '/setup/party'
  | '/setup/dmx'
  | '/setup/settings'
  | '/show'
  | '/party'
  | '/print/cues';

const ROUTES: Route[] = [
  '/setup/projects',
  '/setup/universes',
  '/setup/patch',
  '/setup/programmer',
  '/setup/stage',
  '/setup/looks',
  '/setup/compositions',
  '/setup/scenes',
  '/setup/party',
  '/setup/dmx',
  '/setup/settings',
  '/show',
  '/party',
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
