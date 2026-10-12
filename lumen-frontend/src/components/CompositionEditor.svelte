<script lang="ts">
  import { untrack } from 'svelte';
  import { ApiError, api } from '../lib/api';
  import { previewOf, rgbToCss } from '../lib/color';
  import {
    compositionFixtures, compositionIcon, cycleMs, effectLabel, isFast, lookMap, moveStart, scenesUsing, startOf,
    stepRows, stepTimes, type StepStart,
  } from '../lib/compositions';
  import { live } from '../lib/live.svelte';
  import { stagePreview } from '../lib/stagePreview.svelte';
  import { notify, showError } from '../lib/toasts.svelte';
  import type { Composition, Fixture, Generator, Group, Look, Mode, Scene } from '../lib/types';
  import EffectForm from './EffectForm.svelte';
  import PreviewStrip from './PreviewStrip.svelte';

  let {
    projectId,
    composition,
    looks,
    scenes,
    fixtures,
    groups,
    folders = [],
    modes,
    startWithGenerator = false,
    onSaved,
  }: {
    projectId: number;
    composition: Composition;
    looks: Look[];
    scenes: Scene[];
    fixtures: Fixture[];
    groups: Group[];
    modes: Map<number, Mode>;
    /** the folders of the project, offered when choosing one */
    folders?: string[];
    /** open the effect form right away, e.g. for a new effect */
    startWithGenerator?: boolean;
    onSaved?: (composition: Composition) => void;
  } = $props();

  const SAVE_DELAY_MS = 350;

  // initial values; later changes of the prop are taken over by the effect below
  let draft = $state<Composition>(untrack(() => structuredClone($state.snapshot(composition)) as Composition));
  let saved = untrack(() => JSON.stringify(composition));
  let errors = $state<string[]>([]);
  let timer: ReturnType<typeof setTimeout> | null = null;
  let addLook = $state('');
  let generating = $state(untrack(() => startWithGenerator));

  const looksById = $derived(lookMap(looks));
  /** looks a step may play: shared ones and the composition's own */
  const playable = $derived(looks.filter((l) => l.owner === null || l.owner === composition.id));
  const shared = $derived(looks.filter((l) => l.owner === null));
  const fixtureIds = $derived(compositionFixtures(draft, looksById));
  const usedIn = $derived(scenesUsing(composition.id, scenes));
  const fast = $derived(isFast(draft));
  const onStage = $derived(stagePreview.status.compositionId === composition.id);
  const rows = $derived(stepRows(draft.steps));

  // a different composition, or a change from elsewhere while nothing is pending here
  $effect(() => {
    const incoming = JSON.stringify(composition);
    if (draft.id !== composition.id || (timer === null && incoming !== saved)) {
      draft = structuredClone($state.snapshot(composition)) as Composition;
      saved = incoming;
      errors = [];
      generating = false;
    }
  });

  // save a moment after the last change
  $effect(() => {
    const current = JSON.stringify(draft);
    if (current === saved || draft.id !== composition.id) return;
    if (timer) clearTimeout(timer);
    timer = setTimeout(save, SAVE_DELAY_MS);
  });

  $effect(() => live.watch(`preview:${composition.id}`));

  async function save() {
    timer = null;
    const snapshot = JSON.stringify(draft);
    try {
      const result = await api.compositions.update(projectId, draft.id, {
        name: draft.name,
        fadeMs: draft.fadeMs,
        holdMs: draft.holdMs,
        playMode: draft.playMode,
        intensityMode: draft.intensityMode,
        steps: draft.steps,
        folder: draft.folder,
      });
      accept(result, snapshot);
    } catch (e) {
      if (e instanceof ApiError && e.status === 400) errors = e.errors.length > 0 ? e.errors : [e.message];
      else showError(e);
    }
  }

  /** Takes over what the server stored; edits made meanwhile stay in the draft. */
  function accept(result: Composition, snapshot = JSON.stringify(draft)) {
    errors = [];
    saved = JSON.stringify(result);
    if (JSON.stringify(draft) === snapshot) draft = structuredClone(result);
    onSaved?.(result);
  }

  /** Saves pending edits before an action on the server. */
  async function flush() {
    if (timer) {
      clearTimeout(timer);
      await save();
    }
  }

  /** Records the programmer as a new step: a new row at the end, or parallel in the given row. */
  async function recordStep(parallelToRow: number | null = null) {
    if (Object.keys(live.programmer).length === 0) {
      errors = ['The programmer is empty – set some fixtures in the Programmer tab first.'];
      return;
    }
    await flush();
    try {
      const result = await api.compositions.recordStep(projectId, composition.id);
      draft = structuredClone(result);
      accept(result);
      // the server appends the step; moving it into the row saves again
      if (parallelToRow !== null) draft.steps = moveStart(draft.steps, draft.steps.length - 1, { with: parallelToRow });
    } catch (e) {
      showError(e);
    }
  }

  async function generate(generator: Generator) {
    await flush();
    try {
      const result = await api.compositions.generate(projectId, composition.id, generator);
      draft = structuredClone(result);
      accept(result);
      generating = false;
      notify(`${result.steps.length} steps generated`);
    } catch (e) {
      if (e instanceof ApiError && e.status === 400) errors = e.errors.length > 0 ? e.errors : [e.message];
      else showError(e);
    }
  }

  function toggleStage() {
    if (onStage) stagePreview.stop();
    else stagePreview.showComposition(projectId, composition.id);
  }

  // --- steps -------------------------------------------------------------------------------------------------

  function appendLook() {
    const id = Number(addLook);
    addLook = '';
    if (id) draft.steps = [...draft.steps, { lookId: id, fadeMs: null, holdMs: null, parallel: false }];
  }

  /** Swaps a whole row with the row above or below. */
  function moveRow(row: number, by: number) {
    const other = row + by;
    if (other < 0 || other >= rows.length) return;
    const grouped = rows.map((r) => r.map((i) => draft.steps[i]));
    [grouped[row], grouped[other]] = [grouped[other], grouped[row]];
    draft.steps = grouped.flat();
  }

  /** Repeats a step: a copy of a row's first step becomes a new row after it, a parallel step a copy in its row. */
  function duplicateStep(index: number) {
    const steps = [...draft.steps];
    const step = steps[index];
    if (step.parallel) {
      steps.splice(index + 1, 0, { ...step });
    } else {
      const row = rows.find((r) => r[0] === index)!;
      steps.splice(row[row.length - 1] + 1, 0, { ...step });
    }
    draft.steps = steps;
  }

  function removeStep(index: number) {
    const steps = draft.steps.filter((_, i) => i !== index);
    // the next step of the row takes over its place and times
    if (!draft.steps[index].parallel && steps[index]?.parallel) {
      steps[index] = { ...steps[index], parallel: false, fadeMs: draft.steps[index].fadeMs,
        holdMs: draft.steps[index].holdMs };
    }
    draft.steps = steps;
  }

  function startValue(index: number): string {
    const start = startOf(draft.steps, index);
    return 'with' in start ? `with:${start.with}` : `after:${start.after}`;
  }

  function setStart(index: number, value: string) {
    const [kind, row] = value.split(':');
    const start: StepStart = kind === 'with' ? { with: Number(row) } : { after: Number(row) };
    draft.steps = moveStart(draft.steps, index, start);
  }

  /** A row by its looks, e.g. “2 Red + Blue”. */
  function rowLabel(row: number): string {
    const names = rows[row].map((i) => looksById.get(draft.steps[i].lookId)?.name ?? '?');
    return `${row + 1} ${names.join(' + ')}`;
  }

  /** Puts the look of a step into the programmer, to change it and then “Update” it. */
  async function loadLook(lookId: number) {
    try {
      await api.looks.load(projectId, lookId);
    } catch (e) {
      showError(e);
    }
  }

  async function updateLook(lookId: number) {
    try {
      await api.looks.record(projectId, lookId);
      notify(`“${looksById.get(lookId)?.name}” updated from the programmer`);
    } catch (e) {
      showError(e);
    }
  }

  function lookSwatches(look: Look | undefined): { color: string; intensity: number }[] {
    if (!look) return [];
    return Object.entries(look.values)
      .map(([id, values]) => {
        const mode = modes.get(Number(id));
        return mode ? previewOf(mode, values) : null;
      })
      .filter((preview) => preview !== null && preview.intensity > 0.01)
      .slice(0, 12)
      .map((preview) => ({ color: rgbToCss(preview!.color), intensity: preview!.intensity }));
  }

  /** How many compositions play a shared look, for the warning when updating it. */
  function playedBy(look: Look | undefined): string {
    if (!look || look.owner !== null) return 'Replace the look of this step with the programmer values';
    return 'Shared look – the change shows everywhere it is played';
  }

  // --- helpers -----------------------------------------------------------------------------------------------

  function seconds(ms: number | null): number | null {
    return ms === null ? null : Math.round(ms) / 1000;
  }

  function millis(s: number | null | undefined): number | null {
    return s === null || s === undefined || Number.isNaN(Number(s)) ? null : Math.max(0, Math.round(Number(s) * 1000));
  }

  function targetLabel(generator: Generator): string {
    if (generator.target.groupId != null) {
      return `group “${groups.find((g) => g.id === generator.target.groupId)?.name ?? '?'}”`;
    }
    return `${generator.target.fixtureIds?.length ?? 0} fixtures`;
  }
</script>

<div class="editor">
  <header class="head">
    <span class="icon" aria-hidden="true">{compositionIcon(draft)}</span>
    <label class="name">
      <span class="sr-only">Name</span>
      <input bind:value={draft.name} aria-label="Name" />
    </label>
    <label class="folder" title="The folder the composition is listed in">
      <span class="dim">📁</span>
      <input list="composition-folders" bind:value={draft.folder} maxlength="40" placeholder="Manual"
        aria-label="Folder" />
      <datalist id="composition-folders">{#each folders as folder (folder)}<option value={folder}></option>{/each}</datalist>
    </label>
    {#if fast}
      <span class="badge warning" title="Fast flashing can trigger photosensitive epilepsy – it may have to be announced">
        ⚡ fast flashing
      </span>
    {/if}
    <button class="btn small" class:primary={onStage} type="button" onclick={toggleStage}
      title="Play this composition on the real stage, above the playback">
      {onStage ? '■ Stop showing' : '▶ Show on stage'}
    </button>
  </header>

  <PreviewStrip {fixtureIds} values={live.previews[composition.id]} {modes} {fixtures} />

  {#if errors.length > 0}
    <ul class="errors" role="alert">{#each errors as error, i (i)}<li>{error}</li>{/each}</ul>
  {/if}

  <section>
    <h4>Timing</h4>
    <div class="grid">
      <label class="field">Fade into each step (s)
        <input type="number" min="0" step="0.1" bind:value={() => seconds(draft.fadeMs), (v) => (draft.fadeMs = millis(v) ?? 0)} /></label>
      <label class="field">Hold each step (s)
        <input type="number" min="0" step="0.1" bind:value={() => seconds(draft.holdMs), (v) => (draft.holdMs = millis(v) ?? 0)} /></label>
      <label class="field">Play
        <select bind:value={draft.playMode}>
          <option value="loop">Loop</option>
          <option value="ping_pong">Ping-pong</option>
          <option value="once">Once – the last step stays</option>
        </select></label>
      <label class="field">Intensity
        <select bind:value={draft.intensityMode}>
          <option value="absolute">Sets the intensity</option>
          <option value="relative">Scales the light below (e.g. flicker)</option>
        </select></label>
    </div>
    {#if draft.steps.length > 1}
      <p class="hint">One round: {(cycleMs(draft) / 1000).toFixed(1)} s. Each row can have its own times below.</p>
    {/if}
  </section>

  <section>
    <div class="section-head">
      <h4>Steps</h4>
      <button class="btn small record" type="button" onclick={() => recordStep()}
        title="Records the programmer as a new step with a look of its own">⏺ Record step</button>
    </div>
    {#if draft.steps.length === 0}
      <p class="hint">Record the programmer as a step, add a shared look, or generate steps from an effect.</p>
    {/if}
    <ol class="rows">
      {#each rows as row, r (r)}
        {@const leader = draft.steps[row[0]]}
        {@const times = stepTimes(draft, leader)}
        <li class="row" class:parallel={row.length > 1}>
          <div class="row-head">
            <span class="mono row-number">{r + 1}</span>
            <span class="dim small start-label">{r === 0 ? 'Begin' : `after ${r}`}</span>
            <label class="mini" title="Fade into this row – empty = {seconds(draft.fadeMs)} s">Fade
              <input type="number" min="0" step="0.1" placeholder={String(seconds(draft.fadeMs))}
                bind:value={() => seconds(leader.fadeMs), (v) => (draft.steps[row[0]].fadeMs = millis(v))} /></label>
            <label class="mini" title="Hold of this row – empty = {seconds(draft.holdMs)} s">Hold
              <input type="number" min="0" step="0.1" placeholder={String(seconds(draft.holdMs))}
                bind:value={() => seconds(leader.holdMs), (v) => (draft.steps[row[0]].holdMs = millis(v))} /></label>
            <span class="dim small total">{((times.fadeMs + times.holdMs) / 1000).toFixed(1)} s</span>
            <span class="row-actions">
              <button class="btn small ghost" type="button" onclick={() => recordStep(r)}
                title="Records the programmer as a look that plays in parallel in this row">⏺ ∥</button>
              <button class="btn small ghost" type="button" onclick={() => moveRow(r, -1)} aria-label="Move row up">↑</button>
              <button class="btn small ghost" type="button" onclick={() => moveRow(r, 1)} aria-label="Move row down">↓</button>
            </span>
          </div>
          <div class="cards">
            {#each row as i (i)}
              {@const step = draft.steps[i]}
              {@const look = looksById.get(step.lookId)}
              <div class="card">
                <div class="card-top">
                  <span class="mono step-number">{i + 1}</span>
                  <span class="swatches" aria-hidden="true">
                    {#each lookSwatches(look) as swatch, j (j)}
                      <span class="swatch" style="--color: {swatch.color}; --intensity: {swatch.intensity}"></span>
                    {:else}<span class="dim small">dark</span>{/each}
                  </span>
                </div>
                <label class="look">
                  <span class="sr-only">Look of step {i + 1}</span>
                  <select bind:value={() => String(step.lookId), (v) => (step.lookId = Number(v))}>
                    {#if !look}<option value={String(step.lookId)}>(missing look)</option>{/if}
                    {#each playable as option (option.id)}
                      <option value={String(option.id)}>{option.owner === null ? '◆ ' : ''}{option.name}</option>
                    {/each}
                  </select>
                </label>
                <label class="start">Start
                  <select value={startValue(i)} onchange={(e) => setStart(i, e.currentTarget.value)}>
                    {#each rows as _, k (k)}
                      <option value="with:{k}">{k === 0 ? 'Begin' : `with ${rowLabel(k)}`}</option>
                    {/each}
                    {#each rows as _, k (k)}
                      <option value="after:{k}">after {rowLabel(k)}</option>
                    {/each}
                  </select>
                </label>
                <span class="step-actions">
                  <button class="btn small ghost" type="button" onclick={() => loadLook(step.lookId)} title="Load the look into the programmer">Load</button>
                  <button class="btn small ghost" type="button" onclick={() => updateLook(step.lookId)} title={playedBy(look)}>Update</button>
                  <button class="btn small ghost" type="button" onclick={() => duplicateStep(i)} aria-label="Repeat step">⧉</button>
                  <button class="btn small ghost danger" type="button" onclick={() => removeStep(i)} aria-label="Delete step">✕</button>
                </span>
              </div>
            {/each}
          </div>
        </li>
      {/each}
    </ol>
    <label class="field">
      <span class="sr-only">Add a shared look as step</span>
      <select bind:value={addLook} onchange={appendLook} disabled={shared.length === 0}>
        <option value="">{shared.length === 0 ? 'Shared looks are made in the Looks tab' : '+ Add a shared look as step…'}</option>
        {#each shared as option (option.id)}<option value={String(option.id)}>◆ {option.name}</option>{/each}
      </select>
    </label>
    <p class="hint">
      ◆ marks shared looks: changing one changes every composition that plays it. Steps side by side run in parallel
      with the times of their row – choose “Start” to move a step.
    </p>
  </section>

  <section>
    <div class="section-head">
      <h4>Generator</h4>
      {#if !generating}
        <button class="btn small" type="button" onclick={() => (generating = true)}>
          {composition.generator ? 'Change & generate again…' : '✦ Generate from an effect…'}
        </button>
      {/if}
    </div>
    {#if composition.generator && !generating}
      <p class="hint">
        Generated from {effectLabel(composition.generator.kind)} on {targetLabel(composition.generator)}.
        Generating again replaces the steps.
      </p>
    {:else if !generating}
      <p class="hint">Chase, color cycle, rainbow, wave or flicker – made into ordinary steps you can change.</p>
    {/if}
    {#if generating}
      <EffectForm generator={composition.generator} {fixtures} {groups} {modes} replaces={draft.steps.length > 0}
        ongenerate={generate} />
      <button class="btn small ghost" type="button" onclick={() => (generating = false)}>Cancel</button>
    {/if}
  </section>

  <p class="hint">
    {#if usedIn.length === 0}
      In no scene yet.
    {:else}
      In {usedIn.length === 1 ? 'scene' : 'scenes'} {usedIn.map((s) => s.number).join(', ')} – changes show in all of them.
    {/if}
  </p>
</div>

<style>
  .editor {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .head {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  .folder {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .folder input {
    width: 150px;
    min-height: 30px;
    padding: 0 8px;
    border-radius: 7px;
    border: 1px solid var(--border-strong);
    background: rgba(0, 0, 0, 0.25);
    color: var(--text);
    font: inherit;
    font-size: 13px;
  }

  .icon {
    font-size: 22px;
  }

  .name {
    flex: 1;
    min-width: 160px;
  }

  .name input {
    width: 100%;
    min-height: 38px;
    padding: 0 10px;
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
    background: var(--panel-raised);
    color: var(--text);
    font: inherit;
    font-weight: 600;
  }

  section {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  h4 {
    margin: 0;
    font-size: 12px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--text-dim);
  }

  .section-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
  }

  .grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
    gap: 10px 14px;
  }

  .rows {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .row {
    display: flex;
    flex-direction: column;
    gap: 6px;
    padding: 6px 8px 8px;
    border-radius: var(--radius-sm);
    background: var(--panel-raised);
    border-left: 3px solid transparent;
  }

  .row.parallel {
    border-left-color: var(--accent);
  }

  .row-head {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  .row-number {
    min-width: 18px;
    font-weight: 600;
  }

  .start-label {
    min-width: 54px;
  }

  .row-actions {
    display: flex;
    margin-left: auto;
  }

  .cards {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
    gap: 6px;
  }

  .card {
    display: flex;
    flex-direction: column;
    gap: 6px;
    padding: 6px 8px;
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
    background: var(--panel);
  }

  .card-top {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .start {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;
    color: var(--text-dim);
  }

  .start select {
    flex: 1;
    min-width: 0;
  }

  .step-number {
    width: 20px;
    text-align: right;
    color: var(--text-dim);
  }

  .swatches {
    display: flex;
    gap: 2px;
    min-width: 60px;
  }

  .swatch {
    width: 10px;
    height: 16px;
    border-radius: 3px;
    background: color-mix(in srgb, var(--color) calc(var(--intensity) * 100%), #111);
  }

  .look {
    min-width: 0;
  }

  .look select {
    width: 100%;
  }

  .mini {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    color: var(--text-dim);
  }

  .mini input {
    width: 58px;
  }

  .total {
    width: 42px;
  }

  .step-actions {
    display: flex;
    flex-wrap: wrap;
  }

  .errors {
    margin: 0;
    padding: 8px 12px 8px 28px;
    border-radius: var(--radius-sm);
    background: var(--danger-soft);
    color: var(--danger);
  }

  .hint {
    margin: 0;
    font-size: 12px;
    color: var(--text-dim);
  }

  .small {
    font-size: 11px;
  }

  .sr-only {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip: rect(0 0 0 0);
  }
</style>
