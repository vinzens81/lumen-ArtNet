// DTOs of the Lumen REST API

export interface Range {
  from: number;
  to: number;
  label: string;
  color: string | null;
}

export interface Channel {
  offset: number;
  function: string;
  name: string;
  resolution: number;
  dmxChannelCount: number;
  defaultValue: number;
  invert: boolean;
  fade: string;
  ranges: Range[];
}

export interface Mode {
  name: string;
  dmxChannelCount: number;
  channels: Channel[];
}

export interface FixtureType {
  id: string;
  manufacturer: string;
  model: string;
  category: string;
  physical: { panRange: number | null; tiltRange: number | null };
  modes: Mode[];
}

export interface LibraryIssue {
  severity: 'error' | 'warning';
  source: string;
  fixtureTypeId: string | null;
  message: string;
}

export interface Project {
  id: number;
  name: string;
  venue: string | null;
  fadeInMs: number;
  fadeOutMs: number;
  /** shape of all fades, unless a scene has its own */
  fadeCurve: FadeCurve;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ProjectRequest {
  name: string;
  venue: string | null;
  fadeInMs: number;
  fadeOutMs: number;
  fadeCurve: FadeCurve;
}

/** Shape of a fade: linear in DMX values, gentle start and end, or square law (softer in the dark). */
export type FadeCurve = 'linear' | 's-curve' | 'square';

export interface ArtNet {
  net: number;
  subnet: number;
  universe: number;
}

/** auto: nodes found via ArtPoll (+ manual IPs) · unicast: only manual IPs · broadcast: everybody */
export type DeliveryMode = 'auto' | 'unicast' | 'broadcast';

export interface Universe {
  id: number;
  number: number;
  name: string | null;
  displayName: string;
  artnet: ArtNet;
  portAddress: number;
  mode: DeliveryMode;
  ipAddresses: string[];
  /** interface the packets go out on, e.g. "en7"; null = all networks */
  networkInterface: string | null;
}

export interface UniverseRequest {
  number: number;
  name: string | null;
  artnet: ArtNet;
  mode: DeliveryMode;
  ipAddresses: string[];
  networkInterface: string | null;
}

/** An IPv4 network of an interface of the computer Lumen runs on. */
export interface LocalNetwork {
  interfaceName: string;
  address: string;
  prefixLength: number;
  /** directed broadcast, used for ArtPoll and broadcast universes bound to this interface */
  broadcastAddress: string;
}

export interface ArtNetNode {
  ipAddress: string;
  bindIndex: number;
  shortName: string;
  longName: string;
  macAddress: string;
  portAddresses: number[];
  nodeReport: string;
  lastSeen: string;
  online: boolean;
  /** interface of this computer in the node's network, null if none */
  networkInterface: string | null;
}

export interface Position {
  x: number;
  y: number;
}

export interface Fixture {
  id: number;
  name: string;
  shortName: string | null;
  fixtureTypeId: string;
  mode: string;
  universeId: number;
  address: number;
  channelCount: number | null;
  lastAddress: number | null;
  position: Position | null;
  sortOrder: number;
}

export interface FixtureRequest {
  name: string;
  shortName: string | null;
  fixtureTypeId: string;
  mode: string;
  universeId: number;
  address: number;
  position: Position | null;
  sortOrder: number;
}

export interface PatchIssue {
  severity: 'error' | 'warning';
  fixtureIds: number[];
  universeIds: number[];
  message: string;
}

export type AttributeValues = Record<string, number>;

export interface MasterState {
  level: number;
  blackout: boolean;
}

export interface Group {
  id: number;
  name: string;
  color: string | null;
  sortOrder: number;
  fixtureIds: number[];
}

export interface GroupRequest {
  name: string;
  color: string | null;
  sortOrder: number;
  fixtureIds: number[];
}

export interface Scene {
  id: number;
  number: string;
  name: string;
  cueNote: string | null;
  autoFollowMs: number | null;
  fixtureCount: number;
  values: Record<number, AttributeValues>;
  /** presets the values come from */
  presets: PresetRefs;
  /** compositions running in the scene, in their order */
  compositions: { id: number; name: string; kind: CompositionKind }[];
  /** only played by extra keys, GO skips it */
  keyOnly: boolean;
  /** fade-in of the GO into this scene, null = the project's */
  fadeInMs: number | null;
  /** fade-out of the look before at the GO into this scene, null = the project's */
  fadeOutMs: number | null;
  /** the fade-in starts this long after GO */
  delayMs: number;
  /** own fade curve, null = the project's */
  fadeCurve: FadeCurve | null;
  /** text spoken on stage during the scene, empty if none; `**…**` marks stage directions */
  script: string;
}

export interface SceneRequest {
  number?: string | null;
  name: string;
  keyOnly?: boolean;
  cueNote: string | null;
  autoFollowMs: number | null;
  fadeInMs?: number | null;
  fadeOutMs?: number | null;
  delayMs?: number | null;
  fadeCurve?: FadeCurve | null;
  script?: string;
  record?: boolean;
}

/** What an attribute is about, e.g. to update only the colors of a scene. */
export type AttributeGroup = 'intensity' | 'color' | 'position' | 'beam' | 'other';

/** Groups a preset can be made for. */
export type PresetGroup = 'color' | 'position' | 'beam';

/** Which preset the values of a fixture come from: fixture id → attribute group → preset id. */
export type PresetRefs = Record<number, Partial<Record<AttributeGroup, number>>>;

/** Named values of one attribute group per fixture, e.g. the color “Warm”; scenes using it change with it. */
export interface Preset {
  id: number;
  name: string;
  group: PresetGroup;
  /** only attributes of the group, per fixture id */
  values: Record<number, AttributeValues>;
}

/** What “update from programmer” takes: the whole programmer, or merged (only some fixtures/groups). */
export interface RecordRequest {
  merge: boolean;
  /** when merging: only these fixtures, null = all in the programmer */
  fixtureIds?: number[] | null;
  /** when merging: only these attribute groups, empty = all */
  groups?: AttributeGroup[];
}

/** An earlier look of a scene, saved before it was replaced. */
export interface SceneRevision {
  id: number;
  createdAt: string;
  /** what replaced this look, e.g. "Update from programmer" */
  reason: string;
  fixtureCount: number;
  values: Record<number, AttributeValues>;
  presets: PresetRefs;
}

export type OperatingMode = 'setup' | 'show' | 'party';

export interface PlaybackStatus {
  available: boolean;
  /** -1 before the first scene, sceneCount at the end of the show */
  position: number;
  sceneCount: number;
  currentSceneId: number | null;
  nextSceneId: number | null;
  nextIsEnd: boolean;
  /** the running transition; its fade-in starts delayMs after GO, the fade-out at GO */
  fade: { fromSceneId: number | null; elapsedMs: number; fadeInMs: number; fadeOutMs: number; delayMs: number } | null;
  paused: boolean;
  autoFollowInMs: number | null;
  /** scene that was on stage before a server restart and can be resumed */
  resumeSceneId: number | null;
  /** assigned extra keys */
  keys: KeyStatus[];
  /** highlighted sentence of the script of the current scene (stage directions not counted), 0 after a scene change */
  scriptLine: number;
}

export type KeyMode = 'toggle' | 'flash';

export interface KeyStatus {
  slot: number;
  name: string;
  /** 'scene' if the key plays a scene */
  kind: CompositionKind | 'scene';
  mode: KeyMode;
  /** switched on (possibly still fading in) */
  on: boolean;
  /** current fade level 0–1 */
  level: number;
  /** usable in the scene on stage */
  available: boolean;
}

export interface ExtraKey {
  slot: number;
  mode: KeyMode;
  fadeMs: number;
  /** the key's own composition, or null if it plays a scene */
  composition: Composition | null;
  /** the scene the key plays, or null */
  sceneId: number | null;
}

/** Health of the light output, measured by the server every second. */
export interface OutputHealth {
  level: 'ok' | 'warning' | 'error';
  problems: string[];
  renderRateHz: number;
  targetRateHz: number;
  renderErrors: number;
  lastRenderError: string | null;
  watchdogRestarts: number;
  artNetActive: boolean;
  packetsPerSecond: number;
  sendErrorsPerSecond: number;
  universes: number;
  /** the nodes the project expects, empty while Art-Net is off */
  nodes: NodeHealth[];
}

/** online · offline (answered before, not any more) · unchecked (manual IP that never answered ArtPoll) */
export type NodeState = 'online' | 'offline' | 'unchecked';

export interface NodeHealth {
  ipAddress: string;
  name: string;
  state: NodeState;
  /** numbers of the project's universes the node receives */
  universes: number[];
  lastSeen: string | null;
}

export interface Backup {
  name: string;
  createdAt: string;
  sizeBytes: number;
}

export interface Backups {
  /** where the backup files are stored on the server */
  directory: string;
  backups: Backup[];
}

export interface AccessStatus {
  pinSet: boolean;
  /** this browser may change the setup */
  unlocked: boolean;
  /** setting: accept other devices (after a restart) */
  networkAccess: boolean;
  /** the running server accepts other devices */
  networkAccessActive: boolean;
  /** switched on in the server configuration, cannot be changed in the UI */
  networkAccessForced: boolean;
  /** addresses for tablets and phones while network access is active */
  urls: string[];
}

export type CompositionKind = 'chase' | 'color_cycle' | 'rainbow' | 'wave' | 'flicker' | 'steps';

export interface ChaseParams {
  stepMs: number;
  width: number;
  direction: 'forward' | 'backward' | 'ping_pong' | 'random';
  softness: number;
  min: number;
  max: number;
}

export interface ColorCycleParams {
  colors: string[];
  holdMs: number;
  fadeMs: number;
  spread: number;
  intensity: number;
}

export interface RainbowParams {
  cycleMs: number;
  saturation: number;
  spread: number;
  intensity: number;
}

export interface WaveParams {
  shape: 'sine' | 'triangle' | 'sawtooth' | 'square';
  periodMs: number;
  min: number;
  max: number;
  spread: number;
}

export interface FlickerParams {
  character: 'candle' | 'fire' | 'broken_tube';
  strength: number;
  speed: number;
  seed: number;
}

export interface StepsParams {
  playMode: 'loop' | 'ping_pong';
}

export type CompositionParams = ChaseParams | ColorCycleParams | RainbowParams | WaveParams | FlickerParams | StepsParams;

export interface CompositionTarget {
  groupId?: number | null;
  fixtureIds?: number[] | null;
}

export interface CompositionStep {
  holdMs: number;
  fadeMs: number;
  values: Record<number, AttributeValues>;
}

export interface Composition {
  id: number;
  /** scene of a copy, null in the library */
  sceneId: number | null;
  /** library origin of a copy */
  sourceId: number | null;
  position: number;
  name: string;
  kind: CompositionKind;
  params: CompositionParams;
  /** null for step sequences */
  target: CompositionTarget | null;
  steps: CompositionStep[] | null;
  level: number;
  speedFactor: number;
  restart: boolean;
  /** runs through once and stays at the end */
  once: boolean;
}

export interface CompositionRequest {
  name: string;
  kind: CompositionKind;
  params: Partial<CompositionParams>;
  target: CompositionTarget | null;
  steps?: CompositionStep[] | null;
  level?: number;
  speedFactor?: number;
  restart?: boolean;
  once?: boolean;
}

/** A rehearsed GO into the scene on stage; the fade-in starts delayMs after GO, the fade-out at GO. */
export interface RehearsedFade {
  /** the scene fading out, null for the dark stage */
  fromSceneId: number | null;
  elapsedMs: number;
  fadeInMs: number;
  fadeOutMs: number;
  delayMs: number;
}

export interface StagePreviewStatus {
  compositionId: number | null;
  sceneId: number | null;
  /** running rehearsed fade into sceneId, null if none or ended */
  fade: RehearsedFade | null;
}

/** A built-in color palette of party mode. */
export interface PartyPalette {
  id: string;
  label: string;
  /** display color #rrggbb */
  color: string;
}

export interface PartyButtonState {
  id: number;
  name: string;
  mode: KeyMode;
  on: boolean;
}

/** Live state of party mode, pushed as 'party'. */
export interface PartyStatus {
  bpm: number;
  beatMs: number;
  /** time since the last beat when the status was made */
  msSinceBeat: number;
  /** palette and intensity of the fixtures party mode has touched */
  fixtures: Record<number, { palette: string | null; intensity: number }>;
  buttons: PartyButtonState[];
  palettes: PartyPalette[];
}

/** A party button with its values (setup tab). */
export interface PartyButton {
  id: number;
  position: number;
  name: string;
  mode: KeyMode;
  fixtureCount: number;
  values: Record<number, AttributeValues>;
}
