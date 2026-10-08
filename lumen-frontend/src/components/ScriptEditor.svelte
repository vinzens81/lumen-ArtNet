<script lang="ts">
  import { tick } from 'svelte';
  import { parseScript, SPEAKER } from '../lib/script';
  import { highlightScript } from '../lib/scriptHighlight';

  /**
   * The script of a scene as formatted text: speakers in their colors, stage directions and light markers marked. The
   * text stays plain (`NAME:`, `**…**`, `//## … ##`); a colored copy lies exactly under the transparent text field, so
   * typing, selecting, copying and undo work as in any text field.
   */
  let {
    value = $bindable(''),
    placeholder = '',
    rows = 12,
    label = 'Script',
  }: { value: string; placeholder?: string; rows?: number; label?: string } = $props();

  let textarea: HTMLTextAreaElement | undefined = $state();
  let layer: HTMLElement | undefined = $state();
  let newSpeaker = $state('');

  const segments = $derived(highlightScript(value));
  const speakers = $derived(parseScript(value).speakers);

  function syncScroll() {
    if (textarea && layer) {
      layer.scrollTop = textarea.scrollTop;
      layer.scrollLeft = textarea.scrollLeft;
    }
  }

  /** Replaces a range of the text and selects the given part of the result. */
  async function replace(from: number, to: number, insert: string, select: [number, number] = [insert.length, insert.length]) {
    value = value.slice(0, from) + insert + value.slice(to);
    await tick();
    textarea?.focus();
    textarea?.setSelectionRange(from + select[0], from + select[1]);
    syncScroll();
  }

  function lineAt(position: number): { start: number; end: number } {
    const start = value.lastIndexOf('\n', position - 1) + 1;
    const next = value.indexOf('\n', position);
    return { start, end: next < 0 ? value.length : next };
  }

  /** Puts the speaker at the start of the current line (replacing one that is there). */
  function setSpeaker(name: string) {
    const trimmed = name.trim().toUpperCase();
    if (!textarea || !trimmed) return;
    const { start, end } = lineAt(textarea.selectionStart);
    const line = value.slice(start, end);
    const speaker = SPEAKER.exec(line);
    const rest = (speaker ? line.slice(speaker[0].length) : line).trimStart();
    const text = `${trimmed}: ${rest}`;
    replace(start, end, text, [text.length, text.length]);
    newSpeaker = '';
  }

  /** Marks the selection as a stage direction, or inserts one to type into. */
  function direction() {
    if (!textarea) return;
    const { selectionStart: from, selectionEnd: to } = textarea;
    const selected = value.slice(from, to).trim();
    const text = selected ? `**${selected}**` : '**stage direction**';
    replace(from, to, text, selected ? [text.length, text.length] : [2, text.length - 2]);
  }

  /** Inserts a light marker as a new line after the current one, its name selected to type over. */
  function marker() {
    if (!textarea) return;
    const { end } = lineAt(textarea.selectionStart);
    const name = `Light ${parseScript(value).markers.length + 1}`;
    const text = `${value.length > 0 ? '\n' : ''}//## ${name} (info)##`;
    const offset = text.indexOf(name);
    replace(end, end, text, [offset, offset + name.length]);
  }
</script>

<div class="script-editor">
  <div class="toolbar" role="toolbar" aria-label="Format the script">
    <select aria-label="Speaker of the current line" value=""
      onchange={(e) => { setSpeaker(e.currentTarget.value); e.currentTarget.value = ''; }}>
      <option value="">🗣 Speaker…</option>
      {#each speakers as speaker (speaker)}<option value={speaker}>{speaker}</option>{/each}
    </select>
    <input class="new-speaker" placeholder="New speaker" bind:value={newSpeaker} aria-label="New speaker"
      onkeydown={(e) => { if (e.key === 'Enter') { e.preventDefault(); setSpeaker(newSpeaker); } }} />
    <button class="btn small" type="button" onclick={() => setSpeaker(newSpeaker)} disabled={!newSpeaker.trim()}>
      Set</button>
    <span class="divider" aria-hidden="true"></span>
    <button class="btn small direction-btn" type="button" onclick={direction}
      title="Marks the selection as a stage direction (**…**) – skipped when stepping through the script">
      ✎ Stage direction</button>
    <button class="btn small marker-btn" type="button" onclick={marker}
      title="A light marker on a line of its own (//## Name (info)##): the part after it gets light of its own">
      💡 Light marker</button>
  </div>
  <div class="surface-wrap">
    <div class="surface layer" bind:this={layer} aria-hidden="true">{#each segments as segment, i (i)}<span
        class={segment.kind} style={segment.hue !== undefined ? `--hue: ${segment.hue}` : ''}>{segment.text}</span>{/each}{'\n '}</div>
    <textarea class="surface input" bind:this={textarea} bind:value {rows} {placeholder} aria-label={label}
      spellcheck="true" onscroll={syncScroll} oninput={syncScroll}></textarea>
  </div>
</div>

<style>
  .script-editor {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .toolbar {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 6px;
  }

  .toolbar select,
  .new-speaker {
    min-height: 30px;
    padding: 0 8px;
    border-radius: 7px;
    border: 1px solid var(--border-strong);
    background: rgba(0, 0, 0, 0.25);
    color: var(--text);
    font: inherit;
    font-size: 12px;
  }

  .toolbar select {
    width: auto;
  }

  .new-speaker {
    width: 130px;
  }

  .divider {
    width: 1px;
    height: 20px;
    background: var(--border);
  }

  .direction-btn {
    color: var(--direction);
  }

  .marker-btn {
    color: var(--warning);
  }

  .surface-wrap {
    position: relative;
    border: 1px solid var(--border-strong);
    border-radius: var(--radius-sm);
    background: rgba(0, 0, 0, 0.25);
  }

  .surface-wrap:focus-within {
    border-color: var(--accent);
  }

  /* text field and colored copy share every metric, so each character lies exactly on its copy */
  .surface {
    box-sizing: border-box;
    width: 100%;
    margin: 0;
    padding: 10px 12px;
    border: 0;
    font-family: var(--font);
    font-size: 14px;
    font-weight: 400;
    line-height: 1.6;
    letter-spacing: normal;
    tab-size: 4;
    white-space: pre-wrap;
    overflow-wrap: break-word;
    word-break: normal;
    scrollbar-gutter: stable;
  }

  .layer {
    position: absolute;
    inset: 0;
    overflow: hidden;
    color: var(--text);
    pointer-events: none;
  }

  .input {
    position: relative;
    display: block;
    min-height: 160px;
    resize: vertical;
    overflow-y: auto;
    background: transparent;
    color: transparent;
    caret-color: var(--text);
    outline: none;
  }

  .input::placeholder {
    color: var(--text-faint);
  }

  .input::selection {
    background: var(--accent-soft);
    color: transparent;
  }

  /* only colors and backgrounds: bold or italic would change the width of the letters */
  .speaker {
    border-radius: 3px;
    background: hsl(var(--hue) 70% 60% / 0.18);
    color: hsl(var(--hue) 80% 74%);
  }

  .direction {
    background: var(--direction-soft);
    color: var(--direction);
  }

  .marker {
    background: var(--warning-soft);
    color: var(--warning);
  }

  .syntax {
    color: var(--text-faint);
  }
</style>
