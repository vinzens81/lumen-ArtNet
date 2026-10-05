import { describe, expect, it } from 'vitest';
import { attributesOf, defaultValue, supportedValues } from './attributes';
import { hsvToRgb, previewOf, rgbToHsv } from './color';
import type { Channel, Mode } from './types';

function channel(fn: string, extra: Partial<Channel> = {}): Channel {
  return {
    offset: 1, function: fn, attribute: fn, head: 0, name: fn, resolution: 8, dmxChannelCount: 1, defaultValue: 0,
    invert: false, fade: 'smooth', ranges: [], ...extra,
  };
}

const RGB: Mode = { name: '3ch', dmxChannelCount: 3, heads: 0, channels: [channel('red'), channel('green'), channel('blue')] };
const DIMMER: Mode = { name: '1ch', dmxChannelCount: 1, heads: 0, channels: [channel('dimmer')] };
const WHEEL: Mode = {
  name: '2ch', dmxChannelCount: 2, heads: 0,
  channels: [channel('dimmer'), channel('color-wheel', { ranges: [{ from: 10, to: 19, label: 'Red', color: '#ff0000' }] })],
};

describe('hsv', () => {
  it('converts both ways', () => {
    expect(hsvToRgb(0, 1, 1)).toEqual([1, 0, 0]);
    expect(hsvToRgb(1 / 3, 1, 1)).toEqual([0, 1, 0]);
    const [h, s, v] = rgbToHsv([0, 0.5, 1]);
    expect(hsvToRgb(h, s, v).map((c) => Math.round(c * 100) / 100)).toEqual([0, 0.5, 1]);
  });
});

describe('previewOf', () => {
  it('mixes RGB and scales by dimmer and master', () => {
    const preview = previewOf(RGB, { dimmer: 1, red: 1, blue: 1 }, 0.5);
    expect(preview.color).toEqual([1, 0, 1]);
    expect(preview.intensity).toBe(0.5);
  });

  it('is dark when no color is set', () => {
    expect(previewOf(RGB, { dimmer: 1 }).intensity).toBe(0);
  });

  it('uses warm white for plain dimmers and the slot color of a color wheel', () => {
    expect(previewOf(DIMMER, { dimmer: 0.4 }).intensity).toBe(0.4);
    expect(previewOf(WHEEL, { dimmer: 1, 'color-wheel': 15 / 255 }).color).toEqual([1, 0, 0]);
  });
});

describe('attributes', () => {
  it('adds a virtual dimmer and names generic channels', () => {
    const mode: Mode = { ...RGB, channels: [...RGB.channels, channel('generic', { name: 'Speed', attribute: 'generic:Speed' })] };
    expect(attributesOf(mode)).toEqual(['dimmer', 'red', 'green', 'blue', 'generic:Speed']);
    expect(supportedValues(DIMMER, { dimmer: 1, red: 1 })).toEqual({ dimmer: 1 });
  });

  it('reads defaults from the profile', () => {
    const mode: Mode = { name: 'x', dmxChannelCount: 2, heads: 0, channels: [channel('pan', { resolution: 16, defaultValue: 32768 })] };
    expect(defaultValue(mode, 'pan')).toBeCloseTo(0.5, 3);
    expect(defaultValue(RGB, 'dimmer')).toBe(0);
  });
});
