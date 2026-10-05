import type { Group, PartyStatus } from './types';

/** Fixtures of the selected groups, each once, in group order; `all` selects every fixture. */
export function selectedFixtures(
  groups: Group[],
  selectedGroupIds: Set<number>,
  all: boolean,
  allFixtureIds: number[],
): number[] {
  if (all) return [...allFixtureIds];
  const result = new Set<number>();
  for (const group of groups) {
    if (selectedGroupIds.has(group.id)) group.fixtureIds.forEach((id) => result.add(id));
  }
  return [...result];
}

/**
 * Where the beat stands, counted from the status: `beat` is the beat within a bar of four (0–3), `phase` runs from
 * 0 at the beat to 1 just before the next one.
 */
export function beatAt(msSinceBeat: number, beatMs: number, msSinceStatus: number): { beat: number; phase: number } {
  if (beatMs <= 0) return { beat: 0, phase: 0 };
  const beats = (msSinceBeat + Math.max(0, msSinceStatus)) / beatMs;
  const whole = Math.floor(beats);
  return { beat: ((whole % 4) + 4) % 4, phase: beats - whole };
}

/** The intensity all given fixtures share, or null if they differ (or none is given). */
export function commonIntensity(fixtureIds: number[], status: PartyStatus | null): number | null {
  if (fixtureIds.length === 0) return null;
  const levels = new Set(fixtureIds.map((id) => status?.fixtures[id]?.intensity ?? 0));
  return levels.size === 1 ? [...levels][0] : null;
}

/** The palette all given fixtures share, or null. */
export function commonPalette(fixtureIds: number[], status: PartyStatus | null): string | null {
  if (fixtureIds.length === 0) return null;
  const palettes = new Set(fixtureIds.map((id) => status?.fixtures[id]?.palette ?? null));
  return palettes.size === 1 ? [...palettes][0] : null;
}

/** Keyboard key of the button at this place in the grid: 1–9, the others have none. */
export function buttonKey(index: number): string | null {
  return index < 9 ? String(index + 1) : null;
}
