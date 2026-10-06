import type {
  AccessStatus,
  ArtNetNode,
  LocalNetwork,
  Backup,
  Backups,
  Composition,
  CompositionRequest,
  FaderSetup,
  AttributeValues,
  Fixture,
  FixtureRequest,
  FixtureType,
  KeyMode,
  Generator,
  Group,
  GroupRequest,
  LibraryIssue,
  Look,
  LookRevision,
  OflImportResult,
  MasterState,
  OperatingMode,
  PlaybackStatus,
  PatchIssue,
  Position,
  Project,
  ProjectRequest,
  RecordRequest,
  Scene,
  SceneComposition,
  SceneKey,
  SceneLook,
  SceneRequest,
  PartyMatrix,
  PartyStatus,
  Preset,
  PresetGroup,
  PresetRefs,
  StagePreviewStatus,
  StageShape,
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
    /** imports an "OFL JSON" download of the Open Fixture Library as a profile */
    importOfl: (json: Blob, manufacturer: string, replace: boolean) =>
      post<OflImportResult>(
        `/api/library/import/ofl?manufacturer=${encodeURIComponent(manufacturer)}&replace=${replace}`, json),
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
  faders: {
    list: (projectId: number) => get<FaderSetup[]>(`/api/projects/${projectId}/faders`),
    save: (projectId: number, slot: number, name: string | null, compositionId: number) =>
      put<FaderSetup>(`/api/projects/${projectId}/faders/${slot}`, { name, compositionId }),
    remove: (projectId: number, slot: number) => del(`/api/projects/${projectId}/faders/${slot}`),
  },
  presets: {
    list: (projectId: number) => get<Preset[]>(`/api/projects/${projectId}/presets`),
    /** records a preset from the programmer values of the fixtures */
    create: (projectId: number, name: string, group: PresetGroup, fixtureIds: number[]) =>
      post<Preset>(`/api/projects/${projectId}/presets`, { name, group, fixtureIds }),
    rename: (projectId: number, id: number, name: string) =>
      put<Preset>(`/api/projects/${projectId}/presets/${id}`, { name }),
    /** takes the programmer values of the fixtures into the preset; scenes using it change with it */
    record: (projectId: number, id: number, fixtureIds: number[]) =>
      post<Preset>(`/api/projects/${projectId}/presets/${id}/record`, { fixtureIds }),
    apply: (projectId: number, id: number, fixtureIds: number[]) =>
      post<void>(`/api/projects/${projectId}/presets/${id}/apply`, { fixtureIds }),
    remove: (projectId: number, id: number) => del(`/api/projects/${projectId}/presets/${id}`),
  },
  scenes: {
    list: (projectId: number) => get<Scene[]>(`/api/projects/${projectId}/scenes`),
    create: (projectId: number, scene: SceneRequest) => post<Scene>(`/api/projects/${projectId}/scenes`, scene),
    update: (projectId: number, id: number, scene: SceneRequest) =>
      put<Scene>(`/api/projects/${projectId}/scenes/${id}`, scene),
    record: (projectId: number, id: number, request?: RecordRequest) =>
      post<Scene>(`/api/projects/${projectId}/scenes/${id}/record`, request),
    setCompositions: (projectId: number, id: number, compositions: SceneComposition[]) =>
      put<Scene>(`/api/projects/${projectId}/scenes/${id}/compositions`, compositions),
    /** The light of a part: after the marker, or the start of the scene for marker null. */
    setSection: (projectId: number, id: number, section: { marker: string | null; compositions: SceneComposition[];
      looks: SceneLook[] }) => put<Scene>(`/api/projects/${projectId}/scenes/${id}/sections`, section),
    setKeys: (projectId: number, id: number, keys: SceneKey[]) =>
      put<Scene>(`/api/projects/${projectId}/scenes/${id}/keys`, keys),
    duplicate: (projectId: number, id: number, number: string | null) =>
      post<Scene>(`/api/projects/${projectId}/scenes/${id}/duplicate`, { number }),
    load: (projectId: number, id: number) => post<void>(`/api/projects/${projectId}/scenes/${id}/load`),
    remove: (projectId: number, id: number) => del(`/api/projects/${projectId}/scenes/${id}`),
  },
  looks: {
    list: (projectId: number) => get<Look[]>(`/api/projects/${projectId}/looks`),
    create: (projectId: number, name: string, record: boolean) =>
      post<Look>(`/api/projects/${projectId}/looks`, { name, record }),
    update: (projectId: number, id: number, name: string, shared?: boolean) =>
      put<Look>(`/api/projects/${projectId}/looks/${id}`, { name, shared }),
    duplicate: (projectId: number, id: number) => post<Look>(`/api/projects/${projectId}/looks/${id}/duplicate`),
    record: (projectId: number, id: number, request?: RecordRequest) =>
      post<Look>(`/api/projects/${projectId}/looks/${id}/record`, request),
    revisions: (projectId: number, id: number) =>
      get<LookRevision[]>(`/api/projects/${projectId}/looks/${id}/revisions`),
    restore: (projectId: number, id: number, revisionId: number) =>
      post<Look>(`/api/projects/${projectId}/looks/${id}/revisions/${revisionId}/restore`),
    load: (projectId: number, id: number) => post<void>(`/api/projects/${projectId}/looks/${id}/load`),
    remove: (projectId: number, id: number) => del(`/api/projects/${projectId}/looks/${id}`),
  },
  compositions: {
    list: (projectId: number) => get<Composition[]>(`/api/projects/${projectId}/compositions`),
    get: (projectId: number, id: number) => get<Composition>(`/api/projects/${projectId}/compositions/${id}`),
    create: (projectId: number, composition: CompositionRequest) =>
      post<Composition>(`/api/projects/${projectId}/compositions`, composition),
    update: (projectId: number, id: number, composition: CompositionRequest) =>
      put<Composition>(`/api/projects/${projectId}/compositions/${id}`, composition),
    duplicate: (projectId: number, id: number) =>
      post<Composition>(`/api/projects/${projectId}/compositions/${id}/duplicate`),
    recordStep: (projectId: number, id: number) =>
      post<Composition>(`/api/projects/${projectId}/compositions/${id}/record-step`),
    generate: (projectId: number, id: number, generator: Generator) =>
      post<Composition>(`/api/projects/${projectId}/compositions/${id}/generate`, generator),
    remove: (projectId: number, id: number) => del(`/api/projects/${projectId}/compositions/${id}`),
  },
  party: {
    status: () => get<PartyStatus>('/api/party'),
    press: (row: number, column: number) => post<PartyStatus>(`/api/party/cells/${row}/${column}/press`),
    release: (row: number, column: number) => post<PartyStatus>(`/api/party/cells/${row}/${column}/release`),
    clear: () => post<PartyStatus>('/api/party/clear'),
  },
  partyMatrix: {
    get: (projectId: number) => get<PartyMatrix>(`/api/projects/${projectId}/party-matrix`),
    save: (projectId: number, matrix: PartyMatrix) =>
      put<PartyMatrix>(`/api/projects/${projectId}/party-matrix`, matrix),
  },
  stagePreview: {
    status: () => get<StagePreviewStatus>('/api/stage-preview'),
    showComposition: (projectId: number, id: number) =>
      post<StagePreviewStatus>(`/api/projects/${projectId}/compositions/${id}/stage-preview`),
    showScene: (projectId: number, sceneId: number) =>
      post<StagePreviewStatus>(`/api/projects/${projectId}/scenes/${sceneId}/stage-preview`),
    rehearseFade: (projectId: number, sceneId: number) =>
      post<StagePreviewStatus>(`/api/projects/${projectId}/scenes/${sceneId}/rehearse-fade`),
    stop: () => request<StagePreviewStatus>('DELETE', '/api/stage-preview'),
  },
  stage: {
    place: (projectId: number, positions: Record<number, Position | null>) =>
      put<void>(`/api/projects/${projectId}/fixtures/positions`, { positions }),
    shape: (projectId: number, fixtureId: number, shape: StageShape) =>
      put<void>(`/api/projects/${projectId}/fixtures/${fixtureId}/stage-shape`, shape),
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
    setScriptLine: (sceneId: number, line: number, section: number) =>
      put<PlaybackStatus>('/api/playback/script', { sceneId, line, section }),
    pressKey: (slot: number) => post<PlaybackStatus>(`/api/playback/keys/${slot}/press`),
    releaseKey: (slot: number) => post<PlaybackStatus>(`/api/playback/keys/${slot}/release`),
    setFader: (slot: number, level: number) => put<PlaybackStatus>(`/api/playback/faders/${slot}`, { level }),
    resume: () => post<PlaybackStatus>('/api/playback/resume'),
    dismissResume: () => request<PlaybackStatus>('DELETE', '/api/playback/resume'),
  },
  mode: {
    get: () => get<{ mode: OperatingMode }>('/api/mode'),
    set: (mode: OperatingMode) => put<{ mode: OperatingMode }>('/api/mode', { mode }),
  },
  artnet: {
    nodes: () => get<ArtNetNode[]>('/api/artnet/nodes'),
    networks: () => get<LocalNetwork[]>('/api/artnet/networks'),
    forgetNode: (ipAddress: string) => del(`/api/artnet/expected-nodes/${encodeURIComponent(ipAddress)}`),
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
