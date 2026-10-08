import { describe, expect, it } from 'vitest';
import { showStack } from './showStack';
import type { Scene } from './types';

const scenes = [1, 2, 3, 4, 5].map((id) => ({ id, number: String(id), name: `S${id}` }) as Scene);

function status(position: number, current: number | null, next: number | null, extra = {}) {
  return { position, currentSceneId: current, nextSceneId: next, nextIsEnd: false, fade: null, ...extra };
}

describe('showStack', () => {
  it('stacks the scene before, the next two and how many follow', () => {
    const stack = showStack(scenes, status(1, 2, 3));
    expect(stack.previous?.id).toBe(1);
    expect(stack.current?.id).toBe(2);
    expect(stack.next).toEqual({ kind: 'scene', scene: scenes[2] });
    expect(stack.afterNext).toEqual({ kind: 'scene', scene: scenes[3] });
    expect(stack.more).toBe(1);
  });

  it('shows the scene fading out as the one before and the end after the last scene', () => {
    const fade = { fromSceneId: 2, elapsedMs: 0, fadeInMs: 1000, fadeOutMs: 1000, delayMs: 0, withinScene: false };
    const stack = showStack(scenes, status(3, 4, 5, { fade }));
    expect(stack.previous?.id).toBe(2);
    expect(stack.afterNext).toEqual({ kind: 'end' });
    expect(stack.more).toBe(0);
    expect(showStack(scenes, status(4, 5, null, { nextIsEnd: true })).next).toEqual({ kind: 'end' });
  });

  it('keeps the stack while a light marker fades within the scene', () => {
    const fade = { fromSceneId: 3, elapsedMs: 0, fadeInMs: 1000, fadeOutMs: 1000, delayMs: 0, withinScene: true };
    expect(showStack(scenes, status(2, 3, 4, { fade })).previous?.id).toBe(2);
  });

  it('has nothing before the first scene and before the start', () => {
    expect(showStack(scenes, status(-1, null, 1)).previous).toBeNull();
    expect(showStack(scenes, status(0, 1, 2)).previous).toBeNull();
    expect(showStack(scenes, null).current).toBeNull();
  });
});
