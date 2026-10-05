import { describe, expect, it } from 'vitest';
import { cueRows, sheetSeconds } from './cueSheet';
import type { ExtraKey, Scene } from './types';

function scene(id: number, number: string, overrides: Partial<Scene> = {}): Scene {
  return {
    id, number, name: `Scene ${number}`, cueNote: null, autoFollowMs: null, keyOnly: false,
    fadeInMs: null, fadeOutMs: null, delayMs: 0, fixtureCount: 1, values: {}, compositions: [],
    ...overrides,
  };
}

const project = { fadeInMs: 3000, fadeOutMs: 5000 };

describe('cueRows', () => {
  it('lists the cue list with the fade times that apply', () => {
    const rows = cueRows(project, [
      scene(1, '1', { cueNote: 'House lights out' }),
      scene(2, '2', { fadeInMs: 0, fadeOutMs: 0, delayMs: 500 }),
    ], [], new Map());

    expect(rows.map((r) => r.number)).toEqual(['1', '2']);
    expect(rows[0].cueNote).toBe('House lights out');
    expect(rows[0].fadeIn).toEqual({ ms: 3000, own: false });
    expect(rows[1].fadeOut).toEqual({ ms: 0, own: true });
    expect(rows[1].delayMs).toBe(500);
  });

  it('leaves out scenes for extra keys and auto-follow on the last scene', () => {
    const rows = cueRows(project, [
      scene(1, '1', { autoFollowMs: 8000 }),
      scene(2, '2', { autoFollowMs: 4000 }),
      scene(3, '50', { keyOnly: true, name: 'Lightning' }),
    ], [], new Map());

    expect(rows.map((r) => r.number)).toEqual(['1', '2']);
    expect(rows[0].autoFollowMs).toBe(8000);
    expect(rows[1].autoFollowMs).toBeNull();
  });

  it('names the extra keys of each scene', () => {
    const scenes = [scene(1, '1'), scene(3, '50', { keyOnly: true, name: 'Lightning' })];
    const keys: ExtraKey[] = [
      { slot: 2, mode: 'flash', fadeMs: 0, composition: null, sceneId: 3 },
      { slot: 1, mode: 'toggle', fadeMs: 0, composition: { name: 'Storm' } as ExtraKey['composition'], sceneId: null },
    ];

    const rows = cueRows(project, scenes, keys, new Map([[1, [2, 1]]]));

    expect(rows[0].keys).toEqual(['1 Storm', '2 Lightning']);
  });
});

describe('sheetSeconds', () => {
  it('drops needless decimals', () => {
    expect(sheetSeconds(3000)).toBe('3 s');
    expect(sheetSeconds(1500)).toBe('1.5 s');
    expect(sheetSeconds(0)).toBe('0 s');
  });
});
