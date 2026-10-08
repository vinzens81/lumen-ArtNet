import { describe, expect, it } from 'vitest';
import { attributeGroup, presetColor, presetNames, presetsFor, presetUse } from './presets';
import type { Mode, Preset } from './types';

const warm: Preset = { id: 1, name: 'Warm', group: 'color', values: { 10: { red: 1, green: 0.5 }, 11: { red: 1 } } };
const narrow: Preset = { id: 2, name: 'Narrow', group: 'beam', values: { 12: { zoom: 0.1 } } };

describe('attributeGroup', () => {
  it('matches the groups of the server', () => {
    expect(attributeGroup('dimmer')).toBe('intensity');
    expect(attributeGroup('color-wheel')).toBe('color');
    expect(attributeGroup('tilt')).toBe('position');
    expect(attributeGroup('gobo')).toBe('beam');
    expect(attributeGroup('generic:Speed')).toBe('other');
  });
});

describe('presets of a selection', () => {
  it('offers presets that cover a selected fixture', () => {
    expect(presetsFor([warm, narrow], 'color', [11, 99])).toEqual([warm]);
    expect(presetsFor([warm, narrow], 'color', [12])).toEqual([]);
  });

  it('tells whether the fixtures use a preset', () => {
    expect(presetUse(warm, [10, 11], { 10: { color: 1 }, 11: { color: 1 } })).toBe('all');
    expect(presetUse(warm, [10, 11, 99], { 10: { color: 1 } })).toBe('some');
    expect(presetUse(warm, [10], { 10: { color: 3 } })).toBeNull();
  });

  it('names the presets of a fixture', () => {
    expect(presetNames({ 12: { beam: 2, color: 1 } }, 12, [warm, narrow]).sort()).toEqual(['Narrow', 'Warm']);
    expect(presetNames({}, 12, [warm])).toEqual([]);
  });
});

describe('presetColor', () => {
  it('shows the color of the first fixture', () => {
    const mode: Mode = {
      name: '3ch', dmxChannelCount: 3, heads: 0,
      channels: ['red', 'green', 'blue'].map((fn, i) => ({
        offset: i + 1, function: fn, attribute: fn, head: 0, name: fn, resolution: 8, dmxChannelCount: 1, defaultValue: 0, invert: false,
        fade: 'smooth', ranges: [],
      })),
    };
    const modes = new Map([[10, mode]]);
    const orange = presetColor(warm, modes);
    expect(orange).not.toBeNull();
    expect(orange).not.toBe(presetColor({ ...warm, values: { 10: { red: 1 } } }, modes));
    expect(presetColor(narrow, new Map())).toBeNull();
  });
});
