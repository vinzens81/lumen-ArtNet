/**
 * The text spoken on stage during a scene, split into the sentences the operator steps through. Text in
 * `**…**` is a stage direction: shown differently and skipped when stepping. A name and a colon at the start of a
 * line (`Maike Schmitz: Hallo Rolf!`) marks who speaks. A line `//## Name (info)##` is a light marker: the scene's
 * light changes when the operator steps past it (same rule as ScriptMarkers on the server). A line
 * `//|| Name (info)||` is a pause: nobody speaks, but the operator stops there like at a speech.
 */
export type ScriptPart =
  /** a spoken sentence; `index` counts the sentences of the scene, starting at 0 */
  | { kind: 'line'; text: string; index: number }
  | { kind: 'note'; text: string }
  /** who speaks from here on (until the next speaker or an empty line) */
  | { kind: 'speaker'; name: string }
  /**
   * a light marker: the part `section` of the scene (1, 2, …) starts here; `firstLine` is the index of the first
   * sentence after it
   */
  | { kind: 'marker'; name: string; info: string; section: number; firstLine: number }
  /** a pause the operator stops at; it takes a sentence `index` of its own, so it is a position like a sentence */
  | { kind: 'pause'; name: string; info: string; index: number }
  /** line break in the text */
  | { kind: 'break' }
  /** empty line in the text */
  | { kind: 'paragraph' };

export interface Script {
  parts: ScriptPart[];
  /** number of positions in the text: spoken sentences and pauses */
  lineCount: number;
  noteCount: number;
  pauseCount: number;
  /** names of the speakers in order of their first appearance */
  speakers: string[];
  /** light markers in their order */
  markers: { name: string; info: string }[];
}

export const MARKER = /^[ \t]*\/\/[ \t]*##[ \t]*(.*?)[ \t]*##[ \t]*$/gm;
export const PAUSE = /^[ \t]*\/\/[ \t]*\|\|[ \t]*(.*?)[ \t]*\|\|[ \t]*$/gm;
/** a light marker (`##`) or a pause (`||`) on a line of its own */
const DIVIDER = /^[ \t]*\/\/[ \t]*(##|\|\|)[ \t]*(.*?)[ \t]*\1[ \t]*$/gm;

/**
 * A speaker at the start of a line: one to four words starting with a capital letter, then a colon and a space (or
 * the end of the line) – "Maike Schmitz:", "ROMEO:", "Der Wirt:". "He said: …" is no speaker.
 */
export const SPEAKER = /^\s*(\p{Lu}[\p{L}\p{M}'’.-]*(?:[ \t]+\p{Lu}[\p{L}\p{M}'’.-]*){0,3})[ \t]*:(?=\s|$)/u;
export const MAX_SPEAKER_LENGTH = 40;

/** A dot after these words does not end a sentence (compared in lower case). */
const ABBREVIATIONS = new Set([
  'dr', 'mr', 'mrs', 'ms', 'prof', 'st', 'jr', 'sr', 'vs', 'no', 'nr', 'ca', 'bzw', 'vgl', 'ggf', 'evtl', 'inkl',
  'hr', 'fr', 'frl', 'abs', 'str',
]);

export const NOTE = /\*\*([\s\S]+?)\*\*/g;
/** end of a sentence: punctuation, closing quotes or brackets, then whitespace or the end of the text */
const SENTENCE_END = /([.!?…]+)(["'“”‘’»«)\]]*)(?=\s|$)/g;

export function parseScript(text: string): Script {
  const parts: ScriptPart[] = [];
  let lineCount = 0;
  let noteCount = 0;
  let pauseCount = 0;
  const speakers: string[] = [];
  /** the next text starts a line of the text (where a speaker can stand) */
  let atLineStart = true;

  const markers: { name: string; info: string }[] = [];

  const addBreak = (kind: 'break' | 'paragraph') => {
    const last = parts[parts.length - 1];
    if (last === undefined || last.kind === 'marker' || last.kind === 'pause') return; // nothing to separate
    if (last.kind === 'paragraph') return;
    if (last.kind === 'break') parts[parts.length - 1] = { kind };
    else parts.push({ kind });
  };

  const addSpoken = (chunk: string) => {
    chunk.split(/(\n\s*\n|\n)/).forEach((piece, i) => {
      if (i % 2 === 1) {
        addBreak(piece === '\n' ? 'break' : 'paragraph');
        atLineStart = true;
        return;
      }
      const speaker = atLineStart ? SPEAKER.exec(piece) : null;
      if (speaker && speaker[1].length <= MAX_SPEAKER_LENGTH) {
        const name = speaker[1];
        parts.push({ kind: 'speaker', name });
        if (!speakers.includes(name)) speakers.push(name);
        piece = piece.slice(speaker[0].length);
      }
      if (piece.trim()) atLineStart = false;
      for (const sentence of sentences(piece)) {
        parts.push({ kind: 'line', text: sentence, index: lineCount++ });
      }
    });
  };

  /** Text between light markers: spoken sentences and stage directions. */
  const addText = (chunk: string) => {
    let position = 0;
    for (const match of chunk.matchAll(NOTE)) {
      addSpoken(chunk.slice(position, match.index));
      const note = match[1].trim();
      if (note) {
        parts.push({ kind: 'note', text: note });
        noteCount++;
        atLineStart = false;
      }
      position = match.index + match[0].length;
    }
    addSpoken(chunk.slice(position));
  };

  let position = 0;
  for (const match of text.matchAll(DIVIDER)) {
    if (!match[2].trim()) continue;
    addText(text.slice(position, match.index));
    while (parts.length > 0 && isBreak(parts[parts.length - 1])) parts.pop();
    const info = /^(.*?)\s*\((.*)\)$/.exec(match[2]);
    const named = info && info[1].trim() ? { name: info[1].trim(), info: info[2].trim() } : { name: match[2].trim(), info: '' };
    if (match[1] === '##') {
      markers.push(named);
      parts.push({ kind: 'marker', ...named, section: markers.length, firstLine: lineCount });
    } else {
      parts.push({ kind: 'pause', ...named, index: lineCount++ });
      pauseCount++;
    }
    atLineStart = true;
    position = match.index + match[0].length;
  }
  addText(text.slice(position));

  while (parts.length > 0 && isBreak(parts[parts.length - 1])) parts.pop();
  return { parts, lineCount, noteCount, pauseCount, speakers, markers };
}

/** How the script is laid out: speeches indented under their speaker, stage directions on lines of their own. */
export type ScriptBlock =
  /** what one speaker says; `continued` after a stage direction within the speech (the name is not repeated) */
  | { kind: 'speech'; speaker: string; continued: boolean; parts: ScriptPart[] }
  /** text without a speaker */
  | { kind: 'text'; parts: ScriptPart[] }
  | { kind: 'note'; text: string }
  | { kind: 'marker'; name: string; info: string; section: number }
  | { kind: 'pause'; name: string; info: string; index: number }
  | { kind: 'paragraph' };

export function scriptBlocks(script: Script): ScriptBlock[] {
  const blocks: ScriptBlock[] = [];
  /** who speaks, until the next speaker or an empty line */
  let speaker: string | null = null;
  /** the speech of `speaker` was interrupted by a stage direction */
  let interrupted = false;
  /** the block text goes into, null until text comes */
  let parts: ScriptPart[] | null = null;

  const open = (): ScriptPart[] => {
    if (parts === null) {
      parts = [];
      blocks.push(speaker !== null
        ? { kind: 'speech', speaker, continued: interrupted, parts }
        : { kind: 'text', parts });
    }
    return parts;
  };
  const close = () => {
    while (parts !== null && parts.length > 0 && isBreak(parts[parts.length - 1])) parts.pop();
    parts = null;
  };

  for (const part of script.parts) {
    switch (part.kind) {
      case 'speaker':
        close();
        speaker = part.name;
        interrupted = false;
        open(); // the name shows even before its text
        break;
      case 'note':
        close();
        blocks.push({ kind: 'note', text: part.text });
        interrupted = speaker !== null;
        break;
      case 'marker':
        close();
        blocks.push({ kind: 'marker', name: part.name, info: part.info, section: part.section });
        interrupted = speaker !== null;
        break;
      case 'pause':
        close();
        blocks.push({ kind: 'pause', name: part.name, info: part.info, index: part.index });
        interrupted = speaker !== null;
        break;
      case 'paragraph':
        close();
        speaker = null;
        interrupted = false;
        if (blocks.length > 0 && blocks[blocks.length - 1].kind !== 'paragraph') blocks.push({ kind: 'paragraph' });
        break;
      case 'break':
        // a line break at the start of a block has nothing to separate
        if (parts !== null && (parts as ScriptPart[]).length > 0) open().push(part);
        break;
      case 'line':
        open().push(part);
        break;
    }
  }
  close();
  return blocks;
}

/** A color for the speaker, always the same for the same name (hue in degrees). */
export function speakerHue(name: string): number {
  let hash = 0;
  for (const char of name) hash = (hash * 31 + char.codePointAt(0)!) >>> 0;
  return (hash * 137.508) % 360;
}

function isBreak(part: ScriptPart): boolean {
  return part.kind === 'break' || part.kind === 'paragraph';
}

/** Splits text without line breaks or stage directions into sentences. */
function sentences(text: string): string[] {
  const result: string[] = [];
  let start = 0;
  for (const match of text.matchAll(SENTENCE_END)) {
    const end = match.index + match[0].length;
    if (endsSentence(text, match.index, match[1], end)) {
      push(result, text.slice(start, end));
      start = end;
    }
  }
  push(result, text.slice(start));
  return result;
}

function push(result: string[], sentence: string) {
  const trimmed = sentence.trim();
  if (trimmed) result.push(trimmed);
}

/**
 * Whether the punctuation at `index` ends a sentence. Dots after numbers ("am 3. Oktober"), abbreviations ("Dr.")
 * and acronyms ("z.B.", "U.S.A.", "A.") do not, nor does anything followed by a lower-case word ("Nun… ja").
 */
function endsSentence(text: string, index: number, punctuation: string, end: number): boolean {
  if (/^\s*\p{Ll}/u.test(text.slice(end))) return false;
  if (punctuation !== '.') return true;
  const word = /(\S*)$/.exec(text.slice(0, index))?.[1] ?? '';
  const bare = word.replace(/^["'„“‚‘»«(\[]+/, '');
  if (/^\d+$/.test(bare)) return false;
  if (/^\p{L}$/u.test(bare)) return false;
  if (bare.includes('.')) return false;
  return !ABBREVIATIONS.has(bare.toLowerCase());
}

/**
 * The units the operator steps through: the whole speech of a speaker (stage directions in it do not split it), text
 * without a speaker line by line, and every pause on its own. A light marker always starts a new passage. `first` and
 * `last` are sentence indexes.
 */
export interface Passage {
  first: number;
  last: number;
}

export function passages(script: Script): Passage[] {
  const result: Passage[] = [];
  let open: Passage | null = null;
  let inSpeech = false;
  const close = () => {
    if (open) result.push(open);
    open = null;
  };
  for (const part of script.parts) {
    switch (part.kind) {
      case 'speaker':
        close();
        inSpeech = true;
        break;
      case 'paragraph':
        close();
        inSpeech = false;
        break;
      case 'marker':
        close();
        break;
      case 'pause':
        close();
        result.push({ first: part.index, last: part.index });
        break;
      case 'break':
      case 'note':
        if (!inSpeech) close();
        break;
      case 'line':
        if (open) open.last = part.index;
        else open = { first: part.index, last: part.index };
        break;
    }
  }
  close();
  return result;
}

/** The passage that contains the sentence (or the last one before it); -1 without sentences. */
export function passageOf(list: Passage[], line: number): number {
  let found = -1;
  list.forEach((passage, i) => {
    if (passage.first <= line) found = i;
  });
  return list.length > 0 ? Math.max(0, found) : -1;
}

/** The first sentence of the passage `step` passages away from the one with `line`; null without sentences. */
export function stepPassage(script: Script, line: number, step: number): number | null {
  const list = passages(script);
  if (list.length === 0) return null;
  const target = Math.max(0, Math.min(list.length - 1, passageOf(list, line) + step));
  return list[target].first;
}

/**
 * A place the operator stops at when stepping through the script: the start of the scene, every passage (a pause is
 * one), and every light marker that no speech or pause follows before the next marker – there the light changes and
 * waits, e.g. for a dance described only in stage directions. A marker followed by speech or a pause changes the light
 * together with it.
 *
 * @param line    the sentence it stands at (for a marker: the first sentence after it)
 * @param section the part of the scene whose light is on stage there
 * @param marker  the stop is a light marker, not a speech
 */
export interface Stop {
  line: number;
  section: number;
  marker: boolean;
}

export function stops(script: Script): Stop[] {
  const starts = new Set(passages(script).map((p) => p.first));
  const result: Stop[] = [{ line: 0, section: 0, marker: false }];
  const add = (stop: Stop) => {
    const last = result[result.length - 1];
    if (last.line !== stop.line || last.section !== stop.section) result.push(stop);
  };
  let section = 0;
  /** a marker waiting for its speech; without one before the next marker it is a stop of its own */
  let pending: Stop | null = null;
  for (const part of script.parts) {
    if (part.kind === 'marker') {
      if (pending) add(pending);
      section = part.section;
      pending = { line: part.firstLine, section, marker: true };
    } else if ((part.kind === 'line' || part.kind === 'pause') && starts.has(part.index)) {
      pending = null;
      add({ line: part.index, section, marker: false });
    }
  }
  if (pending) add(pending);
  return result;
}

/** The stop the operator is at (or the last one before the position). */
export function stopOf(list: Stop[], line: number, section: number): number {
  let found = 0;
  list.forEach((stop, i) => {
    if (stop.section < section || (stop.section === section && stop.line <= line)) found = i;
  });
  return found;
}

/** The stop `step` stops away from the position, within the scene. */
export function stepStop(list: Stop[], line: number, section: number, step: number): Stop {
  return list[Math.max(0, Math.min(list.length - 1, stopOf(list, line, section) + step))];
}

/** What to scroll to for a stop: its speech, or the light marker. */
export function stopSelector(stop: Stop): string {
  return stop.marker ? `[data-marker="${stop.section}"]` : `[data-line="${stop.line}"]`;
}

/** The part of the scene a sentence is in: 0 before the first light marker, n after the n-th. */
export function sectionOf(script: Script, line: number): number {
  return script.parts.filter((p) => p.kind === 'marker' && p.firstLine <= line).length;
}

/** How long a first ▼ at the last stop waits for the second one that starts the next scene. */
export const NEXT_SCENE_ARM_MS = 3000;

/**
 * What ▼ (or ↓) does: the next stop; at the last stop of the scene (or in a scene without text) the first press
 * asks and a second one within {@link NEXT_SCENE_ARM_MS} is a GO into the next scene. Nothing when GO is not possible.
 */
export function scriptDownAction(stopIndex: number, stopCount: number, canGo: boolean,
  armed: boolean): 'speech' | 'arm' | 'go' | 'none' {
  if (stopIndex < stopCount - 1) return 'speech';
  if (!canGo) return 'none';
  return armed ? 'go' : 'arm';
}
