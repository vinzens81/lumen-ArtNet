/** Minimal hash router: #/setup/patch, #/show, … */

export type Route =
  | '/setup/projects'
  | '/setup/universes'
  | '/setup/patch'
  | '/setup/stage'
  | '/setup/scenes'
  | '/show';

const ROUTES: Route[] = ['/setup/projects', '/setup/universes', '/setup/patch', '/setup/stage', '/setup/scenes', '/show'];

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
