import { MARKER, MAX_SPEAKER_LENGTH, NOTE, SPEAKER, speakerHue } from './script';

/** What a piece of the script text is, for coloring it in the editor. */
export type HighlightKind = 'text' | 'speaker' | 'direction' | 'marker' | 'syntax';

/** A piece of the text; `hue` is the speaker's color (as in show mode). */
export interface Segment {
  text: string;
  kind: HighlightKind;
  hue?: number;
}

/**
 * The script split into colored pieces that put together give exactly the text again – so a colored copy can lie
 * under the editor without moving a single character. Same rules as the script view: `NAME:` at the start of a line,
 * `**…**` stage directions, `//## Name (info)##` light markers.
 */
export function highlightScript(text: string): Segment[] {
  const kinds: HighlightKind[] = new Array(text.length).fill('text');
  const hues: (number | undefined)[] = new Array(text.length).fill(undefined);
  const mark = (from: number, to: number, kind: HighlightKind, hue?: number) => {
    for (let i = from; i < to; i++) {
      kinds[i] = kind;
      hues[i] = hue;
    }
  };

  for (const match of text.matchAll(MARKER)) {
    if (match[1].trim()) mark(match.index, match.index + match[0].length, 'marker');
  }
  for (const match of text.matchAll(NOTE)) {
    const start = match.index;
    const end = start + match[0].length;
    if (kinds.slice(start, end).includes('marker')) continue;
    mark(start, start + 2, 'syntax');
    mark(start + 2, end - 2, 'direction');
    mark(end - 2, end, 'syntax');
  }
  let position = 0;
  for (const line of text.split('\n')) {
    const speaker = kinds[position] === 'text' ? SPEAKER.exec(line) : null;
    if (speaker && speaker[1].length <= MAX_SPEAKER_LENGTH) {
      const start = position + line.indexOf(speaker[1]);
      mark(start, start + speaker[1].length, 'speaker', speakerHue(speaker[1]));
      mark(position + speaker[0].length - 1, position + speaker[0].length, 'syntax');
    }
    position += line.length + 1;
  }

  const segments: Segment[] = [];
  for (let i = 0; i < text.length; i++) {
    const last = segments[segments.length - 1];
    if (last && last.kind === kinds[i] && last.hue === hues[i]) last.text += text[i];
    else segments.push({ text: text[i], kind: kinds[i], hue: hues[i] });
  }
  return segments;
}
