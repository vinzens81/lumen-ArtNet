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

export interface Universe {
  id: number;
  number: number;
  name: string | null;
  displayName: string;
  artnet: ArtNet;
  portAddress: number;
  target: string;
}

export interface UniverseRequest {
  number: number;
  name: string | null;
  artnet: ArtNet;
  target: string;
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
