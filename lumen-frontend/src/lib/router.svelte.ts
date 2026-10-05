/** Minimal hash router: #/setup/patch, #/show, … */

export type Route =
  | '/setup/projects'
  | '/setup/universes'
  | '/setup/patch'
  | '/setup/programmer'
  | '/setup/stage'
  | '/setup/compositions'
  | '/setup/scenes'
  | '/setup/dmx'
  | '/setup/settings'
  | '/show'
  | '/print/cues';

const ROUTES: Route[] = [
  '/setup/projects',
  '/setup/universes',
  '/setup/patch',
  '/setup/programmer',
  '/setup/stage',
  '/setup/compositions',
  '/setup/scenes',
  '/setup/dmx',
  '/setup/settings',
  '/show',
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

export function isShowMode(route: Route): boolean {
  return route === '/show';
}

/** Pages for printing: shown without the app around them, in both modes. */
export function isPrintPage(route: Route): boolean {
  return route.startsWith('/print/');
}
