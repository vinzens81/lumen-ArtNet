import { previewOf, rgbToCss } from './color';
import type { AttributeGroup, Mode, Preset, PresetGroup, PresetRefs } from './types';

const COLOR = new Set([
  'red', 'green', 'blue', 'white', 'warm-white', 'cold-white', 'amber', 'uv', 'lime', 'cyan', 'magenta', 'yellow',
  'cto', 'color-wheel',
]);
const BEAM = new Set(['focus', 'zoom', 'iris', 'gobo', 'gobo-rotation', 'prism', 'shutter', 'strobe']);

/** The attribute groups presets can be made for, with their names in the UI. */
export const PRESET_GROUPS: { id: PresetGroup; label: string }[] = [
  { id: 'color', label: 'Color' },
  { id: 'beam', label: 'Beam' },
];

/** What an attribute is about (same as AttributeGroup on the server). */
export function attributeGroup(attribute: string): AttributeGroup {
  if (attribute === 'dimmer') return 'intensity';
  if (COLOR.has(attribute)) return 'color';
  if (attribute === 'pan' || attribute === 'tilt') return 'position';
  if (BEAM.has(attribute)) return 'beam';
  return 'other';
}

/** Presets of a group that have values for at least one of the fixtures. */
export function presetsFor(presets: Preset[], group: PresetGroup, fixtureIds: number[]): Preset[] {
  return presets.filter((p) => p.group === group && fixtureIds.some((id) => p.values[id]));
}

/**
 * Whether the fixtures use a preset: 'all' if every fixture the preset has values for refers to it, 'some' if a
 * part does, null if none.
 */
export function presetUse(preset: Preset, fixtureIds: number[], refs: PresetRefs): 'all' | 'some' | null {
  const covered = fixtureIds.filter((id) => preset.values[id]);
  const using = covered.filter((id) => refs[id]?.[preset.group] === preset.id);
  if (using.length === 0) return null;
  return using.length === covered.length ? 'all' : 'some';
}

/** CSS color of a color preset as the first fixture with values would show it; null for other presets. */
export function presetColor(preset: Preset, modes: Map<number, Mode>): string | null {
  if (preset.group !== 'color') return null;
  for (const [id, values] of Object.entries(preset.values)) {
    const mode = modes.get(Number(id));
    if (mode) return rgbToCss(previewOf(mode, { ...values, dimmer: 1 }).color);
  }
  return null;
}

/** Names of the presets a fixture's values come from, e.g. "Warm · Chair". */
export function presetNames(refs: PresetRefs, fixtureId: number, presets: Preset[]): string[] {
  return Object.values(refs[fixtureId] ?? {})
    .map((id) => presets.find((p) => p.id === id)?.name)
    .filter((name): name is string => name !== undefined);
}
