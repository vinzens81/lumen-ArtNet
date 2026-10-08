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
  /** attribute id, e.g. `red`, `red@3` (head 3) or `generic:Speed` */
  attribute: string;
  /** 0 = the whole fixture, 1… = a head (segment, pixel) of a multi-pixel fixture */
  head: number;
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
  /** number of heads of a multi-pixel fixture, 0 without */
  heads: number;
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

/** A fixture imported from the Open Fixture Library. */
export interface OflImportResult {
  fixtureType: FixtureType;
  /** the new profile file, relative to the library */
  source: string;
  /** what got lost or simplified – check before using it on stage */
  warnings: string[];
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
  /** move in dark: dim-out of the heads that move, unless a scene has its own */
  dimOutMs: number;
  /** move in dark: time the heads get to move while dark */
  travelMs: number;
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
  /** null = 500 ms */
  dimOutMs: number | null;
  /** null = 1500 ms */
  travelMs: number | null;
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

export type StageShapeKind = 'auto' | 'spot' | 'bar' | 'strip';

/** How a fixture is drawn in the stage view. */
export interface StageShape {
  kind: StageShapeKind;
  /** clockwise in degrees (0–359), 0 = across the stage */
  rotation: number;
  /** bar/strip length as a fraction of the stage width (0.02–1) */
  length: number;
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
  stageShape: StageShape;
  /** allowed range of pan and tilt in the show (not the programmer), null = no limit */
  limits: PositionLimits | null;
}

/** Allowed range of pan and tilt of a fixture in DMX values (0–255, inclusive). */
export interface PositionLimits {
  panMin: number;
  panMax: number;
  tiltMin: number;
  tiltMax: number;
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
  /** missing = drawn automatically */
  stageShape?: StageShape;
  /** missing or null = no limit */
  limits?: PositionLimits | null;
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

/** A composition used in a scene, with the settings of this use. */
export interface SceneComposition {
  compositionId: number;
  /** 0–1 */
  level: number;
  /** 2 = twice as fast */
  speedFactor: number;
  /** start from the beginning at GO, even if it already runs in the scene before */
  restart: boolean;
}

/** A look used directly in a scene: it stays as it is. */
export interface SceneLook {
  lookId: number;
  /** 0–1 */
  level: number;
}

/** A move used in a scene: where its pan/tilt fixtures point. */
export interface SceneMove {
  moveId: number;
}

/** The light of a part of a scene: after the light marker `marker` of the script. */
export interface SceneSection {
  marker: string;
  compositions: SceneComposition[];
  looks: SceneLook[];
  /** on the same fixture a later move wins */
  moves: SceneMove[];
}

/** An extra key of a scene: switches a composition or a move on and off while the scene is on stage. */
export interface SceneKey {
  /** 1–8 */
  slot: number;
  /** exactly one of composition and move */
  compositionId: number | null;
  moveId: number | null;
  mode: KeyMode;
  fadeMs: number;
}

/** The third level: a set of compositions that run side by side from GO, plus the scene's extra keys. */
export interface Scene {
  id: number;
  number: string;
  name: string;
  cueNote: string | null;
  autoFollowMs: number | null;
  /** fade-in of the GO into this scene, null = the project's */
  fadeInMs: number | null;
  /** fade-out of the look before at the GO into this scene, null = the project's */
  fadeOutMs: number | null;
  /** the fade-in starts this long after GO */
  delayMs: number;
  /** own fade curve, null = the project's */
  fadeCurve: FadeCurve | null;
  /** heads that point somewhere else dim out, move while dark and dim in */
  moveInDark: boolean;
  /** dim-out of those heads, null = the project's */
  dimOutMs: number | null;
  /** time they get to move while dark, null = the project's */
  travelMs: number | null;
  /** text spoken on stage during the scene, empty if none; `**…**` marks stage directions */
  script: string;
  /** in order: on the same attribute a later one wins (intensity: the highest) */
  compositions: SceneComposition[];
  keys: SceneKey[];
  /** looks used directly at the start of the scene, applied before the compositions */
  looks: SceneLook[];
  /** moves at the start of the scene: where the pan/tilt fixtures point */
  moves: SceneMove[];
  /** the light of the parts after the light markers of the script; a marker without one is dark */
  sections: SceneSection[];
}

export interface SceneRequest {
  number?: string | null;
  name: string;
  cueNote: string | null;
  autoFollowMs: number | null;
  fadeInMs?: number | null;
  fadeOutMs?: number | null;
  delayMs?: number | null;
  fadeCurve?: FadeCurve | null;
  moveInDark?: boolean;
  dimOutMs?: number | null;
  travelMs?: number | null;
  script?: string;
  /** the quick way: the programmer becomes the scene's first composition */
  record?: boolean;
}

/** The first level: fixed values per fixture, recorded from the programmer. */
export interface Look {
  id: number;
  name: string;
  /** the composition the look belongs to (a recorded or generated step), null = shared */
  owner: number | null;
  fixtureCount: number;
  values: Record<number, AttributeValues>;
  /** presets the values come from */
  presets: PresetRefs;
}

/** What an attribute is about, e.g. to update only the colors of a scene. */
export type AttributeGroup = 'intensity' | 'color' | 'position' | 'beam' | 'other';

/** Groups a preset can be made for; a stored position is a move with one point. */
export type PresetGroup = 'color' | 'beam';

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

/** An earlier content of a look, saved before it was replaced. */
export interface LookRevision {
  id: number;
  createdAt: string;
  /** what replaced this look, e.g. "Update from programmer" */
  reason: string;
  fixtureCount: number;
  values: Record<number, AttributeValues>;
  presets: PresetRefs;
}

/** A fader of the show, e.g. the house lights, with its level 0–1. */
export interface FaderStatus {
  slot: number;
  name: string;
  level: number;
}

/** Which composition a fader plays. */
export interface FaderSetup {
  slot: number;
  /** null = the composition's name */
  name: string | null;
  compositionId: number;
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
  fade: {
    fromSceneId: number | null; elapsedMs: number; fadeInMs: number; fadeOutMs: number; delayMs: number;
    /** to another part of the scene on stage (a light marker), not to another scene */
    withinScene: boolean;
  } | null;
  paused: boolean;
  autoFollowInMs: number | null;
  /** scene that was on stage before a server restart and can be resumed */
  resumeSceneId: number | null;
  /** extra keys of the scene on stage */
  keys: KeyStatus[];
  /** assigned faders with their level */
  faders: FaderStatus[];
  /** highlighted sentence of the script of the current scene (stage directions not counted), 0 after a scene change */
  scriptLine: number;
  /** part of the current scene whose light is on stage: 0 = the start, n = after the n-th light marker */
  section: number;
}

export type KeyMode = 'toggle' | 'flash';

/** A key of the scene on stage; its name is the name of its composition. */
export interface KeyStatus {
  slot: number;
  name: string;
  mode: KeyMode;
  /** switched on (possibly still fading in) */
  on: boolean;
  /** current fade level 0–1 */
  level: number;
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

/** Effects a generator makes the steps of a composition from. */
export type EffectKind = 'chase' | 'color_cycle' | 'rainbow' | 'wave' | 'flicker';

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

export type EffectParams = ChaseParams | ColorCycleParams | RainbowParams | WaveParams | FlickerParams;

/** The fixtures an effect runs on: a group (its members when generating) or fixtures in order. */
export interface EffectTarget {
  groupId?: number | null;
  fixtureIds?: number[] | null;
}

/** What the steps of a composition were generated from. */
export interface Generator {
  kind: EffectKind;
  params: Partial<EffectParams>;
  target: EffectTarget;
  /** every head of a multi-pixel fixture is an element of its own */
  perHead: boolean;
}

export type PlayMode = 'loop' | 'ping_pong' | 'once';
export type IntensityMode = 'absolute' | 'relative';

/**
 * A step plays a look; its own fade/hold override the composition's (null = the composition's). A parallel step starts
 * together with the step before it: they form one row with the times of the row's first step.
 */
export interface CompositionStep {
  lookId: number;
  fadeMs: number | null;
  holdMs: number | null;
  parallel: boolean;
}

/** The second level: 1–n steps playing looks. Shared by every scene it is in. */
export interface Composition {
  id: number;
  name: string;
  fadeMs: number;
  holdMs: number;
  playMode: PlayMode;
  /** absolute sets the intensity; relative scales what lies below in the scene (a flicker) */
  intensityMode: IntensityMode;
  generator: Generator | null;
  steps: CompositionStep[];
}

export interface CompositionRequest {
  name: string;
  fadeMs?: number;
  holdMs?: number;
  playMode?: PlayMode;
  intensityMode?: IntensityMode;
  steps?: CompositionStep[];
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
  moveId: number | null;
  sceneId: number | null;
  /** running rehearsed fade into sceneId, null if none or ended */
  fade: RehearsedFade | null;
}

/**
 * A field of the party matrix (rows and columns from 0): plays a scene, a composition or a move, or – with none –
 * only shows its label.
 */
export interface PartyCell {
  row: number;
  column: number;
  /** null = the name of what it plays */
  label: string | null;
  sceneId: number | null;
  compositionId: number | null;
  moveId: number | null;
  /** fill #rrggbb */
  color: string;
  /** border #rrggbb */
  borderColor: string;
  mode: KeyMode;
  /** fields of the same group exclude each other */
  group: string | null;
}

/** The party mode of a project: a grid of fields, lines above the rows in `separators`. */
export interface PartyMatrix {
  rows: number;
  columns: number;
  separators: number[];
  /** only the fields that are not empty */
  cells: PartyCell[];
}

/** Live state of party mode, pushed as 'party': the fields that are on. */
export interface PartyStatus {
  on: { row: number; column: number }[];
}

/** A point of a figure in DMX values: x = pan, y = tilt, 0–255 (decimals for a finer position). */
export interface FigurePoint {
  x: number;
  y: number;
}

/** A shape in the pan/tilt grid, drawn without fixtures; one point = a fixed position. */
export interface Figure {
  id: number;
  name: string;
  /** after the last point back to the first */
  closed: boolean;
  points: FigurePoint[];
}

export interface FigureRequest {
  name: string;
  closed: boolean;
  points: FigurePoint[];
}

export type MoveRepeat = 'loop' | 'once' | 'ping_pong';
export type OnceStart = 'sequential' | 'together';

/** A fixture of a move with its own corrections. */
export interface MoveFixture {
  fixtureId: number;
  /** own shift in DMX values, on top of the shift of the move */
  shiftX: number;
  shiftY: number;
  /** mirrored around the middle of the figure */
  mirrorX: boolean;
  mirrorY: boolean;
  /** follows the figure the other way round */
  reverse: boolean;
}

/** A figure laid onto pan/tilt fixtures: pan and tilt in the show come only from moves. */
export interface Move {
  id: number;
  name: string;
  figureId: number;
  /** in order: the order decides the offset */
  fixtures: MoveFixture[];
  /** percent */
  sizeX: number;
  sizeY: number;
  /** DMX values */
  shiftX: number;
  shiftY: number;
  /** degrees, counter-clockwise */
  rotation: number;
  /** one round of the figure */
  durationMs: number;
  repeat: MoveRepeat;
  /** points by which each further fixture starts later */
  stepOffset: number;
  onceStart: OnceStart;
}

export type MoveRequest = Omit<Move, 'id'>;
