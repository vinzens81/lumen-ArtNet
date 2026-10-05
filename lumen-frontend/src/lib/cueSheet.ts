import type { ExtraKey, Project, Scene } from './types';

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
  /** extra keys usable in the scene, e.g. "1 Storm" */
  keys: string[];
}

/** Name of an extra key as the operator sees it: its composition or the scene it plays. */
export function keyName(key: ExtraKey, scenes: Scene[]): string {
  if (key.composition) return key.composition.name;
  const scene = scenes.find((s) => s.id === key.sceneId);
  return scene ? scene.name : '?';
}

/**
 * The cue list as printed for the operator: the scenes GO plays, in order, with the fade times that apply (the
 * scene's own or the project's).
 *
 * @param sceneKeys slots of the extra keys usable per scene id
 */
export function cueRows(
  project: Pick<Project, 'fadeInMs' | 'fadeOutMs'>,
  scenes: Scene[],
  keys: ExtraKey[],
  sceneKeys: Map<number, number[]>,
): CueRow[] {
  const cues = scenes.filter((s) => !s.keyOnly);
  const keyNames = new Map(keys.map((k) => [k.slot, `${k.slot} ${keyName(k, scenes)}`]));
  return cues.map((scene, index) => ({
    sceneId: scene.id,
    number: scene.number,
    name: scene.name,
    cueNote: scene.cueNote,
    fadeIn: { ms: scene.fadeInMs ?? project.fadeInMs, own: scene.fadeInMs !== null },
    fadeOut: { ms: scene.fadeOutMs ?? project.fadeOutMs, own: scene.fadeOutMs !== null },
    delayMs: scene.delayMs,
    autoFollowMs: index < cues.length - 1 ? scene.autoFollowMs : null,
    compositions: scene.compositions.map((c) => c.name),
    keys: [...(sceneKeys.get(scene.id) ?? [])]
      .sort((a, b) => a - b)
      .map((slot) => keyNames.get(slot))
      .filter((name): name is string => name !== undefined),
  }));
}

/** Seconds for the sheet: "3 s", "1.5 s", "0 s". */
export function sheetSeconds(ms: number): string {
  return `${+(ms / 1000).toFixed(2)} s`;
}
