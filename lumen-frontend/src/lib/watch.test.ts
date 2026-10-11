import { describe, expect, it } from 'vitest';
import type { PlaybackStatus, Scene } from './types';
import { watchCaption, watchIdleText, watchNext } from './watch';

const scenes = [
  { id: 1, number: '1', name: 'Opening', script: 'Anna: Hello.\n//## Window (blue)##\nBen: Bye.' },
  { id: 2, number: '2.5', name: 'Night', script: '' },
] as Scene[];

function status(extra: Partial<PlaybackStatus>): PlaybackStatus {
  return {
    available: true, position: 0, sceneCount: 2, currentSceneId: 1, nextSceneId: 2, nextIsEnd: false, fade: null,
    paused: false, autoFollowInMs: null, resumeSceneId: null, keys: [], faders: [], scriptLine: 0, section: 0,
    showTimeMs: null, showTimeRunning: false,
    ...extra,
  };
}

describe('watchCaption', () => {
  it('names the scene on stage and the light marker it is at', () => {
    expect(watchCaption(scenes, status({}))).toEqual({ title: '1 · Opening', part: null });
    expect(watchCaption(scenes, status({ section: 1 }))).toEqual({ title: '1 · Opening', part: 'Window' });
  });

  it('says when no scene is on stage', () => {
    expect(watchCaption(scenes, null).title).toBe('No show running');
    expect(watchCaption(scenes, status({ position: -1, currentSceneId: null })).title).toBe('Before the first scene');
    expect(watchCaption(scenes, status({ position: 2, currentSceneId: null })).title).toBe('End of the show');
  });
});

describe('watchNext', () => {
  it('names the scene after the one on stage', () => {
    expect(watchNext(scenes, status({}))).toBe('Next: 2.5 · Night');
    expect(watchNext(scenes, status({ currentSceneId: 2, nextSceneId: null, nextIsEnd: true }))).toBe('Next: end of the show');
  });

  it('says nothing when no show is running or nothing follows', () => {
    expect(watchNext(scenes, null)).toBeNull();
    expect(watchNext(scenes, status({ nextSceneId: null }))).toBeNull();
  });
});

describe('watchIdleText', () => {
  it('says why there is no script to show', () => {
    expect(watchIdleText(null)).toMatch(/^No show running/);
    expect(watchIdleText(status({ available: false }))).toMatch(/^No show running/);
    expect(watchIdleText(status({ position: -1, currentSceneId: null }))).toMatch(/^Before the first scene/);
    expect(watchIdleText(status({ position: 2, currentSceneId: null }))).toBe('End of the show.');
  });

  it('is null while a scene is on stage', () => {
    expect(watchIdleText(status({}))).toBeNull();
  });
});
