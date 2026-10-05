import { describe, expect, it } from 'vitest';
import { defaultParams, isFast, overlaps, targetFixtures, unsuitableFixtures } from './compositions';
import type { Composition, Fixture, Mode } from './types';

function composition(id: number, kind: Composition['kind'], target: Composition['target'], extra: Partial<Composition> = {}): Composition {
  return {
    id,
    sceneId: 1,
    sourceId: null,
    position: id,
    name: `C${id}`,
    kind,
    params: defaultParams(kind),
    target,
    steps: null,
    level: 1,
    speedFactor: 1,
    restart: false,
    once: false,
    perHead: false,
    ...extra,
  };
}

const mode = (...functions: string[]): Mode =>
  ({ name: 'm', dmxChannelCount: functions.length, channels: functions.map((f) => ({ function: f })) }) as Mode;

describe('compositions', () => {
  it('marks fast intensity changes', () => {
    expect(isFast({ kind: 'chase', params: { ...defaultParams('chase'), stepMs: 200 } as never, speedFactor: 1 })).toBe(true);
    expect(isFast({ kind: 'chase', params: defaultParams('chase'), speedFactor: 1 })).toBe(false);
    expect(isFast({ kind: 'chase', params: defaultParams('chase'), speedFactor: 2 })).toBe(true);
    expect(isFast({ kind: 'wave', params: { ...defaultParams('wave'), periodMs: 250, max: 0.2 } as never, speedFactor: 1 }))
      .toBe(false);
    expect(isFast({ kind: 'flicker', params: { ...defaultParams('flicker'), character: 'broken_tube' } as never, speedFactor: 1 }))
      .toBe(true);
  });

  it('finds fixtures that cannot show the composition', () => {
    const modes = new Map([
      [1, mode('dimmer', 'red', 'green', 'blue')],
      [2, mode('dimmer')],
    ]);
    expect(unsuitableFixtures('color_cycle', [1, 2], modes)).toEqual([2]);
    expect(unsuitableFixtures('chase', [1, 2], modes)).toEqual([]);
  });

  it('resolves targets and reports overlaps', () => {
    const groups = [{ id: 7, fixtureIds: [1, 2, 3] }];
    const fixtures = [1, 2, 3].map((id) => ({ id, name: `F${id}` }) as Fixture);
    const chase = composition(1, 'chase', { groupId: 7 });
    const wave = composition(2, 'wave', { fixtureIds: [3, 4] });
    const colors = composition(3, 'rainbow', { fixtureIds: [1] });
    const steps = composition(4, 'steps', null, { steps: [{ holdMs: 1, fadeMs: 0, values: { 9: {}, 2: {} } }] });

    expect(targetFixtures(chase, groups)).toEqual([1, 2, 3]);
    expect(targetFixtures(steps, groups)).toEqual([2, 9]);
    expect(overlaps([chase, wave, colors], groups, fixtures)).toEqual(['"C2" overrides "C1" on F3']);
  });
});
