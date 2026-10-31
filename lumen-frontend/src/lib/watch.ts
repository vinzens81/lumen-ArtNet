import { parseScript } from './script';
import type { PlaybackStatus, Scene } from './types';

/** What the view-only stage says about the playback: the scene on stage and the part of it after a light marker. */
export interface WatchCaption {
  /** "3 · Opening", or what is on stage when no scene is */
  title: string;
  /** name of the light marker whose light is on stage, null for the start of the scene */
  part: string | null;
}

export function watchCaption(scenes: Scene[], status: PlaybackStatus | null): WatchCaption {
  if (!status?.available) return { title: 'No show running', part: null };
  const scene = scenes.find((s) => s.id === status.currentSceneId);
  if (!scene) {
    return { title: status.position >= status.sceneCount && status.sceneCount > 0 ? 'End of the show' : 'Before the first scene', part: null };
  }
  const markers = status.section > 0 ? parseScript(scene.script).markers : [];
  return { title: `${scene.number} · ${scene.name}`, part: markers[status.section - 1]?.name ?? null };
}

/** What comes after the scene on stage, for the view-only script: "Next: 2.5 · Night", or null when nothing is known. */
export function watchNext(scenes: Scene[], status: PlaybackStatus | null): string | null {
  if (!status?.available) return null;
  const next = scenes.find((s) => s.id === status.nextSceneId);
  if (next) return `Next: ${next.number} · ${next.name}`;
  return status.nextIsEnd ? 'Next: end of the show' : null;
}

/** What the view-only script says while no scene is on stage, or null when one is. */
export function watchIdleText(status: PlaybackStatus | null): string | null {
  if (!status?.available) return 'No show running – the script appears here once a scene is on stage.';
  if (status.currentSceneId !== null) return null;
  if (status.position >= status.sceneCount && status.sceneCount > 0) return 'End of the show.';
  return 'Before the first scene – the script appears here with the first GO.';
}

/**
 * The scenes coming up, for the view-only cue list: the next scene (as chosen by the operator) and the ones after it
 * in the cue list, at most `count`. `end` is true when the show ends after them.
 */
export function watchUpcoming(scenes: Scene[], status: PlaybackStatus | null,
  count = 3): { scenes: Scene[]; end: boolean } {
  if (!status?.available || status.nextIsEnd) return { scenes: [], end: status?.nextIsEnd === true };
  const start = scenes.findIndex((s) => s.id === status.nextSceneId);
  if (start < 0) return { scenes: [], end: false };
  const upcoming = scenes.slice(start, start + count);
  return { scenes: upcoming, end: start + count >= scenes.length };
}
