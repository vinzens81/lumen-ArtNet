import type { Mode, QuickSetupRequest, QuickSetupResult } from './types';

/** What quick setup makes. */
export interface QuickSetupOptions {
  intensity: boolean;
  colors: boolean;
  moves: boolean;
  partyButtons: boolean;
  gradients: boolean;
  /** where the party rows go: a page (from 0), a new page, or null = the first page with room */
  page: number | 'new' | null;
}

export const ALL_OPTIONS: QuickSetupOptions = {
  intensity: true, colors: true, moves: true, partyButtons: true, gradients: true, page: null,
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

/** Whether the setup adds fields to the party matrix – only then the page matters. */
export function makesPartyRows(options: QuickSetupOptions): boolean {
  return options.partyButtons || options.gradients;
}

/**
 * The options, without what none of the fixtures can do; a page that no longer exists (e.g. remembered from another
 * project) becomes "the first page with room".
 */
export function fitting(options: QuickSetupOptions, abilities: Abilities, pageCount = Infinity): QuickSetupOptions {
  const page = typeof options.page === 'number' && options.page >= pageCount ? null : options.page;
  return { ...options, colors: options.colors && abilities.colors, moves: options.moves && abilities.moves, page };
}

/** Whether there is something to make: party rows alone need buttons or gradients to fill them. */
export function makesSomething(options: QuickSetupOptions): boolean {
  return options.intensity || options.colors || options.moves || options.gradients;
}

export function quickSetupRequest(fixtureIds: number[], name: string, options: QuickSetupOptions):
  QuickSetupRequest {
  const { page, ...switches } = options;
  return { fixtureIds, name: name.trim() || null, ...switches, partyPage: typeof page === 'number' ? page : null,
    newPartyPage: page === 'new' };
}

/** A short line for a toast, e.g. "8 looks, 3 moves and 2 party rows for PAR" (with the page if not the first). */
export function resultSummary(result: QuickSetupResult): string {
  const parts = [
    count(result.looks.length, 'look'),
    count(result.presets.length, 'palette color'),
    count(result.moves.length, 'move'),
    count(result.partyRows.length, 'party row'),
  ].filter((part) => part !== null);
  // rows on another page than the first are easy to miss
  if (result.partyPage !== null && result.partyPage > 0 && result.partyRows.length > 0) {
    parts[parts.length - 1] += ` on page ${result.partyPage + 1}`;
  }
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
      const options = { ...ALL_OPTIONS, ...stored.options };
      const page = options.page;
      if (page !== null && page !== 'new' && !(Number.isInteger(page) && page >= 0)) options.page = null;
      return { enabled: stored.enabled, options };
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
