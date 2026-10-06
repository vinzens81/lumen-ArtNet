<script lang="ts">
  import { untrack } from 'svelte';
  import { defaultParams, EFFECTS, generatorFixtures, unsuitableFixtures } from '../lib/compositions';
  import type { EffectKind, Fixture, Generator, Group, Mode } from '../lib/types';

  /** Effect, target and parameters a generator makes steps from. */
  let {
    generator,
    fixtures,
    groups,
    modes,
    replaces,
    ongenerate,
  }: {
    generator: Generator | null;
    fixtures: Fixture[];
    groups: Group[];
    modes: Map<number, Mode>;
    /** the composition has steps that generating replaces */
    replaces: boolean;
    ongenerate: (generator: Generator) => void;
  } = $props();

  let draft = $state<Generator>(untrack(() => generator
    ? structuredClone($state.snapshot(generator)) as Generator
    : { kind: 'chase', params: defaultParams('chase'), target: groups[0] ? { groupId: groups[0].id } : { fixtureIds: [] },
      perHead: false }));
  let addFixture = $state('');

  const p = $derived(draft.params as any);
  const sortedFixtures = $derived([...fixtures].sort((a, b) => a.universeId - b.universeId || a.address - b.address));
  const byId = $derived(new Map(fixtures.map((f) => [f.id, f])));
  const targetIds = $derived(generatorFixtures(draft, groups));
  const unsuitable = $derived(unsuitableFixtures(draft.kind, targetIds, modes));
  /** heads of the multi-pixel fixtures in the target */
  const targetHeads = $derived(targetIds.reduce((sum, id) => sum + (modes.get(id)?.heads ?? 0), 0));

  function chooseKind(kind: EffectKind) {
    draft.kind = kind;
    draft.params = generator?.kind === kind ? structuredClone($state.snapshot(generator.params)) : defaultParams(kind);
  }

  function useGroup(groupId: number) {
    draft.target = { groupId, fixtureIds: null };
  }

  function useFixtures() {
    draft.target = { groupId: null, fixtureIds: [...targetIds] };
  }

  function moveFixture(index: number, step: number) {
    const ids = [...(draft.target.fixtureIds ?? [])];
    const other = index + step;
    if (other < 0 || other >= ids.length) return;
    [ids[index], ids[other]] = [ids[other], ids[index]];
    draft.target = { groupId: null, fixtureIds: ids };
  }

  function removeFixture(id: number) {
    draft.target = { groupId: null, fixtureIds: (draft.target.fixtureIds ?? []).filter((f) => f !== id) };
  }

  function appendFixture() {
    const id = Number(addFixture);
    addFixture = '';
    if (!id) return;
    draft.target = { groupId: null, fixtureIds: [...(draft.target.fixtureIds ?? []), id] };
  }

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
  <div class="kinds" role="radiogroup" aria-label="Effect">
    {#each EFFECTS as effect (effect.kind)}
      <label class="kind" class:active={draft.kind === effect.kind} title={effect.description}>
        <input type="radio" name="effect-kind" checked={draft.kind === effect.kind} onchange={() => chooseKind(effect.kind)} />
        <span aria-hidden="true">{effect.icon}</span> {effect.label}
      </label>
    {/each}
  </div>

  {#if unsuitable.length > 0}
    <p class="hint warning-text">
      {draft.kind === 'color_cycle' || draft.kind === 'rainbow' ? 'Cannot show colors' : 'Have no dimmer'}:
      {unsuitable.map((id) => byId.get(id)?.name ?? id).join(', ')}
    </p>
  {/if}

    <section>
      <h4>Target</h4>
      <div class="choice" role="radiogroup" aria-label="Target">
        <label><input type="radio" checked={draft.target.groupId != null} disabled={groups.length === 0}
          onchange={() => groups[0] && useGroup(groups[0].id)} /> Group</label>
        <label><input type="radio" checked={draft.target.groupId == null} onchange={useFixtures} /> Fixtures in order</label>
      </div>
      {#if draft.target.groupId != null}
        <label class="field">Group
          <select value={draft.target.groupId} onchange={(e) => useGroup(Number(e.currentTarget.value))}>
            {#each groups as group (group.id)}<option value={group.id}>{group.name}</option>{/each}
          </select>
          <span class="hint">Its members in their order when the steps are generated – generate again after changing the group.</span>
        </label>
      {:else}
        <ol class="order">
          {#each draft.target.fixtureIds ?? [] as id, i (id)}
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
            {#each sortedFixtures.filter((f) => !(draft.target.fixtureIds ?? []).includes(f.id)) as f (f.id)}
              <option value={String(f.id)}>{f.name}</option>
            {/each}
          </select>
        </label>
      {/if}
    </section>

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
        <label class="field">Own intensity · {percent(p.intensity)}
          <input type="range" min="0" max="1" step="0.01" bind:value={p.intensity} /></label>
      {:else if draft.kind === 'rainbow'}
        <label class="field">Time per round (s)
          <input type="number" min="0.1" step="0.5" bind:value={() => seconds(p.cycleMs), (v) => (p.cycleMs = millis(v))} /></label>
        <label class="field">Saturation · {percent(p.saturation)}
          <input type="range" min="0" max="1" step="0.05" bind:value={p.saturation} /></label>
        <label class="field">Spread · {percent(p.spread)}
          <input type="range" min="0" max="1" step="0.05" bind:value={p.spread} /></label>
        <label class="field">Own intensity · {percent(p.intensity)}
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
      {/if}
      {#if targetHeads > 0 || draft.perHead}
        <label class="check"><input type="checkbox" bind:checked={draft.perHead} />
          Each head separately <span class="hint">({targetHeads} heads of pixel fixtures run as elements of their own –
          e.g. a chase along a LED bar)</span></label>
      {/if}
    </div>
  </section>

  <div class="generate">
    <button class="btn primary" type="button" disabled={targetIds.length === 0}
      onclick={() => ongenerate($state.snapshot(draft) as Generator)}>
      ✦ {replaces ? 'Generate steps again' : 'Generate steps'}
    </button>
    <span class="hint">
      {replaces ? 'Replaces the steps (and their own looks) – ' : ''}afterwards every step can be changed like a recorded one.
    </span>
  </div>
</div>

<style>
  .kinds {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .kind {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 6px 10px;
    border: 1px solid var(--border);
    border-radius: 999px;
    background: var(--panel-raised);
    cursor: pointer;
  }

  .kind input {
    position: absolute;
    opacity: 0;
    pointer-events: none;
  }

  .kind.active {
    border-color: var(--accent);
    background: var(--accent-soft);
  }

  .kind:has(input:focus-visible) {
    outline: 2px solid var(--accent);
  }

  .generate {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  .editor {
    display: flex;
    flex-direction: column;
    gap: 16px;
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

  .order {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .order li {
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
</style>
