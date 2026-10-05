/**
 * The text spoken on stage during a scene, split into the sentences the operator steps through. Text in
 * `**…**` is a stage direction: shown differently and skipped when stepping.
 */
export type ScriptPart =
  /** a spoken sentence; `index` counts the sentences of the scene, starting at 0 */
  | { kind: 'line'; text: string; index: number }
  | { kind: 'note'; text: string }
  /** line break in the text */
  | { kind: 'break' }
  /** empty line in the text */
  | { kind: 'paragraph' };

export interface Script {
  parts: ScriptPart[];
  /** number of spoken sentences */
  lineCount: number;
  noteCount: number;
}

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
        return;
      }
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
    }
    position = match.index + match[0].length;
  }
  addSpoken(text.slice(position));

  while (parts.length > 0 && isBreak(parts[parts.length - 1])) parts.pop();
  return { parts, lineCount, noteCount };
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
