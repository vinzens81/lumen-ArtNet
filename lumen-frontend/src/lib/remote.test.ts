import { describe, expect, it } from 'vitest';
import { nowProgress, remoteBlocked, stepNext } from './remote';
import type { PlaybackStatus, Scene } from './types';

const scenes = [1, 2, 3].map((id) => ({ id, number: String(id), name: `S${id}` }) as Scene);

function status(extra: Partial<PlaybackStatus> = {}): PlaybackStatus {
  return {
    available: true, position: 0, sceneCount: 3, currentSceneId: 1, nextSceneId: 2, nextIsEnd: false, fade: null,
    paused: false, autoFollowInMs: null, resumeSceneId: null, keys: [], faders: [], scriptLine: 0, section: 0,
    showTimeMs: null, showTimeRunning: false,
    ...extra,
  } as PlaybackStatus;
}

const fade = { fromSceneId: null, elapsedMs: 1000, fadeInMs: 4000, fadeOutMs: 2000, delayMs: 0, withinScene: false };

describe('nowProgress', () => {
  it('shows the running fade-in with the time left', () => {
    expect(nowProgress(status({ fade }), 1000, null)).toEqual({ kind: 'fade', value: 0.5, text: 'Fade in · 2.0 s left' });
  });

  it('counts down the delay before the fade-in starts', () => {
    const delayed = { ...fade, elapsedMs: 0, delayMs: 3000 };
    expect(nowProgress(status({ fade: delayed }), 1000, null)).toEqual(
      { kind: 'fade', value: 0, text: 'Fade in starts in 2.0 s' });
  });

  it('names a fade to a light marker within the scene', () => {
    expect(nowProgress(status({ fade: { ...fade, withinScene: true } }), 0, null).text).toBe('Light marker · 3.0 s left');
  });

  it('stands still while paused', () => {
    expect(nowProgress(status({ fade, paused: true }), 5000, null).value).toBe(0.25);
  });

  it('counts down auto-follow once the fade is done', () => {
    const done = { ...fade, elapsedMs: 4000 };
    expect(nowProgress(status({ fade: done, autoFollowInMs: 6000 }), 1000, 10000)).toEqual(
      { kind: 'follow', value: 0.5, text: 'Continues automatically in 5.0 s' });
  });

  it('waits for GO otherwise', () => {
    expect(nowProgress(status(), 0, null)).toEqual({ kind: 'idle', value: 0, text: 'Stays until GO' });
    expect(nowProgress(status({ paused: true }), 0, null).text).toBe('Paused');
    expect(nowProgress(null, 0, null).text).toBe('');
  });
});

describe('remoteBlocked', () => {
  it('runs the show only in show mode with scenes', () => {
    expect(remoteBlocked('show', true, status())).toBeNull();
    expect(remoteBlocked('setup', true, status())).toMatch(/Show mode is off/);
    expect(remoteBlocked('party', true, status())).toMatch(/Show mode is off/);
    expect(remoteBlocked('show', true, status({ available: false }))).toMatch(/no scenes/);
    expect(remoteBlocked('show', false, status())).toBe('Connecting…');
  });
});

describe('stepNext', () => {
  it('moves the next scene up and down the list', () => {
    expect(stepNext(scenes, status(), 1)?.id).toBe(3);
    expect(stepNext(scenes, status(), -1)?.id).toBe(1);
  });

  it('stops at both ends of the list', () => {
    expect(stepNext(scenes, status({ nextSceneId: 3 }), 1)).toBeNull();
    expect(stepNext(scenes, status({ position: -1, currentSceneId: null, nextSceneId: 1 }), -1)).toBeNull();
  });

  it('steps back from the end to the last scene', () => {
    const atLast = status({ position: 2, currentSceneId: 3, nextSceneId: null, nextIsEnd: true });
    expect(stepNext(scenes, atLast, -1)?.id).toBe(3);
    expect(stepNext(scenes, atLast, 1)).toBeNull();
  });
});

