import { describe, expect, it } from 'vitest';
import {
  attributesOf, baseOf, defaultValue, dimmersOnlyPerHead, headDimmers, headOf, onlyHeadDimmers, valueFor, withHead,
} from './attributes';
import { headPreviewsOf, previewOf } from './color';
import type { Channel, Mode } from './types';

function channel(fn: string, offset: number, head = 0): Channel {
  return {
    offset, function: fn, attribute: head > 0 ? `${fn}@${head}` : fn, head, name: fn, resolution: 8,
    dmxChannelCount: 1, defaultValue: 0, invert: false, fade: 'smooth', ranges: [],
  };
}

/** a bar with 2 RGB heads and no dimmer channel */
const bar: Mode = {
  name: '6ch', dmxChannelCount: 6, heads: 2,
  channels: [1, 2].flatMap((head) => ['red', 'green', 'blue'].map((fn, i) => channel(fn, (head - 1) * 3 + i + 1, head))),
};

/** a dimmer pack: four channels as heads, no dimmer for the whole fixture */
const pack: Mode = { name: '4ch', dmxChannelCount: 4, heads: 4, channels: [1, 2, 3, 4].map((h) => channel('dimmer', h, h)) };

describe('dimmer packs', () => {
  it('have their channels per head and no master', () => {
    expect(dimmersOnlyPerHead(pack)).toBe(true);
    expect(headDimmers(pack)).toEqual([1, 2, 3, 4]);
    expect(onlyHeadDimmers(pack)).toBe(true);
    expect(dimmersOnlyPerHead(bar)).toBe(false);
    expect(onlyHeadDimmers(bar)).toBe(false);
    const blinder: Mode = { ...pack, channels: [channel('dimmer', 1), ...pack.channels.slice(0, 2)] };
    expect(dimmersOnlyPerHead(blinder)).toBe(false);
  });
});

describe('multi-pixel attributes', () => {
  it('splits head and base', () => {
    expect(headOf('red@3')).toBe(3);
    expect(headOf('generic:Speed')).toBe(0);
    expect(baseOf('red@3')).toBe('red');
    expect(withHead('red@3', 1)).toBe('red@1');
    expect(withHead('red@3', 0)).toBe('red');
  });

  it('lists whole-fixture and head attributes like the server', () => {
    expect(attributesOf(bar)).toEqual([
      'dimmer', 'red', 'green', 'blue', 'red@1', 'green@1', 'blue@1', 'red@2', 'green@2', 'blue@2', 'dimmer@1', 'dimmer@2',
    ]);
    expect(defaultValue(bar, 'dimmer@2')).toBe(0);
  });

  it('falls back to the whole fixture', () => {
    expect(valueFor({ red: 0.5 }, 'red@2')).toBe(0.5);
    expect(valueFor({ red: 0.5, 'red@2': 1 }, 'red@2')).toBe(1);
    expect(valueFor({}, 'red')).toBeUndefined();
  });

  it('previews heads one by one and the fixture as their average', () => {
    const values = { dimmer: 1, red: 1, 'blue@2': 1, 'red@2': 0 };
    const heads = headPreviewsOf(bar, values);
    expect(heads.map((h) => h.color)).toEqual([[1, 0, 0], [0, 0, 1]]);
    expect(previewOf(bar, values).intensity).toBe(1);
  });
});
