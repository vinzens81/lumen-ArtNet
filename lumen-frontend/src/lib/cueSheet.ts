import type { Composition, Project, Scene } from './types';

/** A fade time on the cue sheet; `own` marks a scene's own time instead of the project's. */
export interface SheetTime {
  ms: number;
  own: boolean;
}

/** One line of the printed cue list. */
export interface CueRow {
  sceneId: number;
  number: string;
  name: string;
  cueNote: string | null;
  fadeIn: SheetTime;
  fadeOut: SheetTime;
  delayMs: number;
  /** auto-follow stay time; null without it and on the last scene, where it is ignored */
  autoFollowMs: number | null;
  /** names of the compositions running in the scene */
  compositions: string[];
  /** extra keys of the scene, e.g. "1 Storm" */
  keys: string[];
}

/**
 * The cue list as printed for the operator: the scenes GO plays, in order, with the fade times that apply (the
 * scene's own or the project's).
 */
export function cueRows(
  project: Pick<Project, 'fadeInMs' | 'fadeOutMs'>,
  scenes: Scene[],
  compositions: Composition[],
): CueRow[] {
  const cues = scenes.filter((s) => !s.outsideCueList);
  const names = new Map(compositions.map((c) => [c.id, c.name]));
  return cues.map((scene, index) => ({
    sceneId: scene.id,
    number: scene.number,
    name: scene.name,
    cueNote: scene.cueNote,
    fadeIn: { ms: scene.fadeInMs ?? project.fadeInMs, own: scene.fadeInMs !== null },
    fadeOut: { ms: scene.fadeOutMs ?? project.fadeOutMs, own: scene.fadeOutMs !== null },
    delayMs: scene.delayMs,
    autoFollowMs: index < cues.length - 1 ? scene.autoFollowMs : null,
    compositions: scene.compositions.map((use) => names.get(use.compositionId) ?? '?'),
    keys: [...scene.keys]
      .sort((a, b) => a.slot - b.slot)
      .map((key) => `${key.slot} ${names.get(key.compositionId) ?? '?'}`),
  }));
}

/** Seconds for the sheet: "3 s", "1.5 s", "0 s". */
export function sheetSeconds(ms: number): string {
  return `${+(ms / 1000).toFixed(2)} s`;
}
