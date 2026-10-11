import { fadeInProgress } from './sceneTiming';
import type { OperatingMode, PlaybackStatus, Scene } from './types';

/** What the bar below the scene on stage shows on the show remote. */
export interface NowProgress {
  kind: 'fade' | 'follow' | 'idle';
  /** 0..1 */
  value: number;
  text: string;
}

function seconds(ms: number): string {
  return `${(ms / 1000).toFixed(1)} s`;
}

/**
 * How far the running fade or the auto-follow countdown is. `sinceStatusMs` is the time since the status arrived
 * (it stands still while paused), `autoFollowMs` the full auto-follow time of the scene on stage.
 */
export function nowProgress(status: PlaybackStatus | null, sinceStatusMs: number,
  autoFollowMs: number | null): NowProgress {
  if (!status) return { kind: 'idle', value: 0, text: '' };
  const since = status.paused ? 0 : Math.max(0, sinceStatusMs);
  const fade = status.fade;
  if (fade) {
    const elapsed = fade.elapsedMs + since;
    const value = fadeInProgress(elapsed, fade.fadeInMs, fade.delayMs);
    if (value < 1) {
      const what = fade.withinScene ? 'Light marker' : 'Fade in';
      const delayLeft = fade.delayMs - elapsed;
      const text = delayLeft > 0 ? `${what} starts in ${seconds(delayLeft)}`
        : `${what} · ${seconds(Math.max(0, fade.delayMs + fade.fadeInMs - elapsed))} left`;
      return { kind: 'fade', value, text };
    }
  }
  if (status.autoFollowInMs !== null) {
    const left = Math.max(0, status.autoFollowInMs - since);
    const total = autoFollowMs !== null && autoFollowMs > 0 ? autoFollowMs : null;
    const value = total === null ? 0 : Math.min(1, Math.max(0, 1 - left / total));
    return { kind: 'follow', value, text: `Continues automatically in ${seconds(left)}` };
  }
  return { kind: 'idle', value: 0, text: status.paused ? 'Paused' : 'Stays until GO' };
}

/** Why the remote cannot run the show right now, or null when it can. */
export function remoteBlocked(mode: OperatingMode, modeKnown: boolean, status: PlaybackStatus | null): string | null {
  if (!modeKnown) return 'Connecting…';
  if (mode !== 'show') return 'Show mode is off – start it on the control desk. The remote only runs the show.';
  if (!status?.available) return 'No show to run – the active project has no scenes.';
  return null;
}

/**
 * The scene to make the next one when stepping through the list by `step` from the current next scene (or from the
 * end when it is next). Null before the first and after the last scene.
 */
export function stepNext(scenes: Scene[], status: PlaybackStatus | null, step: number): Scene | null {
  if (!status || scenes.length === 0) return null;
  const next = status.nextSceneId !== null ? scenes.findIndex((s) => s.id === status.nextSceneId) : -1;
  const from = next >= 0 ? next : status.nextIsEnd ? scenes.length : status.position;
  return scenes[from + step] ?? null;
}

