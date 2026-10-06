<script lang="ts">
  import { api } from '../lib/api';
  import { compositionIcon, effectLabel, isFast, lookMap, overlaps, scenesUsing } from '../lib/compositions';
  import { live } from '../lib/live.svelte';
  import { showError } from '../lib/toasts.svelte';
  import type { Composition, Fixture, Group, Look, Mode, Scene, SceneComposition, SceneLook } from '../lib/types';
  import CompositionEditor from './CompositionEditor.svelte';
  import Modal from './Modal.svelte';

  /**
   * The light of a scene or of one part of it (after a light marker of the script): compositions with level, tempo
   * and restart, and looks used directly.
   */
  let {
    projectId,
    scene,
    marker = null,
    info = '',
    scenes,
    compositions,
    looks,
    fixtures,
    groups,
    modes,
  }: {
    projectId: number;
    scene: Scene;
    scenes: Scene[];
    compositions: Composition[];
    looks: Look[];
    fixtures: Fixture[];
    groups: Group[];
    modes: Map<number, Mode>;
    /** the light marker of the part; null = the start of the scene */
    marker?: string | null;
    /** the text in brackets of the marker */
    info?: string;
  } = $props();

  let uses = $state<SceneComposition[]>([]);
  let lookUses = $state<SceneLook[]>([]);
  let addId = $state('');
  let addLookId = $state('');
  let editing = $state<{ id: number; generate: boolean } | null>(null);

  const byId = $derived(new Map(compositions.map((c) => [c.id, c])));
  const editingComposition = $derived(editing ? byId.get(editing.id) ?? null : null);
  const available = $derived(compositions.filter((c) => !uses.some((u) => u.compositionId === c.id)));
  const warnings = $derived(overlaps(uses.flatMap((u) => byId.get(u.compositionId) ?? []), lookMap(looks), fixtures));
  const programmerHasValues = $derived(Object.keys(live.programmer).length > 0);

  const looksById = $derived(lookMap(looks));
  /** looks a scene can use: the shared ones (a composition's own looks stay in their composition) */
  const availableLooks = $derived(looks.filter((l) => l.owner === null && !lookUses.some((u) => u.lookId === l.id)));
  const section = $derived(marker === null ? scene : scene.sections.find((s) => s.marker === marker)
    ?? { compositions: [], looks: [] });

  // the uses of the scene or part; slider moves change them here first and are saved when let go
  $effect(() => {
    uses = section.compositions.map((u) => ({ ...u }));
    lookUses = section.looks.map((u) => ({ ...u }));
  });

  async function save(next: SceneComposition[], nextLooks: SceneLook[] = lookUses) {
    uses = next;
    lookUses = nextLooks;
    try {
      await api.scenes.setSection(projectId, scene.id, { marker, compositions: next, looks: nextLooks });
    } catch (e) {
      showError(e);
    }
  }

  function changeLook(index: number, patch: Partial<SceneLook>) {
    save(uses, lookUses.map((u, i) => (i === index ? { ...u, ...patch } : u)));
  }

  function addLook() {
    const id = Number(addLookId);
    addLookId = '';
    if (id) save(uses, [...lookUses, { lookId: id, level: 1 }]);
  }

  function change(index: number, patch: Partial<SceneComposition>) {
    save(uses.map((u, i) => (i === index ? { ...u, ...patch } : u)));
  }

  function move(index: number, step: number) {
    const next = [...uses];
    const other = index + step;
    if (other < 0 || other >= next.length) return;
    [next[index], next[other]] = [next[other], next[index]];
    save(next);
  }

  function add() {
    const id = Number(addId);
    addId = '';
    if (id) save([...uses, { compositionId: id, level: 1, speedFactor: 1, restart: false }]);
  }

  /** A new composition for this scene: the programmer as its first step, or an effect to generate. */
  async function create(kind: 'programmer' | 'effect') {
    const count = uses.length + 1;
    try {
      let created = await api.compositions.create(projectId, {
        name: `${scene.name}${marker ? ` – ${marker}` : ''} – ${kind === 'effect' ? 'effect' : count}`,
      });
      if (kind === 'programmer') created = await api.compositions.recordStep(projectId, created.id);
      await save([...uses, { compositionId: created.id, level: 1, speedFactor: 1, restart: false }]);
      if (kind === 'effect') editing = { id: created.id, generate: true };
    } catch (e) {
      showError(e);
    }
  }

  function detail(composition: Composition): string {
    const steps = `${composition.steps.length} ${composition.steps.length === 1 ? 'step' : 'steps'}`;
    const others = scenesUsing(composition.id, scenes).filter((s) => s.id !== scene.id).length;
    return [
      composition.generator ? effectLabel(composition.generator.kind) : steps,
      composition.intensityMode === 'relative' ? 'relative' : null,
      others > 0 ? `also in ${others} ${others === 1 ? 'scene' : 'scenes'}` : null,
    ].filter(Boolean).join(' · ');
  }

  function sliderValue(event: Event): number {
    return Number((event.currentTarget as HTMLInputElement).value);
  }
</script>

<section class="compositions" class:part={marker !== null}>
  {#if marker === null}
    <h3>Compositions</h3>
    <p class="hint dim">
      Run side by side from GO. Intensity: the highest wins; colors: the later one. A composition that is in the next
      scene too keeps running.
    </p>
  {:else}
    <h3>Compositions · 💡 {marker}</h3>
    <p class="hint dim">
      {#if info}<strong>{info}</strong>{' – '}{/if}Fades in when the script passes the marker. This part stands on its own:
      choose again what should stay on. What is in both parts does not move.
    </p>
  {/if}

  {#if lookUses.length > 0}
    <ol class="list">
      {#each lookUses as use, i (use.lookId)}
        {@const look = looksById.get(use.lookId)}
        <li>
          <div class="use-head">
            <span class="icon" aria-hidden="true">◆</span>
            <span class="name">{look?.name ?? '(deleted look)'}</span>
            <span class="dim small">look</span>
            <span class="actions">
              <button class="btn small ghost danger" type="button"
                onclick={() => save(uses, lookUses.filter((_, j) => j !== i))} aria-label="Remove look">✕</button>
            </span>
          </div>
          <div class="use">
            <label class="mini">Level <span class="mono">{Math.round(use.level * 100)} %</span>
              <input type="range" min="0" max="1" step="0.01" value={use.level}
                oninput={(e) => (lookUses[i].level = sliderValue(e))}
                onchange={(e) => changeLook(i, { level: sliderValue(e) })} /></label>
          </div>
        </li>
      {/each}
    </ol>
  {/if}

  {#if uses.length > 0}
    <ol class="list">
      {#each uses as use, i (use.compositionId)}
        {@const composition = byId.get(use.compositionId)}
        <li>
          <div class="use-head">
            <span class="icon" aria-hidden="true">{composition ? compositionIcon(composition) : '?'}</span>
            <button type="button" class="link" onclick={() => (editing = { id: use.compositionId, generate: false })}
              title="Edit the composition – it changes in every scene that uses it">
              <span class="name">{composition?.name ?? '(deleted)'}</span>
              {#if composition}<span class="dim small">{detail(composition)}</span>{/if}
            </button>
            {#if composition && isFast(composition, use.speedFactor)}<span class="fast" title="Fast flashing">⚡</span>{/if}
            <span class="actions">
              <button class="btn small ghost" type="button" onclick={() => move(i, -1)} aria-label="Move up">↑</button>
              <button class="btn small ghost" type="button" onclick={() => move(i, 1)} aria-label="Move down">↓</button>
              <button class="btn small ghost danger" type="button" onclick={() => save(uses.filter((_, j) => j !== i))}
                aria-label="Remove from scene">✕</button>
            </span>
          </div>
          <div class="use">
            <label class="mini">Level <span class="mono">{Math.round(use.level * 100)} %</span>
              <input type="range" min="0" max="1" step="0.01" value={use.level}
                oninput={(e) => (uses[i].level = sliderValue(e))} onchange={(e) => change(i, { level: sliderValue(e) })} /></label>
            <label class="mini">Tempo <span class="mono">{use.speedFactor.toFixed(2)}×</span>
              <input type="range" min="0.25" max="4" step="0.05" value={use.speedFactor}
                oninput={(e) => (uses[i].speedFactor = sliderValue(e))}
                onchange={(e) => change(i, { speedFactor: sliderValue(e) })} /></label>
            <label class="check small" title="Start from the beginning at GO, even if it already runs in the scene before">
              <input type="checkbox" checked={use.restart} onchange={(e) => change(i, { restart: e.currentTarget.checked })} />
              Restart at GO
            </label>
          </div>
        </li>
      {/each}
    </ol>
  {:else if lookUses.length === 0}
    <p class="dim small">{marker === null ? 'No compositions – the scene is dark.' : 'Nothing chosen – the stage goes dark at this marker.'}</p>
  {/if}
  {#each warnings as warning, i (i)}<p class="hint warning-text">{warning}</p>{/each}

  <div class="add">
    <label class="field grow">
      <span class="sr-only">Add a composition</span>
      <select bind:value={addId} onchange={add} disabled={available.length === 0}>
        <option value="">{available.length === 0 ? 'No other compositions yet' : '+ Add a composition…'}</option>
        {#each available as composition (composition.id)}
          <option value={String(composition.id)}>{compositionIcon(composition)} {composition.name}</option>
        {/each}
      </select>
    </label>
    <button class="btn small record-outline" type="button" onclick={() => create('programmer')}
      disabled={!programmerHasValues} title="A new composition with the programmer as its step">⏺ From programmer</button>
    <button class="btn small" type="button" onclick={() => create('effect')}
      title="A new composition generated from a chase, rainbow, flicker, …">✦ Effect</button>
  </div>
  <label class="field">
    <span class="sr-only">Add a look</span>
    <select bind:value={addLookId} onchange={addLook} disabled={availableLooks.length === 0}>
      <option value="">{availableLooks.length === 0 ? 'Shared looks are made in the Looks tab' : '+ Add a look…'}</option>
      {#each availableLooks as look (look.id)}<option value={String(look.id)}>◆ {look.name}</option>{/each}
    </select>
  </label>
</section>

<Modal wide bind:open={() => editingComposition !== null, (open) => { if (!open) editing = null; }} title="Composition">
  {#if editingComposition && editing}
    {#key editingComposition.id}
      <CompositionEditor {projectId} composition={editingComposition} {looks} {scenes} {fixtures} {groups} {modes}
        startWithGenerator={editing.generate} />
    {/key}
  {/if}
  {#snippet actions()}
    <button class="btn primary" type="button" onclick={() => (editing = null)}>Done</button>
  {/snippet}
</Modal>

<style>
  .compositions {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  /* a part after a light marker */
  .compositions.part h3 {
    color: var(--warning);
  }

  .list {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .list li {
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 6px 8px;
    border-radius: var(--radius-sm);
    background: var(--panel-raised);
  }

  .use-head,
  .use,
  .add {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .use {
    flex-wrap: wrap;
    padding-left: 28px;
  }

  .mini {
    display: flex;
    flex-direction: column;
    gap: 2px;
    width: 130px;
    font-size: 11px;
    color: var(--text-dim);
  }

  .icon {
    width: 20px;
    text-align: center;
  }

  .link {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    min-width: 0;
    padding: 0;
    border: 0;
    background: none;
    color: var(--text);
    font: inherit;
    text-align: left;
    cursor: pointer;
  }

  .link:hover .name {
    color: var(--accent);
  }

  .name {
    font-weight: 600;
  }

  .actions {
    display: flex;
  }

  .fast {
    color: var(--warning);
  }

  .grow {
    flex: 1;
  }

  .hint {
    margin: 0;
    font-size: 12px;
  }

  .warning-text {
    color: var(--warning);
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
