import type { Scene, SceneRequest } from './types';

type Timing = Pick<Scene, 'fadeInMs' | 'fadeOutMs' | 'delayMs'>;

function secondsText(ms: number): string {
  return `${+(ms / 1000).toFixed(2)} s`;
}

/**
 * Short label of the fade times a scene sets itself, e.g. "in 0 s · out 0 s · delay 1 s"; null if it uses the
 * project's times without a delay.
 */
export function timingLabel(timing: Timing): string | null {
  const parts: string[] = [];
  if (timing.fadeInMs !== null) parts.push(`in ${secondsText(timing.fadeInMs)}`);
  if (timing.fadeOutMs !== null) parts.push(`out ${secondsText(timing.fadeOutMs)}`);
  if (timing.delayMs > 0) parts.push(`delay ${secondsText(timing.delayMs)}`);
  return parts.length > 0 ? parts.join(' · ') : null;
}

/** A seconds field of a form in milliseconds; an empty field (null) stays null, e.g. "use the project's time". */
export function secondsToMs(seconds: number | null | undefined): number | null {
  return seconds === null || seconds === undefined || Number.isNaN(seconds) ? null : Math.round(seconds * 1000);
}

/** An update request that keeps everything of the scene – change single fields on the result. */
export function sceneRequestOf(scene: Scene): SceneRequest {
  return {
    number: scene.number,
    name: scene.name,
    cueNote: scene.cueNote,
    autoFollowMs: scene.autoFollowMs,
    keyOnly: scene.keyOnly,
    fadeInMs: scene.fadeInMs,
    fadeOutMs: scene.fadeOutMs,
    delayMs: scene.delayMs,
  };
}

/**
 * Progress 0–1 of the fade-in of a running transition: it waits for the delay, a zero fade-in completes at once.
 */
export function fadeInProgress(elapsedMs: number, fadeInMs: number, delayMs: number): number {
  const sinceStart = elapsedMs - delayMs;
  if (sinceStart < 0) return 0;
  return fadeInMs <= 0 ? 1 : Math.min(1, sinceStart / fadeInMs);
}

/** Whether a transition has ended: the delayed fade-in and the fade-out are both complete. */
export function fadeEnded(
  elapsedMs: number,
  fade: { fadeInMs: number; fadeOutMs: number; delayMs: number },
): boolean {
  return elapsedMs >= Math.max(fade.delayMs + fade.fadeInMs, fade.fadeOutMs);
}
