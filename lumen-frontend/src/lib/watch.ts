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
