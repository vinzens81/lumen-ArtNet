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
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ProjectRequest {
  name: string;
  venue: string | null;
  fadeInMs: number;
  fadeOutMs: number;
}

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
}

export interface UniverseRequest {
  number: number;
  name: string | null;
  artnet: ArtNet;
  mode: DeliveryMode;
  ipAddresses: string[];
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
}

export interface SceneRequest {
  number?: string | null;
  name: string;
  cueNote: string | null;
  autoFollowMs: number | null;
  record?: boolean;
}

export type OperatingMode = 'setup' | 'show';

export interface PlaybackStatus {
  available: boolean;
  /** -1 before the first scene, sceneCount at the end of the show */
  position: number;
  sceneCount: number;
  currentSceneId: number | null;
  nextSceneId: number | null;
  nextIsEnd: boolean;
  fade: { fromSceneId: number | null; elapsedMs: number; fadeInMs: number; fadeOutMs: number } | null;
  paused: boolean;
  autoFollowInMs: number | null;
  /** scene that was on stage before a server restart and can be resumed */
  resumeSceneId: number | null;
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
