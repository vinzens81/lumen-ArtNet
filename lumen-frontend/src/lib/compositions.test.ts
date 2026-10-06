import { describe, expect, it } from 'vitest';
import {
  compositionFixtures, compositionIcon, cycleMs, defaultParams, generatorFixtures, isFast, lookMap, overlaps,
  sceneStartValues, unsuitableFixtures,
} from './compositions';
import type { Composition, Fixture, Look, Mode } from './types';

function composition(id: number, lookIds: number[], extra: Partial<Composition> = {}): Composition {
  return {
    id,
    name: `C${id}`,
    fadeMs: 1000,
    holdMs: 2000,
    playMode: 'loop',
    intensityMode: 'absolute',
    generator: null,
    steps: lookIds.map((lookId) => ({ lookId, fadeMs: null, holdMs: null })),
    ...extra,
  };
}

function look(id: number, values: Look['values']): Look {
  return { id, name: `L${id}`, owner: null, fixtureCount: Object.keys(values).length, values, presets: {} };
}

const mode = (...functions: string[]): Mode =>
  ({ name: 'm', dmxChannelCount: functions.length, channels: functions.map((f) => ({ function: f })) }) as Mode;

describe('compositions', () => {
  it('marks fast changes of effects and steps', () => {
    const chase = (stepMs: number) => composition(1, [1], {
      generator: { kind: 'chase', params: { ...defaultParams('chase'), stepMs }, target: {}, perHead: false },
    });
    expect(isFast(chase(200))).toBe(true);
    expect(isFast(chase(500))).toBe(false);
    expect(isFast(chase(500), 2)).toBe(true);
    expect(isFast(composition(1, [1, 2], { fadeMs: 0, holdMs: 100 }))).toBe(true);
    expect(isFast(composition(1, [1], { fadeMs: 0, holdMs: 100 })).valueOf()).toBe(false);
  });

  it('finds fixtures that cannot show an effect', () => {
    const modes = new Map([
      [1, mode('dimmer', 'red', 'green', 'blue')],
      [2, mode('dimmer')],
    ]);
    expect(unsuitableFixtures('color_cycle', [1, 2], modes)).toEqual([2]);
    expect(unsuitableFixtures('chase', [1, 2], modes)).toEqual([]);
  });

  it('resolves generator targets and the fixtures of steps', () => {
    const groups = [{ id: 7, fixtureIds: [3, 1] }];
    expect(generatorFixtures({ target: { groupId: 7 } }, groups)).toEqual([3, 1]);
    expect(generatorFixtures({ target: { fixtureIds: [5] } }, groups)).toEqual([5]);
    const looks = lookMap([look(1, { 9: { dimmer: 1 } }), look(2, { 2: { red: 1 } })]);
    expect(compositionFixtures(composition(1, [1, 2]), looks)).toEqual([2, 9]);
  });

  it('times a cycle with step overrides and ping-pong', () => {
    const c = composition(1, [1, 2, 3], { fadeMs: 0, holdMs: 1000 });
    c.steps[1].holdMs = 3000;
    expect(cycleMs(c)).toBe(5000);
    expect(cycleMs({ ...c, playMode: 'ping_pong' })).toBe(8000);
  });

  it('reports compositions that override the color of others', () => {
    const fixtures = [1, 2].map((id) => ({ id, name: `F${id}` }) as Fixture);
    const looks = lookMap([look(1, { 1: { dimmer: 1, red: 1 } }), look(2, { 1: { blue: 1 }, 2: { dimmer: 1 } }),
      look(3, { 2: { dimmer: 0.5 } })]);
    expect(overlaps([composition(1, [1]), composition(2, [2]), composition(3, [3])], looks, fixtures))
      .toEqual(['"C2" overrides the color of "C1" on F1']);
  });

  it('previews a scene at its start', () => {
    const looks = lookMap([look(1, { 1: { dimmer: 0.4, red: 1 } }), look(2, { 1: { dimmer: 1, blue: 1 } }),
      look(3, { 1: { dimmer: 0.2 } })]);
    const compositions = new Map([
      [1, composition(1, [1])],
      [2, composition(2, [2, 3])],
      [3, composition(3, [3], { intensityMode: 'relative' })],
    ]);
    const scene = { compositions: [
      { compositionId: 1, level: 1, speedFactor: 1, restart: false },
      { compositionId: 2, level: 0.5, speedFactor: 1, restart: false },
      { compositionId: 3, level: 1, speedFactor: 1, restart: false },
    ] };
    expect(sceneStartValues(scene, compositions, looks)).toEqual({ 1: { dimmer: 0.5, red: 1, blue: 1 } });
    expect(compositionIcon(composition(1, [1]))).toBe('▣');
    expect(compositionIcon(composition(1, [1, 2]))).toBe('▦');
  });
});
