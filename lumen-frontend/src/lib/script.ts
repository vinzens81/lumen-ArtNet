/**
 * The text spoken on stage during a scene, split into the sentences the operator steps through. Text in
 * `**…**` is a stage direction: shown differently and skipped when stepping. A name and a colon at the start of a
 * line (`Maike Schmitz: Hallo Rolf!`) marks who speaks.
 */
export type ScriptPart =
  /** a spoken sentence; `index` counts the sentences of the scene, starting at 0 */
  | { kind: 'line'; text: string; index: number }
  | { kind: 'note'; text: string }
  /** who speaks from here on (until the next speaker or an empty line) */
  | { kind: 'speaker'; name: string }
  /** line break in the text */
  | { kind: 'break' }
  /** empty line in the text */
  | { kind: 'paragraph' };

export interface Script {
  parts: ScriptPart[];
  /** number of spoken sentences */
  lineCount: number;
  noteCount: number;
  /** names of the speakers in order of their first appearance */
  speakers: string[];
}

/**
 * A speaker at the start of a line: one to four words starting with a capital letter, then a colon and a space (or
 * the end of the line) – "Maike Schmitz:", "ROMEO:", "Der Wirt:". "He said: …" is no speaker.
 */
const SPEAKER = /^\s*(\p{Lu}[\p{L}\p{M}'’.-]*(?:[ \t]+\p{Lu}[\p{L}\p{M}'’.-]*){0,3})[ \t]*:(?=\s|$)/u;
const MAX_SPEAKER_LENGTH = 40;

/** A dot after these words does not end a sentence (compared in lower case). */
const ABBREVIATIONS = new Set([
  'dr', 'mr', 'mrs', 'ms', 'prof', 'st', 'jr', 'sr', 'vs', 'no', 'nr', 'ca', 'bzw', 'vgl', 'ggf', 'evtl', 'inkl',
  'hr', 'fr', 'frl', 'abs', 'str',
]);

const NOTE = /\*\*([\s\S]+?)\*\*/g;
/** end of a sentence: punctuation, closing quotes or brackets, then whitespace or the end of the text */
const SENTENCE_END = /([.!?…]+)(["'“”‘’»«)\]]*)(?=\s|$)/g;

export function parseScript(text: string): Script {
  const parts: ScriptPart[] = [];
  let lineCount = 0;
  let noteCount = 0;
  const speakers: string[] = [];
  /** the next text starts a line of the text (where a speaker can stand) */
  let atLineStart = true;

  const addBreak = (kind: 'break' | 'paragraph') => {
    const last = parts[parts.length - 1];
    if (last === undefined) return; // nothing to separate yet
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

  let position = 0;
  for (const match of text.matchAll(NOTE)) {
    addSpoken(text.slice(position, match.index));
    const note = match[1].trim();
    if (note) {
      parts.push({ kind: 'note', text: note });
      noteCount++;
      atLineStart = false;
    }
    position = match.index + match[0].length;
  }
  addSpoken(text.slice(position));

  while (parts.length > 0 && isBreak(parts[parts.length - 1])) parts.pop();
  return { parts, lineCount, noteCount, speakers };
}

/** How the script is laid out: speeches indented under their speaker, stage directions on lines of their own. */
export type ScriptBlock =
  /** what one speaker says; `continued` after a stage direction within the speech (the name is not repeated) */
  | { kind: 'speech'; speaker: string; continued: boolean; parts: ScriptPart[] }
  /** text without a speaker */
  | { kind: 'text'; parts: ScriptPart[] }
  | { kind: 'note'; text: string }
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

/** The sentence `step` sentences away from `line`, kept within the scene; null without sentences. */
export function stepLine(script: Script, line: number, step: number): number | null {
  if (script.lineCount === 0) return null;
  return Math.max(0, Math.min(script.lineCount - 1, line + step));
}
