import type {
  AccessStatus,
  ArtNetNode,
  Backup,
  Backups,
  Composition,
  CompositionRequest,
  AttributeValues,
  Fixture,
  FixtureRequest,
  FixtureType,
  Group,
  GroupRequest,
  LibraryIssue,
  MasterState,
  OperatingMode,
  PlaybackStatus,
  PatchIssue,
  Position,
  Project,
  ProjectRequest,
  Scene,
  SceneRequest,
  Universe,
  UniverseRequest,
} from './types';

/** Error returned by the backend as RFC 9457 problem details. */
export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly errors: string[] = [],
  ) {
    super(message);
  }
}

async function request<T>(method: string, url: string, body?: unknown): Promise<T> {
  let response: Response;
  try {
    const raw = body instanceof Blob;
    response = await fetch(url, {
      method,
      headers: body === undefined ? {} : { 'Content-Type': raw ? body.type : 'application/json' },
      body: body === undefined ? undefined : raw ? body : JSON.stringify(body),
    });
  } catch {
    throw new ApiError('Lumen server is not reachable', 0);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  const text = await response.text();
  const json = text ? JSON.parse(text) : undefined;
  if (!response.ok) {
    if (response.status === 403 && json?.locked) {
      // setup is locked for this browser: App asks for the PIN
      window.dispatchEvent(new CustomEvent('lumen:locked'));
    }
    throw new ApiError(json?.detail ?? `Request failed (${response.status})`, response.status, json?.errors ?? []);
  }
  return json as T;
}

const get = <T>(url: string) => request<T>('GET', url);
const post = <T>(url: string, body?: unknown) => request<T>('POST', url, body ?? {});
const put = <T>(url: string, body: unknown) => request<T>('PUT', url, body);
const patch = <T>(url: string, body: unknown) => request<T>('PATCH', url, body);
const del = (url: string) => request<void>('DELETE', url);

export const api = {
  library: {
    reload: () => post<void>('/api/library/reload'),
    fixtureTypes: () => get<FixtureType[]>('/api/library/fixture-types'),
    issues: () => get<LibraryIssue[]>('/api/library/issues'),
  },
  projects: {
    list: () => get<Project[]>('/api/projects'),
    create: (project: ProjectRequest) => post<Project>('/api/projects', project),
    update: (id: number, project: ProjectRequest) => put<Project>(`/api/projects/${id}`, project),
    remove: (id: number) => del(`/api/projects/${id}`),
    activate: (id: number) => post<Project>(`/api/projects/${id}/activate`),
    import: (file: unknown) => post<Project>('/api/projects/import', file),
  },
  universes: {
    list: (projectId: number) => get<Universe[]>(`/api/projects/${projectId}/universes`),
    create: (projectId: number, universe: UniverseRequest) =>
      post<Universe>(`/api/projects/${projectId}/universes`, universe),
    update: (projectId: number, id: number, universe: UniverseRequest) =>
      put<Universe>(`/api/projects/${projectId}/universes/${id}`, universe),
    remove: (projectId: number, id: number) => del(`/api/projects/${projectId}/universes/${id}`),
  },
  fixtures: {
    list: (projectId: number) => get<Fixture[]>(`/api/projects/${projectId}/fixtures`),
    create: (projectId: number, fixture: FixtureRequest) =>
      post<Fixture>(`/api/projects/${projectId}/fixtures`, fixture),
    update: (projectId: number, id: number, fixture: FixtureRequest) =>
      put<Fixture>(`/api/projects/${projectId}/fixtures/${id}`, fixture),
    remove: (projectId: number, id: number) => del(`/api/projects/${projectId}/fixtures/${id}`),
  },
  groups: {
    list: (projectId: number) => get<Group[]>(`/api/projects/${projectId}/groups`),
    create: (projectId: number, group: GroupRequest) => post<Group>(`/api/projects/${projectId}/groups`, group),
    update: (projectId: number, id: number, group: GroupRequest) =>
      put<Group>(`/api/projects/${projectId}/groups/${id}`, group),
    remove: (projectId: number, id: number) => del(`/api/projects/${projectId}/groups/${id}`),
  },
  scenes: {
    list: (projectId: number) => get<Scene[]>(`/api/projects/${projectId}/scenes`),
    create: (projectId: number, scene: SceneRequest) => post<Scene>(`/api/projects/${projectId}/scenes`, scene),
    update: (projectId: number, id: number, scene: SceneRequest) =>
      put<Scene>(`/api/projects/${projectId}/scenes/${id}`, scene),
    record: (projectId: number, id: number) => post<Scene>(`/api/projects/${projectId}/scenes/${id}/record`),
    load: (projectId: number, id: number) => post<void>(`/api/projects/${projectId}/scenes/${id}/load`),
    remove: (projectId: number, id: number) => del(`/api/projects/${projectId}/scenes/${id}`),
  },
  compositions: {
    library: (projectId: number) => get<Composition[]>(`/api/projects/${projectId}/compositions`),
    get: (projectId: number, id: number) => get<Composition>(`/api/projects/${projectId}/compositions/${id}`),
    create: (projectId: number, composition: CompositionRequest) =>
      post<Composition>(`/api/projects/${projectId}/compositions`, composition),
    update: (projectId: number, id: number, composition: CompositionRequest) =>
      put<Composition>(`/api/projects/${projectId}/compositions/${id}`, composition),
    remove: (projectId: number, id: number) => del(`/api/projects/${projectId}/compositions/${id}`),
    ofScene: (projectId: number, sceneId: number) =>
      get<Composition[]>(`/api/projects/${projectId}/scenes/${sceneId}/compositions`),
    insert: (projectId: number, sceneId: number, libraryId: number) =>
      post<Composition>(`/api/projects/${projectId}/scenes/${sceneId}/compositions`, { libraryId }),
    reorder: (projectId: number, sceneId: number, ids: number[]) =>
      put<Composition[]>(`/api/projects/${projectId}/scenes/${sceneId}/compositions/order`, { ids }),
    reloadFromLibrary: (projectId: number, id: number) =>
      post<Composition>(`/api/projects/${projectId}/compositions/${id}/reload-from-library`),
    saveToLibrary: (projectId: number, id: number) =>
      post<Composition>(`/api/projects/${projectId}/compositions/${id}/save-to-library`),
    stagePreview: () => get<{ compositionId: number | null }>('/api/compositions/stage-preview'),
    showOnStage: (projectId: number, id: number) =>
      post<{ compositionId: number | null }>(`/api/projects/${projectId}/compositions/${id}/stage-preview`),
    stopStagePreview: () => request<{ compositionId: number | null }>('DELETE', '/api/compositions/stage-preview'),
  },
  stage: {
    place: (projectId: number, positions: Record<number, Position | null>) =>
      put<void>(`/api/projects/${projectId}/fixtures/positions`, { positions }),
    planUrl: (projectId: number, version: number) => `/api/projects/${projectId}/stage/plan?v=${version}`,
    uploadPlan: (projectId: number, image: Blob) => put<void>(`/api/projects/${projectId}/stage/plan`, image),
    removePlan: (projectId: number) => del(`/api/projects/${projectId}/stage/plan`),
  },
  patchIssues: (projectId: number) => get<PatchIssue[]>(`/api/projects/${projectId}/patch/issues`),
  programmer: {
    set: (fixtureId: number, values: AttributeValues) =>
      patch<AttributeValues>(`/api/programmer/fixtures/${fixtureId}`, values),
    setSelection: (fixtureIds: number[], values: AttributeValues) =>
      patch<Record<number, AttributeValues>>('/api/programmer/fixtures', { fixtureIds, values }),
    clearFixture: (fixtureId: number) => del(`/api/programmer/fixtures/${fixtureId}`),
    clearFixtures: (fixtureIds: number[]) => post<void>('/api/programmer/clear', { fixtureIds }),
    clearAll: () => del('/api/programmer'),
  },
  output: {
    master: () => get<MasterState>('/api/output/master'),
    setMaster: (level: number) => put<MasterState>('/api/output/master', { level }),
    setBlackout: (active: boolean) => put<MasterState>('/api/output/blackout', { active }),
  },
  playback: {
    status: () => get<PlaybackStatus>('/api/playback'),
    go: () => post<PlaybackStatus>('/api/playback/go'),
    back: () => post<PlaybackStatus>('/api/playback/back'),
    pause: () => post<PlaybackStatus>('/api/playback/pause'),
    finish: () => post<PlaybackStatus>('/api/playback/finish'),
    setNext: (sceneId: number) => put<PlaybackStatus>('/api/playback/next', { sceneId }),
    resume: () => post<PlaybackStatus>('/api/playback/resume'),
    dismissResume: () => request<PlaybackStatus>('DELETE', '/api/playback/resume'),
  },
  mode: {
    get: () => get<{ mode: OperatingMode }>('/api/mode'),
    set: (mode: OperatingMode) => put<{ mode: OperatingMode }>('/api/mode', { mode }),
  },
  artnet: {
    nodes: () => get<ArtNetNode[]>('/api/artnet/nodes'),
  },
  access: {
    status: () => get<AccessStatus>('/api/access'),
    unlock: (pin: string) => post<AccessStatus>('/api/access/unlock', { pin }),
    lock: () => post<void>('/api/access/lock'),
    setPin: (pin: string | null) => put<AccessStatus>('/api/access/pin', { pin }),
    setNetworkAccess: (enabled: boolean) => put<AccessStatus>('/api/access/network', { enabled }),
  },
  backups: {
    list: () => get<Backups>('/api/backups'),
    create: () => post<Backup>('/api/backups'),
  },
  health: () => get<{ status: string }>('/actuator/health'),
};
