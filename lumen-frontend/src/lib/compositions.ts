import type {
  ChaseParams,
  Composition,
  CompositionKind,
  CompositionParams,
  Fixture,
  Mode,
  WaveParams,
} from './types';

export const KINDS: { kind: CompositionKind; label: string; icon: string; description: string }[] = [
  { kind: 'chase', label: 'Chase', icon: '⇉', description: 'A light running along the fixtures' },
  { kind: 'color_cycle', label: 'Color cycle', icon: '◐', description: 'Steps through colors with fades' },
  { kind: 'rainbow', label: 'Rainbow', icon: '🌈', description: 'Runs through all hues' },
  { kind: 'wave', label: 'Wave / pulse', icon: '∿', description: 'Intensity rising and falling' },
  { kind: 'flicker', label: 'Flicker', icon: '🔥', description: 'Candle, fire or a broken tube' },
  { kind: 'steps', label: 'Step sequence', icon: '▦', description: 'Your own looks, recorded from the programmer' },
];

export function kindLabel(kind: CompositionKind): string {
  return KINDS.find((k) => k.kind === kind)?.label ?? kind;
}

export function kindIcon(kind: CompositionKind | 'scene'): string {
  if (kind === 'scene') return '▣';
  return KINDS.find((k) => k.kind === kind)?.icon ?? '✦';
}

/** Parameters a new composition of the kind starts with (the server uses the same defaults). */
export function defaultParams(kind: CompositionKind): CompositionParams {
  switch (kind) {
    case 'chase':
      return { stepMs: 500, width: 1, direction: 'forward', softness: 0, min: 0, max: 1 };
    case 'color_cycle':
      return { colors: ['#ff0000', '#0000ff'], holdMs: 2000, fadeMs: 1000, spread: 0, intensity: 1 };
    case 'rainbow':
      return { cycleMs: 10000, saturation: 1, spread: 0, intensity: 1 };
    case 'wave':
      return { shape: 'sine', periodMs: 2000, min: 0, max: 1, spread: 0 };
    case 'flicker':
      return { character: 'candle', strength: 0.5, speed: 1, seed: Math.floor(Math.random() * 100000) };
    case 'steps':
      return { playMode: 'loop' };
  }
}

/** Kinds that set the color: they need fixtures with red/green/blue. */
export function setsColor(kind: CompositionKind): boolean {
  return kind === 'color_cycle' || kind === 'rainbow';
}

/** Kinds that set the intensity. */
export function setsIntensity(kind: CompositionKind): boolean {
  return kind !== 'color_cycle' && kind !== 'rainbow';
}

/**
 * Light changes faster than about 3 times per second can trigger photosensitive epilepsy; theatres often have to
 * announce them.
 */
export function isFast(composition: Pick<Composition, 'kind' | 'params' | 'speedFactor'>): boolean {
  const speed = composition.speedFactor || 1;
  switch (composition.kind) {
    case 'chase': {
      const p = composition.params as ChaseParams;
      return p.max - p.min > 0.3 && p.stepMs / speed < 333;
    }
    case 'wave': {
      const p = composition.params as WaveParams;
      return p.max - p.min > 0.3 && p.periodMs / speed < 333;
    }
    case 'flicker':
      return (composition.params as { character: string }).character === 'broken_tube';
    default:
      return false;
  }
}

/** Fixtures of the target that cannot show what the composition does (e.g. a color cycle on a dimmer). */
export function unsuitableFixtures(kind: CompositionKind, fixtureIds: number[], modes: Map<number, Mode>): number[] {
  return fixtureIds.filter((id) => {
    const functions = new Set(modes.get(id)?.channels.map((c) => c.function) ?? []);
    if (setsColor(kind)) return !(functions.has('red') && functions.has('green') && functions.has('blue'));
    return !functions.has('dimmer');
  });
}

/** Ordered fixture ids a composition runs on. */
export function targetFixtures(composition: Composition, groups: { id: number; fixtureIds: number[] }[]): number[] {
  if (composition.kind === 'steps') {
    const ids = new Set<number>();
    for (const step of composition.steps ?? []) Object.keys(step.values).forEach((id) => ids.add(Number(id)));
    return [...ids].sort((a, b) => a - b);
  }
  const target = composition.target;
  if (target?.groupId != null) return groups.find((g) => g.id === target.groupId)?.fixtureIds ?? [];
  return target?.fixtureIds ?? [];
}

/**
 * Pairs of compositions in a scene that set the same thing on the same fixture; the later one wins.
 * Returns messages for the editor.
 */
export function overlaps(
  compositions: Composition[],
  groups: { id: number; fixtureIds: number[] }[],
  fixtures: Fixture[],
): string[] {
  const names = new Map(fixtures.map((f) => [f.id, f.name]));
  const messages: string[] = [];
  for (let i = 0; i < compositions.length; i++) {
    for (let j = i + 1; j < compositions.length; j++) {
      const a = compositions[i];
      const b = compositions[j];
      const sameAttributes =
        (setsColor(a.kind) && setsColor(b.kind)) || (setsIntensity(a.kind) && setsIntensity(b.kind));
      if (!sameAttributes) continue;
      const shared = targetFixtures(a, groups).filter((id) => targetFixtures(b, groups).includes(id));
      if (shared.length === 0) continue;
      const list = shared.slice(0, 3).map((id) => names.get(id) ?? `#${id}`).join(', ');
      const more = shared.length > 3 ? ` and ${shared.length - 3} more` : '';
      messages.push(`"${b.name}" overrides "${a.name}" on ${list}${more}`);
    }
  }
  return messages;
}
