import type { Mode, QuickSetupRequest, QuickSetupResult } from './types';

/** What quick setup makes. */
export interface QuickSetupOptions {
  intensity: boolean;
  colors: boolean;
  moves: boolean;
  partyButtons: boolean;
  gradients: boolean;
}

export const ALL_OPTIONS: QuickSetupOptions = {
  intensity: true, colors: true, moves: true, partyButtons: true, gradients: true,
};

/**
 * What the fixtures can do: colors need mixing (red/green/blue or cyan/magenta/yellow) or a color wheel with colored
 * slots, a color gradient needs mixing, moves need pan and tilt.
 */
export interface Abilities {
  colors: boolean;
  gradient: boolean;
  moves: boolean;
}

export function abilitiesOf(modes: (Mode | undefined)[]): Abilities {
  const has = (mode: Mode | undefined, ...functions: string[]) =>
    functions.every((f) => mode?.channels.some((c) => c.function === f) ?? false);
  const mixes = (mode: Mode | undefined) => has(mode, 'red', 'green', 'blue') || has(mode, 'cyan', 'magenta', 'yellow');
  return {
    colors: modes.some((mode) => mixes(mode) || hasColoredWheel(mode)),
    gradient: modes.some(mixes),
    moves: modes.some((mode) => has(mode, 'pan', 'tilt')),
  };
}

/** A color wheel with at least one slot of its own color (not a transition like "Blue → cyan"). */
function hasColoredWheel(mode: Mode | undefined): boolean {
  return mode?.channels.some((c) => c.function === 'color-wheel'
    && c.ranges.some((r) => r.color !== null && !r.label.includes('→'))) ?? false;
}

/** The options, without what none of the fixtures can do. */
export function fitting(options: QuickSetupOptions, abilities: Abilities): QuickSetupOptions {
  return { ...options, colors: options.colors && abilities.colors, moves: options.moves && abilities.moves };
}

/** Whether there is something to make: party rows alone need buttons or gradients to fill them. */
export function makesSomething(options: QuickSetupOptions): boolean {
  return options.intensity || options.colors || options.moves || options.gradients;
}

export function quickSetupRequest(fixtureIds: number[], name: string, options: QuickSetupOptions):
  QuickSetupRequest {
  return { fixtureIds, name: name.trim() || null, ...options };
}

/** A short line for a toast, e.g. "8 looks, 3 moves and 2 party rows for PAR". */
export function resultSummary(result: QuickSetupResult): string {
  const parts = [
    count(result.looks.length, 'look'),
    count(result.presets.length, 'palette color'),
    count(result.moves.length, 'move'),
    count(result.partyRows.length, 'party row'),
  ].filter((part) => part !== null);
  const made = parts.length === 0 ? 'Nothing'
    : parts.length === 1 ? parts[0] : `${parts.slice(0, -1).join(', ')} and ${parts.at(-1)}`;
  return `${made} for "${result.name}"`;
}

function count(n: number, word: string): string | null {
  return n === 0 ? null : `${n} ${word}${n === 1 ? '' : 's'}`;
}

const STORAGE_KEY = 'lumen.quickSetup';

/** The choice last made on this device: whether to set up while patching, and what. */
export function rememberedChoice(): { enabled: boolean; options: QuickSetupOptions } {
  try {
    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? 'null');
    if (stored && typeof stored.enabled === 'boolean') {
      return { enabled: stored.enabled, options: { ...ALL_OPTIONS, ...stored.options } };
    }
  } catch {
    // storage blocked or broken: start with the defaults
  }
  return { enabled: false, options: { ...ALL_OPTIONS } };
}

export function rememberChoice(enabled: boolean, options: QuickSetupOptions) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ enabled, options }));
  } catch {
    // only a convenience
  }
}
