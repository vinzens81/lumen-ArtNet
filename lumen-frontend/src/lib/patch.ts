import type { Fixture, FixtureRequest, Mode } from './types';

export const UNIVERSE_SIZE = 512;

/** Channels occupied by a fixture; unknown types count as one channel. */
export function footprint(fixture: Pick<Fixture, 'channelCount'>): number {
  return fixture.channelCount ?? 1;
}

/** For every channel (index 0 = channel 1) the ids of the fixtures occupying it. */
export function occupancy(fixtures: Fixture[]): number[][] {
  const cells: number[][] = Array.from({ length: UNIVERSE_SIZE }, () => []);
  for (const fixture of fixtures) {
    const last = Math.min(fixture.address + footprint(fixture) - 1, UNIVERSE_SIZE);
    for (let channel = fixture.address; channel <= last; channel++) {
      cells[channel - 1].push(fixture.id);
    }
  }
  return cells;
}

/**
 * First start address at or after {@code from} where {@code count} channels are free, or null if the universe
 * has no gap that large.
 */
export function nextFreeAddress(fixtures: Fixture[], count: number, from = 1, ignoreId?: number): number | null {
  const cells = occupancy(fixtures.filter((f) => f.id !== ignoreId));
  for (let start = Math.max(from, 1); start + count - 1 <= UNIVERSE_SIZE; start++) {
    let free = true;
    for (let channel = start; channel < start + count; channel++) {
      if (cells[channel - 1].length > 0) {
        free = false;
        start = channel; // continue after the occupied channel
        break;
      }
    }
    if (free) {
      return start;
    }
  }
  return null;
}

/** Names for several fixtures added at once: "PAR" → "PAR 1", "PAR 2", … */
export function numberedNames(base: string, count: number, firstNumber = 1): string[] {
  if (count <= 1) {
    return [base];
  }
  return Array.from({ length: count }, (_, i) => `${base} ${firstNumber + i}`);
}

/**
 * Requests for patching {@code count} fixtures of the same type back to back from {@code address}. Computed once
 * before the first request, so nothing that changes while they are sent (e.g. a reloaded patch) shifts them.
 */
export function plannedFixtures(
  base: Omit<FixtureRequest, 'name' | 'address'> & { name: string },
  address: number,
  channels: number,
  count: number,
): FixtureRequest[] {
  const names = numberedNames(base.name.trim(), Math.max(1, count));
  return names.map((name, i) => ({
    ...base,
    name,
    shortName: names.length === 1 ? base.shortName : null,
    address: address + i * Math.max(1, channels),
  }));
}

const LOCATE_FUNCTIONS = new Set(['dimmer', 'red', 'green', 'blue', 'white', 'warm-white', 'cold-white', 'amber', 'lime']);

/** Programmer values that make a fixture light up bright white to find it on stage. */
export function locateValues(mode: Mode): Record<string, number> {
  const values: Record<string, number> = { dimmer: 1 };
  for (const channel of mode.channels) {
    if (LOCATE_FUNCTIONS.has(channel.function)) {
      values[channel.function] = 1;
    }
  }
  return values;
}

/** Stable, well distributed color for a fixture id (golden angle). */
export function fixtureHue(id: number): number {
  return Math.round((id * 137.508) % 360);
}

export interface ChannelOwner {
  fixture: Fixture;
  /** channel name from the profile, with "fine" for the second byte of 16-bit channels */
  channel: string;
}

/** Which fixture and profile channel each DMX channel (index 0 = channel 1) of a universe belongs to. */
export function channelOwners(fixtures: Fixture[], modes: Map<number, Mode>): (ChannelOwner | null)[] {
  const owners: (ChannelOwner | null)[] = Array.from({ length: UNIVERSE_SIZE }, () => null);
  for (const fixture of fixtures) {
    const mode = modes.get(fixture.id);
    if (!mode) continue;
    for (const channel of mode.channels) {
      for (let byte = 0; byte < channel.dmxChannelCount; byte++) {
        const index = fixture.address - 1 + channel.offset + byte;
        if (index < UNIVERSE_SIZE && owners[index] === null) {
          owners[index] = { fixture, channel: byte === 0 ? channel.name : `${channel.name} fine` };
        }
      }
    }
  }
  return owners;
}
