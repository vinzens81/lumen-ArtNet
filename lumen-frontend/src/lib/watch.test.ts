import { describe, expect, it } from 'vitest';
import type { PlaybackStatus, Scene } from './types';
import { watchCaption } from './watch';

const scenes = [
  { id: 1, number: '1', name: 'Opening', script: 'Anna: Hello.\n//## Window (blue)##\nBen: Bye.' },
  { id: 2, number: '2.5', name: 'Night', script: '' },
] as Scene[];

function status(extra: Partial<PlaybackStatus>): PlaybackStatus {
  return {
    available: true, position: 0, sceneCount: 2, currentSceneId: 1, nextSceneId: 2, nextIsEnd: false, fade: null,
    paused: false, autoFollowInMs: null, resumeSceneId: null, keys: [], faders: [], scriptLine: 0, section: 0,
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
