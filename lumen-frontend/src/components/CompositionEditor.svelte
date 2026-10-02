<script lang="ts">
  import { untrack } from 'svelte';
  import { ApiError, api } from '../lib/api';
  import { previewOf, rgbToCss } from '../lib/color';
  import { isFast, kindIcon, kindLabel, targetFixtures, unsuitableFixtures } from '../lib/compositions';
  import { live } from '../lib/live.svelte';
  import { showError } from '../lib/toasts.svelte';
  import type { AttributeValues, Composition, CompositionStep, Fixture, Group, Mode } from '../lib/types';
  import PreviewStrip from './PreviewStrip.svelte';

  let {
    projectId,
    composition,
    fixtures,
    groups,
    modes,
    onSaved,
  }: {
    projectId: number;
    composition: Composition;
    fixtures: Fixture[];
    groups: Group[];
    modes: Map<number, Mode>;
    onSaved?: (composition: Composition) => void;
  } = $props();

  const SAVE_DELAY_MS = 350;

  // initial values; later changes of the prop are taken over by the effect below
  let draft = $state<Composition>(untrack(() => structuredClone($state.snapshot(composition))));
  let saved = untrack(() => JSON.stringify(composition));
  let stageCompositionId = $state<number | null>(null);
  let errors = $state<string[]>([]);
  let timer: ReturnType<typeof setTimeout> | null = null;
  let addFixture = $state('');

  const isCopy = $derived(composition.sceneId !== null);
  const p = $derived(draft.params as any);
  const sortedFixtures = $derived([...fixtures].sort((a, b) => a.universeId - b.universeId || a.address - b.address));
  const byId = $derived(new Map(fixtures.map((f) => [f.id, f])));
  const targetIds = $derived(targetFixtures(draft, groups));
  const unsuitable = $derived(unsuitableFixtures(draft.kind, targetIds, modes));
  const fast = $derived(isFast(draft));
  const onStage = $derived(stageCompositionId === composition.id);

  // a different composition, or a change from elsewhere while nothing is pending here
  $effect(() => {
    const incoming = JSON.stringify(composition);
    if (draft.id !== composition.id || (timer === null && incoming !== saved)) {
      draft = structuredClone($state.snapshot(composition));
      saved = incoming;
      errors = [];
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

  $effect(() => {
    void live.compositionsRevision;
    api.compositions.stagePreview().then((s) => (stageCompositionId = s.compositionId)).catch(() => {});
  });

  async function save() {
    timer = null;
    const request = {
      name: draft.name,
      kind: draft.kind,
      params: draft.params,
      target: draft.kind === 'steps' ? null : draft.target,
      steps: draft.kind === 'steps' ? draft.steps : null,
      level: draft.level,
      speedFactor: draft.speedFactor,
      restart: draft.restart,
    };
    const snapshot = JSON.stringify(draft);
    try {
      const result = await api.compositions.update(projectId, draft.id, request);
      errors = [];
      saved = JSON.stringify(result);
      if (JSON.stringify(draft) === snapshot) draft = structuredClone(result);
      onSaved?.(result);
    } catch (e) {
      if (e instanceof ApiError && e.status === 400) errors = e.errors.length > 0 ? e.errors : [e.message];
      else showError(e);
    }
  }

  async function toggleStage() {
    try {
      const result = onStage
        ? await api.compositions.stopStagePreview()
        : await api.compositions.showOnStage(projectId, composition.id);
      stageCompositionId = result.compositionId;
    } catch (e) {
      showError(e);
    }
  }

  // --- target ------------------------------------------------------------------------------------------------

  function useGroup(groupId: number) {
    draft.target = { groupId, fixtureIds: null };
  }

  function useFixtures() {
    draft.target = { groupId: null, fixtureIds: [...targetIds] };
  }

  function moveFixture(index: number, step: number) {
    const ids = [...(draft.target?.fixtureIds ?? [])];
    const other = index + step;
    if (other < 0 || other >= ids.length) return;
    [ids[index], ids[other]] = [ids[other], ids[index]];
    draft.target = { groupId: null, fixtureIds: ids };
  }

  function removeFixture(id: number) {
    draft.target = { groupId: null, fixtureIds: (draft.target?.fixtureIds ?? []).filter((f) => f !== id) };
  }

  function appendFixture() {
    const id = Number(addFixture);
    addFixture = '';
    if (!id) return;
    draft.target = { groupId: null, fixtureIds: [...(draft.target?.fixtureIds ?? []), id] };
  }

  // --- steps -------------------------------------------------------------------------------------------------

  /** Programmer values of all fixtures that have some. */
  function programmerLook(): Record<number, AttributeValues> {
    const look: Record<number, AttributeValues> = {};
    for (const [id, values] of Object.entries(live.programmer)) {
      if (Object.keys(values).length > 0) look[Number(id)] = { ...values };
    }
    return look;
  }

  function recordStep() {
    const look = programmerLook();
    if (Object.keys(look).length === 0) {
      errors = ['The programmer is empty – set some fixtures in the Programmer tab first.'];
      return;
    }
    draft.steps = [...(draft.steps ?? []), { holdMs: 1000, fadeMs: 500, values: look }];
  }

  function updateStep(index: number) {
    const steps = [...(draft.steps ?? [])];
    steps[index] = { ...steps[index], values: programmerLook() };
    draft.steps = steps;
  }

  function moveStep(index: number, step: number) {
    const steps = [...(draft.steps ?? [])];
    const other = index + step;
    if (other < 0 || other >= steps.length) return;
    [steps[index], steps[other]] = [steps[other], steps[index]];
    draft.steps = steps;
  }

  function duplicateStep(index: number) {
    const steps = [...(draft.steps ?? [])];
    steps.splice(index + 1, 0, structuredClone($state.snapshot(steps[index])) as CompositionStep);
    draft.steps = steps;
  }

  function removeStep(index: number) {
    draft.steps = (draft.steps ?? []).filter((_, i) => i !== index);
  }

  /** Puts exactly the step's look into the programmer, to change it and then "Update" the step. */
  async function loadStep(step: CompositionStep) {
    try {
      await api.programmer.clearAll();
      live.setValues(structuredClone($state.snapshot(step.values)) as Record<number, AttributeValues>);
    } catch (e) {
      showError(e);
    }
  }

  function stepSwatches(step: CompositionStep): string[] {
    return Object.entries(step.values)
      .map(([id, values]) => {
        const mode = modes.get(Number(id));
        return mode ? previewOf(mode, values) : null;
      })
      .filter((preview) => preview !== null && preview.intensity > 0.01)
      .slice(0, 12)
      .map((preview) => `${rgbToCss(preview!.color)}|${preview!.intensity}`);
  }

  // --- helpers -----------------------------------------------------------------------------------------------

  function seconds(ms: number): number {
    return Math.round(ms) / 1000;
  }

  function millis(s: number): number {
    return Math.max(0, Math.round(Number(s) * 1000));
  }

  function percent(value: number): string {
    return `${Math.round(value * 100)} %`;
  }
</script>

<div class="editor">
  <header class="head">
    <span class="icon" aria-hidden="true">{kindIcon(draft.kind)}</span>
    <label class="name">
      <span class="sr-only">Name</span>
      <input bind:value={draft.name} aria-label="Name" />
    </label>
    <span class="badge">{kindLabel(draft.kind)}</span>
    {#if fast}
      <span class="badge warning" title="Fast flashing can trigger photosensitive epilepsy – it may have to be announced">
        ⚡ fast flashing
      </span>
    {/if}
    <button class="btn small" class:primary={onStage} type="button" onclick={toggleStage}
      title="Play this composition on the real stage, above the playback">
      {onStage ? '■ Stop on stage' : '▶ Show on stage'}
    </button>
  </header>

  <PreviewStrip fixtureIds={targetIds} values={live.previews[composition.id]} {modes} {fixtures} />

  {#if errors.length > 0}
    <ul class="errors" role="alert">{#each errors as error, i (i)}<li>{error}</li>{/each}</ul>
  {/if}
  {#if unsuitable.length > 0}
    <p class="hint warning-text">
      {draft.kind === 'color_cycle' || draft.kind === 'rainbow' ? 'Cannot show colors' : 'Have no dimmer'}:
      {unsuitable.map((id) => byId.get(id)?.name ?? id).join(', ')}
    </p>
  {/if}

  {#if draft.kind !== 'steps'}
    <section>
      <h4>Target</h4>
      <div class="choice" role="radiogroup" aria-label="Target">
        <label><input type="radio" checked={draft.target?.groupId != null} disabled={groups.length === 0}
          onchange={() => groups[0] && useGroup(groups[0].id)} /> Group</label>
        <label><input type="radio" checked={draft.target?.groupId == null} onchange={useFixtures} /> Fixtures in order</label>
      </div>
      {#if draft.target?.groupId != null}
        <label class="field">Group
          <select value={draft.target.groupId} onchange={(e) => useGroup(Number(e.currentTarget.value))}>
            {#each groups as group (group.id)}<option value={group.id}>{group.name}</option>{/each}
          </select>
          <span class="hint">Follows the group: new members and a new order apply automatically.</span>
        </label>
      {:else}
        <ol class="order">
          {#each draft.target?.fixtureIds ?? [] as id, i (id)}
            <li>
              <span class="mono dim">{i + 1}</span>
              <span class="grow">{byId.get(id)?.name ?? `#${id}`}</span>
              <button class="btn small ghost" type="button" onclick={() => moveFixture(i, -1)} aria-label="Move up">↑</button>
              <button class="btn small ghost" type="button" onclick={() => moveFixture(i, 1)} aria-label="Move down">↓</button>
              <button class="btn small ghost" type="button" onclick={() => removeFixture(id)} aria-label="Remove">✕</button>
            </li>
          {/each}
        </ol>
        <label class="field">Add fixture
          <select bind:value={addFixture} onchange={appendFixture}>
            <option value="">Choose…</option>
            {#each sortedFixtures.filter((f) => !(draft.target?.fixtureIds ?? []).includes(f.id)) as f (f.id)}
              <option value={String(f.id)}>{f.name}</option>
            {/each}
          </select>
        </label>
      {/if}
    </section>
  {/if}

  <section>
    <h4>Settings</h4>
    <div class="grid">
      {#if draft.kind === 'chase'}
        <label class="field">Time per step (s)
          <input type="number" min="0.02" step="0.05" bind:value={() => seconds(p.stepMs), (v) => (p.stepMs = millis(v))} /></label>
        <label class="field">Fixtures on at once
          <input type="number" min="1" step="1" bind:value={p.width} /></label>
        <label class="field">Direction
          <select bind:value={p.direction}>
            <option value="forward">Forward</option><option value="backward">Backward</option>
            <option value="ping_pong">Ping-pong</option><option value="random">Random</option>
          </select></label>
        <label class="field">Soft transitions · {percent(p.softness)}
          <input type="range" min="0" max="1" step="0.05" bind:value={p.softness} /></label>
        <label class="field">Off level · {percent(p.min)}
          <input type="range" min="0" max="1" step="0.01" bind:value={p.min} /></label>
        <label class="field">On level · {percent(p.max)}
          <input type="range" min="0" max="1" step="0.01" bind:value={p.max} /></label>
      {:else if draft.kind === 'color_cycle'}
        <div class="field wide">Colors
          <div class="colors">
            {#each p.colors as color, i (i)}
              <span class="color">
                <input type="color" value={color} aria-label="Color {i + 1}"
                  onchange={(e) => (p.colors[i] = e.currentTarget.value)} />
                {#if p.colors.length > 2}
                  <button class="btn small ghost" type="button" aria-label="Remove color"
                    onclick={() => (p.colors = p.colors.filter((_: string, j: number) => j !== i))}>✕</button>
                {/if}
              </span>
            {/each}
            <button class="btn small" type="button" onclick={() => (p.colors = [...p.colors, '#ffffff'])}>+ Color</button>
          </div>
        </div>
        <label class="field">Hold (s)
          <input type="number" min="0" step="0.1" bind:value={() => seconds(p.holdMs), (v) => (p.holdMs = millis(v))} /></label>
        <label class="field">Fade (s)
          <input type="number" min="0" step="0.1" bind:value={() => seconds(p.fadeMs), (v) => (p.fadeMs = millis(v))} /></label>
        <label class="field">Spread · {percent(p.spread)}
          <input type="range" min="0" max="1" step="0.05" bind:value={p.spread} /></label>
        <label class="field">Intensity without base light · {percent(p.intensity)}
          <input type="range" min="0" max="1" step="0.01" bind:value={p.intensity} /></label>
      {:else if draft.kind === 'rainbow'}
        <label class="field">Time per round (s)
          <input type="number" min="0.1" step="0.5" bind:value={() => seconds(p.cycleMs), (v) => (p.cycleMs = millis(v))} /></label>
        <label class="field">Saturation · {percent(p.saturation)}
          <input type="range" min="0" max="1" step="0.05" bind:value={p.saturation} /></label>
        <label class="field">Spread · {percent(p.spread)}
          <input type="range" min="0" max="1" step="0.05" bind:value={p.spread} /></label>
        <label class="field">Intensity without base light · {percent(p.intensity)}
          <input type="range" min="0" max="1" step="0.01" bind:value={p.intensity} /></label>
      {:else if draft.kind === 'wave'}
        <label class="field">Shape
          <select bind:value={p.shape}>
            <option value="sine">Sine</option><option value="triangle">Triangle</option>
            <option value="sawtooth">Sawtooth</option><option value="square">Square</option>
          </select></label>
        <label class="field">Period (s)
          <input type="number" min="0.05" step="0.1" bind:value={() => seconds(p.periodMs), (v) => (p.periodMs = millis(v))} /></label>
        <label class="field">Low · {percent(p.min)}
          <input type="range" min="0" max="1" step="0.01" bind:value={p.min} /></label>
        <label class="field">High · {percent(p.max)}
          <input type="range" min="0" max="1" step="0.01" bind:value={p.max} /></label>
        <label class="field">Spread · {percent(p.spread)}
          <input type="range" min="0" max="1" step="0.05" bind:value={p.spread} /></label>
      {:else if draft.kind === 'flicker'}
        <label class="field">Character
          <select bind:value={p.character}>
            <option value="candle">Candle</option><option value="fire">Fire</option>
            <option value="broken_tube">Broken tube</option>
          </select></label>
        <label class="field">Strength · {percent(p.strength)}
          <input type="range" min="0" max="1" step="0.05" bind:value={p.strength} /></label>
        <label class="field">Speed · {p.speed.toFixed(1)}×
          <input type="range" min="0.1" max="4" step="0.1" bind:value={p.speed} /></label>
        <label class="field">Variant
          <span class="row-inline">
            <input type="number" step="1" bind:value={p.seed} />
            <button class="btn small" type="button" onclick={() => (p.seed = Math.floor(Math.random() * 100000))}
              aria-label="Other variant">🎲</button>
          </span></label>
      {:else if draft.kind === 'steps'}
        <label class="field">Play
          <select bind:value={p.playMode}>
            <option value="loop">Loop</option><option value="ping_pong">Ping-pong</option>
          </select></label>
      {/if}
    </div>
  </section>

  {#if draft.kind === 'steps'}
    <section>
      <div class="section-head">
        <h4>Steps</h4>
        <button class="btn small record" type="button" onclick={recordStep}
          title="Adds the current programmer values as a new step">⏺ Record step</button>
      </div>
      {#if (draft.steps ?? []).length === 0}
        <p class="hint">Set a look in the Programmer, then record it as a step. Fixtures missing in a step are dark.</p>
      {/if}
      <ol class="steps">
        {#each draft.steps ?? [] as step, i (i)}
          <li>
            <span class="mono step-number">{i + 1}</span>
            <span class="swatches" aria-hidden="true">
              {#each stepSwatches(step) as swatch, j (j)}
                {@const [color, intensity] = swatch.split('|')}
                <span class="swatch" style="--color: {color}; --intensity: {intensity}"></span>
              {:else}<span class="dim small">dark</span>{/each}
            </span>
            <label class="mini">Fade
              <input type="number" min="0" step="0.1" bind:value={() => seconds(step.fadeMs), (v) => (step.fadeMs = millis(v))} /></label>
            <label class="mini">Hold
              <input type="number" min="0" step="0.1" bind:value={() => seconds(step.holdMs), (v) => (step.holdMs = millis(v))} /></label>
            <span class="step-actions">
              <button class="btn small ghost" type="button" onclick={() => loadStep(step)} title="Load into the programmer">Load</button>
              <button class="btn small ghost" type="button" onclick={() => updateStep(i)} title="Replace with the programmer values">Update</button>
              <button class="btn small ghost" type="button" onclick={() => duplicateStep(i)} aria-label="Duplicate">⧉</button>
              <button class="btn small ghost" type="button" onclick={() => moveStep(i, -1)} aria-label="Move up">↑</button>
              <button class="btn small ghost" type="button" onclick={() => moveStep(i, 1)} aria-label="Move down">↓</button>
              <button class="btn small ghost danger" type="button" onclick={() => removeStep(i)} aria-label="Delete step">✕</button>
            </span>
          </li>
        {/each}
      </ol>
    </section>
  {/if}

  <section>
    <h4>{isCopy ? 'In this scene' : 'Defaults when inserted into a scene'}</h4>
    <div class="grid">
      <label class="field">Level · {percent(draft.level)}
        <input type="range" min="0" max="1" step="0.01" bind:value={draft.level} /></label>
      <label class="field">Tempo · {draft.speedFactor.toFixed(2)}×
        <input type="range" min="0.25" max="4" step="0.05" bind:value={draft.speedFactor} /></label>
      {#if isCopy}
        <label class="check"><input type="checkbox" bind:checked={draft.restart} />
          Restart at GO <span class="hint">(otherwise it continues from the previous scene)</span></label>
      {/if}
    </div>
  </section>
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
    border-radius: var(--radius-sm);
    border: 1px solid var(--border-strong);
    background: rgba(0, 0, 0, 0.25);
    color: var(--text);
    font: inherit;
    font-size: 17px;
    font-weight: 600;
  }

  section {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  h4 {
    margin: 0;
    font-size: 11px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--text-dim);
  }

  .section-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
    gap: 12px 16px;
    align-items: end;
  }

  .wide {
    grid-column: 1 / -1;
  }

  .field input[type='range'] {
    min-height: 24px;
    padding: 0;
    border: 0;
    background: none;
    accent-color: var(--accent);
  }

  .choice {
    display: flex;
    gap: 16px;
    font-size: 13px;
  }

  .choice input,
  .check input {
    accent-color: var(--accent);
  }

  .check {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    grid-column: 1 / -1;
  }

  .order,
  .steps {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .order li,
  .steps li {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 8px;
    border-radius: var(--radius-sm);
    background: var(--panel-raised);
  }

  .grow {
    flex: 1;
  }

  .colors {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    align-items: center;
  }

  .color {
    display: inline-flex;
    align-items: center;
  }

  .color input {
    width: 44px;
    height: 32px;
    padding: 0;
    border: 1px solid var(--border-strong);
    border-radius: 6px;
    background: none;
  }

  .row-inline {
    display: flex;
    gap: 6px;
  }

  .step-number {
    width: 22px;
    color: var(--accent);
    font-weight: 700;
  }

  .swatches {
    flex: 1;
    display: flex;
    gap: 3px;
    flex-wrap: wrap;
    min-width: 60px;
  }

  .swatch {
    width: 12px;
    height: 12px;
    border-radius: 50%;
    background: color-mix(in srgb, var(--color) calc(var(--intensity) * 100%), #111);
    box-shadow: 0 0 calc(8px * var(--intensity)) var(--color);
  }

  .mini {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 11px;
    color: var(--text-dim);
  }

  .mini input {
    width: 58px;
    min-height: 28px;
    padding: 0 6px;
    border-radius: 6px;
    border: 1px solid var(--border-strong);
    background: rgba(0, 0, 0, 0.25);
    color: var(--text);
    font: inherit;
  }

  .step-actions {
    display: flex;
    gap: 2px;
    flex-wrap: wrap;
  }

  .errors {
    margin: 0;
    padding: 8px 12px 8px 28px;
    border-radius: var(--radius-sm);
    background: rgba(255, 77, 109, 0.1);
    color: var(--danger);
    font-size: 13px;
  }

  .hint {
    margin: 0;
    font-size: 12px;
    color: var(--text-faint);
  }

  .warning-text {
    color: var(--warning);
  }

  .small {
    font-size: 12px;
  }

  .sr-only {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip: rect(0 0 0 0);
  }
</style>
