import type {
  AttributeValues,
  ChaseParams,
  Composition,
  CompositionStep,
  EffectKind,
  EffectParams,
  Fixture,
  Generator,
  Look,
  Mode,
  Scene,
  WaveParams,
} from './types';

export const EFFECTS: { kind: EffectKind; label: string; icon: string; description: string }[] = [
  { kind: 'chase', label: 'Chase', icon: '⇉', description: 'A light running along the fixtures' },
  { kind: 'color_cycle', label: 'Color cycle', icon: '◐', description: 'Steps through colors with fades' },
  { kind: 'rainbow', label: 'Rainbow', icon: '🌈', description: 'Runs through all hues' },
  { kind: 'wave', label: 'Wave / pulse', icon: '∿', description: 'Intensity rising and falling' },
  { kind: 'flicker', label: 'Flicker', icon: '🔥', description: 'Candle, fire or a broken tube' },
];

export function effectLabel(kind: EffectKind): string {
  return EFFECTS.find((e) => e.kind === kind)?.label ?? kind;
}

/** Icon of a composition: its effect, a static look (one step) or a step sequence. */
export function compositionIcon(composition: Pick<Composition, 'generator' | 'steps'>): string {
  if (composition.generator) return EFFECTS.find((e) => e.kind === composition.generator!.kind)?.icon ?? '✦';
  return composition.steps.length <= 1 ? '▣' : '▦';
}

/** Parameters a new effect starts with (the server uses the same defaults). */
export function defaultParams(kind: EffectKind): EffectParams {
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
  }
}

/** Effects that set the color: they need fixtures with red/green/blue. */
export function setsColor(kind: EffectKind): boolean {
  return kind === 'color_cycle' || kind === 'rainbow';
}

/** Fixtures of the target that cannot show what the effect does (e.g. a color cycle on a dimmer). */
export function unsuitableFixtures(kind: EffectKind, fixtureIds: number[], modes: Map<number, Mode>): number[] {
  return fixtureIds.filter((id) => {
    const functions = new Set(modes.get(id)?.channels.map((c) => c.function) ?? []);
    if (setsColor(kind)) return !(functions.has('red') && functions.has('green') && functions.has('blue'));
    return !functions.has('dimmer');
  });
}

/** Ordered fixture ids an effect runs on. */
export function generatorFixtures(generator: Pick<Generator, 'target'>, groups: { id: number; fixtureIds: number[] }[]): number[] {
  const target = generator.target;
  if (target.groupId != null) return groups.find((g) => g.id === target.groupId)?.fixtureIds ?? [];
  return target.fixtureIds ?? [];
}

/** Fade and hold of a step: its own, otherwise the composition's. */
export function stepTimes(composition: Pick<Composition, 'fadeMs' | 'holdMs'>, step: { fadeMs: number | null; holdMs: number | null }) {
  return { fadeMs: step.fadeMs ?? composition.fadeMs, holdMs: step.holdMs ?? composition.holdMs };
}

/** Step indexes grouped into rows: a parallel step joins the row of the step before it. */
export function stepRows(steps: Pick<CompositionStep, 'parallel'>[]): number[][] {
  const rows: number[][] = [];
  steps.forEach((step, i) => {
    if (step.parallel && rows.length > 0) rows[rows.length - 1].push(i);
    else rows.push([i]);
  });
  return rows;
}

/** The rows' first steps: they carry the times of their row. */
export function rowLeaders<T extends Pick<CompositionStep, 'parallel'>>(steps: T[]): T[] {
  return stepRows(steps).map((row) => steps[row[0]]);
}

/** Where a step starts: together with a row (row 0 = at the begin), or in a new row right after a row. */
export type StepStart = { with: number } | { after: number };

/** The start of the step at the index, as the editor shows it. */
export function startOf(steps: Pick<CompositionStep, 'parallel'>[], index: number): StepStart {
  const rows = stepRows(steps);
  const row = rows.findIndex((r) => r.includes(index));
  return steps[index].parallel || row === 0 ? { with: row } : { after: row - 1 };
}

/**
 * Moves a step to start with a row or after a row (rows as before the move). The first step of every row is not
 * parallel and keeps its times; the others lose theirs, they play with the times of their row.
 */
export function moveStart<T extends CompositionStep>(steps: T[], index: number, start: StepStart): T[] {
  const rows = stepRows(steps).map((row) => row.map((i) => steps[i]));
  const moving = steps[index];
  const targetRow = 'with' in start ? start.with : start.after;
  const anchor = rows[targetRow];
  if (!anchor) return steps;
  const remaining = rows.map((row) => row.filter((s) => s !== moving));
  const target = remaining[targetRow];
  if ('with' in start) {
    target.push(moving);
  } else {
    remaining.splice(targetRow + 1, 0, [moving]);
  }
  return remaining.filter((row) => row.length > 0).flatMap((row) => row.map((step, i) => (i === 0
    ? { ...step, parallel: false }
    : { ...step, parallel: true, fadeMs: null, holdMs: null })));
}

/** Time once through all steps (ping-pong: there and back); parallel steps take the time of their row. */
export function cycleMs(composition: Pick<Composition, 'fadeMs' | 'holdMs' | 'steps' | 'playMode'>): number {
  const durations = rowLeaders(composition.steps).map((step) => {
    const t = stepTimes(composition, step);
    return t.fadeMs + t.holdMs;
  });
  const once = durations.reduce((a, b) => a + b, 0);
  if (composition.playMode !== 'ping_pong' || durations.length < 3) return once;
  return once + durations.slice(1, -1).reduce((a, b) => a + b, 0);
}

/**
 * Light changes faster than about 3 times per second can trigger photosensitive epilepsy; theatres often have to
 * announce them.
 */
export function isFast(composition: Pick<Composition, 'generator' | 'steps' | 'fadeMs' | 'holdMs'>, speedFactor = 1): boolean {
  const generator = composition.generator;
  const speed = speedFactor || 1;
  if (generator?.kind === 'chase') {
    const p = generator.params as ChaseParams;
    return (p.max ?? 1) - (p.min ?? 0) > 0.3 && (p.stepMs ?? 500) / speed < 333;
  }
  if (generator?.kind === 'wave') {
    const p = generator.params as WaveParams;
    return (p.max ?? 1) - (p.min ?? 0) > 0.3 && (p.periodMs ?? 2000) / speed < 333;
  }
  if (generator?.kind === 'flicker') return (generator.params as { character?: string }).character === 'broken_tube';
  if (generator) return false;
  const leaders = rowLeaders(composition.steps);
  return leaders.length > 1 && leaders.some((step) => {
    const t = stepTimes(composition, step);
    return (t.fadeMs + t.holdMs) / speed < 333;
  });
}

/** Looks by id. */
export function lookMap(looks: Look[]): Map<number, Look> {
  return new Map(looks.map((l) => [l.id, l]));
}

/** Fixtures the steps of a composition set, in id order. */
export function compositionFixtures(composition: Pick<Composition, 'steps'>, looks: Map<number, Look>): number[] {
  const ids = new Set<number>();
  for (const step of composition.steps) {
    Object.keys(looks.get(step.lookId)?.values ?? {}).forEach((id) => ids.add(Number(id)));
  }
  return [...ids].sort((a, b) => a - b);
}

const COLOR_ATTRIBUTES = new Set(['red', 'green', 'blue', 'white', 'warm-white', 'cold-white', 'amber', 'lime', 'uv',
  'color-wheel', 'cto']);

/** What a composition sets per fixture: intensity and/or color. */
function setsPerFixture(composition: Composition, looks: Map<number, Look>): Map<number, Set<'intensity' | 'color'>> {
  const result = new Map<number, Set<'intensity' | 'color'>>();
  for (const step of composition.steps) {
    for (const [id, values] of Object.entries(looks.get(step.lookId)?.values ?? {})) {
      const set = result.get(Number(id)) ?? new Set();
      for (const attribute of Object.keys(values)) {
        const base = attribute.replace(/@\d+$/, '');
        if (base === 'dimmer') set.add('intensity');
        else if (COLOR_ATTRIBUTES.has(base)) set.add('color');
      }
      result.set(Number(id), set);
    }
  }
  return result;
}

/**
 * Pairs of compositions in a scene that set the color of the same fixture; the later one wins. (Intensities mix:
 * the highest wins, nothing gets lost.) Returns messages for the editor.
 */
export function overlaps(compositions: Composition[], looks: Map<number, Look>, fixtures: Fixture[]): string[] {
  const names = new Map(fixtures.map((f) => [f.id, f.name]));
  const sets = compositions.map((c) => setsPerFixture(c, looks));
  const messages: string[] = [];
  for (let i = 0; i < compositions.length; i++) {
    for (let j = i + 1; j < compositions.length; j++) {
      const shared = [...sets[i].keys()].filter((id) => sets[i].get(id)!.has('color') && sets[j].get(id)?.has('color'));
      if (shared.length === 0) continue;
      const list = shared.slice(0, 3).map((id) => names.get(id) ?? `#${id}`).join(', ');
      const more = shared.length > 3 ? ` and ${shared.length - 3} more` : '';
      messages.push(`"${compositions[j].name}" overrides the color of "${compositions[i].name}" on ${list}${more}`);
    }
  }
  return messages;
}

/**
 * Roughly what a scene shows right after GO, for color previews: the first step of every composition, intensity
 * highest takes precedence (scaled by the level of the use), other attributes from the later composition.
 */
export function sceneStartValues(
  scene: Pick<Scene, 'compositions'> & Partial<Pick<Scene, 'looks'>>,
  compositions: Map<number, Composition>,
  looks: Map<number, Look>,
): Record<number, AttributeValues> {
  const result: Record<number, AttributeValues> = {};
  // looks used directly come first, the compositions over them
  for (const use of scene.looks ?? []) {
    for (const [id, fixtureValues] of Object.entries(looks.get(use.lookId)?.values ?? {})) {
      const own: AttributeValues = { ...(result[Number(id)] ?? {}) };
      for (const [attribute, value] of Object.entries(fixtureValues)) {
        own[attribute] = attribute.replace(/@\d+$/, '') === 'dimmer' ? Math.max(own[attribute] ?? 0, value * use.level) : value;
      }
      result[Number(id)] = own;
    }
  }
  for (const use of scene.compositions) {
    const composition = compositions.get(use.compositionId);
    if (!composition || composition.intensityMode === 'relative') continue;
    const firstRow = (stepRows(composition.steps)[0] ?? []).map((i) => composition.steps[i]);
    const entries = firstRow.flatMap((step) => Object.entries(looks.get(step.lookId)?.values ?? {}));
    for (const [id, fixtureValues] of entries) {
      const own: AttributeValues = { ...(result[Number(id)] ?? {}) };
      for (const [attribute, value] of Object.entries(fixtureValues)) {
        own[attribute] = attribute.replace(/@\d+$/, '') === 'dimmer'
          ? Math.max(own[attribute] ?? 0, value * use.level)
          : value;
      }
      result[Number(id)] = own;
    }
  }
  return result;
}

/** Scenes (by number) whose compositions or keys use the composition. */
export function scenesUsing(compositionId: number, scenes: Scene[]): Scene[] {
  return scenes.filter((s) => s.compositions.some((c) => c.compositionId === compositionId)
    || s.keys.some((k) => k.compositionId === compositionId));
}

/** Compositions whose steps play the look. */
export function compositionsPlaying(lookId: number, compositions: Composition[]): Composition[] {
  return compositions.filter((c) => c.steps.some((s) => s.lookId === lookId));
}
