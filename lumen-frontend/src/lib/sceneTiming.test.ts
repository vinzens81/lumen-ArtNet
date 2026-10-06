import { describe, expect, it } from 'vitest';
import { fadeEnded, fadeInProgress, sceneRequestOf, secondsToMs, timingLabel } from './sceneTiming';
import type { Scene } from './types';

describe('timingLabel', () => {
  it('is null for the project times', () => {
    expect(timingLabel({ fadeInMs: null, fadeOutMs: null, delayMs: 0 })).toBeNull();
    expect(timingLabel({ fadeInMs: null, fadeOutMs: null, delayMs: 0, fadeCurve: 's-curve' })).toBe('S-curve');
  });

  it('lists only what the scene sets', () => {
    expect(timingLabel({ fadeInMs: 0, fadeOutMs: 0, delayMs: 0 })).toBe('in 0 s · out 0 s');
    expect(timingLabel({ fadeInMs: null, fadeOutMs: 1500, delayMs: 2000 })).toBe('out 1.5 s · delay 2 s');
  });
});

describe('secondsToMs', () => {
  it('keeps an empty field empty', () => {
    expect(secondsToMs(null)).toBeNull();
    expect(secondsToMs(undefined)).toBeNull();
    expect(secondsToMs(0)).toBe(0);
    expect(secondsToMs(0.25)).toBe(250);
  });
});

describe('sceneRequestOf', () => {
  it('keeps the fade times, e.g. when a scene is moved', () => {
    const scene: Scene = {
      id: 1, number: '2', name: 'Flash', cueNote: null, autoFollowMs: null,
      fadeInMs: 0, fadeOutMs: null, delayMs: 500, fadeCurve: 'square', script: 'Hark.', compositions: [], keys: [], looks: [], sections: [],
    };
    expect({ ...sceneRequestOf(scene), number: '2.5' }).toEqual({
      number: '2.5', name: 'Flash', cueNote: null, autoFollowMs: null,
      fadeInMs: 0, fadeOutMs: null, delayMs: 500, fadeCurve: 'square', script: 'Hark.',
    });
  });
});

describe('fadeInProgress', () => {
  it('waits for the delay', () => {
    expect(fadeInProgress(500, 2000, 1000)).toBe(0);
    expect(fadeInProgress(2000, 2000, 1000)).toBe(0.5);
    expect(fadeInProgress(5000, 2000, 1000)).toBe(1);
  });

  it('completes a zero fade-in once the delay is over', () => {
    expect(fadeInProgress(0, 0, 0)).toBe(1);
    expect(fadeInProgress(999, 0, 1000)).toBe(0);
  });
});

describe('fadeEnded', () => {
  it('waits for the longer of delayed fade-in and fade-out', () => {
    const fade = { fadeInMs: 2000, fadeOutMs: 4000, delayMs: 1000 };
    expect(fadeEnded(3999, fade)).toBe(false);
    expect(fadeEnded(4000, fade)).toBe(true);
    expect(fadeEnded(4000, { ...fade, delayMs: 3000 })).toBe(false);
  });
});
