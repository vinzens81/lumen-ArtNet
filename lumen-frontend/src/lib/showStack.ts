import type { PlaybackStatus, Scene } from './types';

/** A scene in the progress stack of the show view; `end` stands for the fade to black after the last scene. */
export type StackEntry = { kind: 'scene'; scene: Scene } | { kind: 'end' };

/**
 * What the show view stacks around the scene on stage: the one before (smaller), the next and the one after it
 * (smaller still), and how many follow after those.
 */
export interface ShowStack {
  /** the scene before – the one fading out during a fade */
  previous: Scene | null;
  current: Scene | null;
  next: StackEntry | null;
  afterNext: StackEntry | null;
  /** scenes after `afterNext` (the end not counted) */
  more: number;
}

export function showStack(
  scenes: Scene[],
  status: Pick<PlaybackStatus, 'position' | 'currentSceneId' | 'nextSceneId' | 'nextIsEnd' | 'fade'> | null,
): ShowStack {
  const empty: ShowStack = { previous: null, current: null, next: null, afterNext: null, more: 0 };
  if (!status) return empty;
  const byId = new Map(scenes.map((s) => [s.id, s]));
  const current = status.currentSceneId != null ? byId.get(status.currentSceneId) ?? null : null;
  const outgoing = status.fade?.fromSceneId != null ? byId.get(status.fade.fromSceneId) ?? null : null;
  const currentIndex = current ? scenes.indexOf(current) : status.position;
  const previous = outgoing ?? (currentIndex > 0 ? scenes[currentIndex - 1] ?? null : null);

  const nextScene = status.nextSceneId != null ? byId.get(status.nextSceneId) ?? null : null;
  const next: StackEntry | null = nextScene ? { kind: 'scene', scene: nextScene } : status.nextIsEnd ? { kind: 'end' } : null;
  if (!nextScene) return { previous, current, next, afterNext: null, more: 0 };

  const nextIndex = scenes.indexOf(nextScene);
  const following = scenes[nextIndex + 1];
  const afterNext: StackEntry = following ? { kind: 'scene', scene: following } : { kind: 'end' };
  const more = following ? Math.max(0, scenes.length - nextIndex - 2) : 0;
  return { previous, current, next, afterNext, more };
}
