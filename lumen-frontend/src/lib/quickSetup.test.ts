import { describe, expect, it } from 'vitest';
import { abilitiesOf, ALL_OPTIONS, fitting, makesSomething, quickSetupRequest, resultSummary } from './quickSetup';
import type { Mode } from './types';

const mode = (...functions: string[]): Mode => ({
  name: 'std',
  dmxChannelCount: functions.length,
  heads: 0,
  channels: functions.map((f) => ({ function: f, ranges: [] })) as unknown as Mode['channels'],
});

describe('quick setup', () => {
  it('offers colors and moves only to fixtures that can do them', () => {
    expect(abilitiesOf([mode('dimmer')])).toEqual({ colors: false, gradient: false, moves: false });
    expect(abilitiesOf([mode('dimmer'), mode('red', 'green', 'blue', 'white')]))
      .toEqual({ colors: true, gradient: true, moves: false });
    expect(abilitiesOf([mode('cyan', 'magenta', 'yellow')])).toMatchObject({ colors: true, gradient: true });
    expect(abilitiesOf([mode('pan', 'tilt', 'color-wheel')])).toEqual({ colors: false, gradient: false, moves: true });
    const wheel = mode('pan', 'tilt', 'color-wheel');
    wheel.channels[2].ranges = [{ from: 0, to: 9, label: 'Red', color: '#ff0000' }];
    expect(abilitiesOf([wheel])).toEqual({ colors: true, gradient: false, moves: true });
    expect(fitting(ALL_OPTIONS, { colors: false, gradient: false, moves: true })).toEqual({ ...ALL_OPTIONS, colors: false });
  });

  it('needs something to make', () => {
    expect(makesSomething(ALL_OPTIONS)).toBe(true);
    expect(makesSomething({ ...ALL_OPTIONS, intensity: false, colors: false, moves: false, gradients: false }))
      .toBe(false);
  });

  it('builds the request and sums up the result', () => {
    expect(quickSetupRequest([1, 2], '  ', ALL_OPTIONS)).toEqual({ fixtureIds: [1, 2], name: null, ...ALL_OPTIONS });
    expect(quickSetupRequest([1], 'Front', ALL_OPTIONS).name).toBe('Front');
    const made = (n: number) => Array.from({ length: n }, (_, i) => ({ id: i, name: `x${i}` }));
    expect(resultSummary({ name: 'PAR', looks: made(8), compositions: made(8), moves: [], presets: [],
      partyRows: [2, 3], notes: [] })).toBe('8 looks and 2 party rows for "PAR"');
    expect(resultSummary({ name: 'MH', looks: made(1), compositions: [], moves: made(3), presets: made(7),
      partyRows: [4], notes: [] })).toBe('1 look, 7 palette colors, 3 moves and 1 party row for "MH"');
  });
});
