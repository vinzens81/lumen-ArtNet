import { describe, expect, it } from 'vitest';
import { parseScript, stepLine, type ScriptPart } from './script';

function lines(text: string): string[] {
  return parseScript(text).parts.flatMap((part) => (part.kind === 'line' ? [part.text] : []));
}

describe('parseScript', () => {
  it('splits sentences at . ? ! and …', () => {
    expect(lines('To be. Or not? Never! Well… Then go.')).toEqual(['To be.', 'Or not?', 'Never!', 'Well…', 'Then go.']);
    expect(lines('What?! Wait... Now.')).toEqual(['What?!', 'Wait...', 'Now.']);
  });

  it('keeps closing quotes with their sentence', () => {
    expect(lines('He said: „Komm.“ Sie ging. "Stop!" Done.')).toEqual(['He said: „Komm.“', 'Sie ging.', '"Stop!"', 'Done.']);
  });

  it('does not split at numbers', () => {
    expect(lines('Am 3. Oktober kam er. Es kostet 3.50 Euro. Ende.')).toEqual([
      'Am 3. Oktober kam er.',
      'Es kostet 3.50 Euro.',
      'Ende.',
    ]);
  });

  it('does not split at acronyms and abbreviations', () => {
    expect(lines('Er kam z.B. aus den U.S.A. Danach ging er.')).toEqual(['Er kam z.B. aus den U.S.A. Danach ging er.']);
    expect(lines('Dr. Faust trifft Hr. Wagner, d. h. seinen Diener. Gut.')).toEqual([
      'Dr. Faust trifft Hr. Wagner, d. h. seinen Diener.',
      'Gut.',
    ]);
    expect(lines('Signed J. Smith. Go.')).toEqual(['Signed J. Smith.', 'Go.']);
  });

  it('does not split when the next word starts in lower case', () => {
    expect(lines('Ich… ich weiß nicht. Nein!')).toEqual(['Ich… ich weiß nicht.', 'Nein!']);
  });

  it('ends a sentence at a line break and keeps breaks and paragraphs', () => {
    const script = parseScript('ROMEO\nBut soft\n\n\nJULIET\nAy me.');
    expect(script.parts).toEqual<ScriptPart[]>([
      { kind: 'line', text: 'ROMEO', index: 0 },
      { kind: 'break' },
      { kind: 'line', text: 'But soft', index: 1 },
      { kind: 'paragraph' },
      { kind: 'line', text: 'JULIET', index: 2 },
      { kind: 'break' },
      { kind: 'line', text: 'Ay me.', index: 3 },
    ]);
  });

  it('takes **…** as stage directions that are not counted', () => {
    const script = parseScript('Hark. **Thunder, the lights flicker** Who goes there?\n**Exit**');
    expect(script.parts).toEqual<ScriptPart[]>([
      { kind: 'line', text: 'Hark.', index: 0 },
      { kind: 'note', text: 'Thunder, the lights flicker' },
      { kind: 'line', text: 'Who goes there?', index: 1 },
      { kind: 'break' },
      { kind: 'note', text: 'Exit' },
    ]);
    expect(script.lineCount).toBe(2);
    expect(script.noteCount).toBe(2);
  });

  it('ends a sentence before a stage direction and allows directions over several lines', () => {
    expect(parseScript('Half a sentence **long\ndirection** and the rest.').parts).toEqual<ScriptPart[]>([
      { kind: 'line', text: 'Half a sentence', index: 0 },
      { kind: 'note', text: 'long\ndirection' },
      { kind: 'line', text: 'and the rest.', index: 1 },
    ]);
  });

  it('keeps an unclosed ** as text', () => {
    expect(lines('Bold ** move. Yes.')).toEqual(['Bold ** move.', 'Yes.']);
  });

  it('has no parts for an empty text', () => {
    expect(parseScript('  \n\n ')).toEqual({ parts: [], lineCount: 0, noteCount: 0 });
  });
});

describe('stepLine', () => {
  const script = parseScript('One. Two. Three.');

  it('stays within the scene', () => {
    expect(stepLine(script, 0, 1)).toBe(1);
    expect(stepLine(script, 2, 1)).toBe(2);
    expect(stepLine(script, 0, -1)).toBe(0);
  });

  it('is null without sentences', () => {
    expect(stepLine(parseScript('**Silence**'), 0, 1)).toBeNull();
  });
});
