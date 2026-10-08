<script lang="ts">
  import { api } from '../lib/api';
  import { live } from '../lib/live.svelte';
  import { attributeGroup, PRESET_GROUPS, presetColor, presetsFor, presetUse } from '../lib/presets';
  import { notify, showError } from '../lib/toasts.svelte';
  import type { Mode, Preset, PresetGroup } from '../lib/types';
  import Modal from './Modal.svelte';

  let {
    projectId,
    selection,
    attributes,
    modes,
  }: {
    projectId: number;
    /** selected fixture ids */
    selection: number[];
    /** attributes the selected fixtures have */
    attributes: Set<string>;
    /** library mode per fixture id, for the color swatches */
    modes: Map<number, Mode>;
  } = $props();

  let presets = $state<Preset[]>([]);
  let editMode = $state(false);
  let createOpen = $state(false);
  let createGroup = $state<PresetGroup>('color');
  let createName = $state('');
  let editOpen = $state(false);
  let editing = $state<Preset | null>(null);
  let editName = $state('');

  const groups = $derived(PRESET_GROUPS.filter((g) => [...attributes].some((a) => attributeGroup(a) === g.id)));

  $effect(() => {
    void live.presetsRevision;
    load(projectId);
  });

  async function load(id: number) {
    try {
      presets = await api.presets.list(id);
    } catch (e) {
      showError(e);
    }
  }

  async function apply(preset: Preset) {
    if (editMode) {
      editing = preset;
      editName = preset.name;
      editOpen = true;
      return;
    }
    try {
      await api.presets.apply(projectId, preset.id, selection);
    } catch (e) {
      showError(e);
    }
  }

  function openCreate(group: PresetGroup) {
    createGroup = group;
    createName = '';
    createOpen = true;
  }

  async function create() {
    try {
      const preset = await api.presets.create(projectId, createName, createGroup, selection);
      notify(`Preset “${preset.name}” recorded`);
      createOpen = false;
      await load(projectId);
    } catch (e) {
      showError(e);
    }
  }

  async function rename() {
    if (!editing) return;
    try {
      await api.presets.rename(projectId, editing.id, editName);
      editOpen = false;
      await load(projectId);
    } catch (e) {
      showError(e);
    }
  }

  async function update() {
    if (!editing) return;
    try {
      const preset = await api.presets.record(projectId, editing.id, selection);
      notify(`Preset “${preset.name}” updated – scenes using it changed with it`);
      editOpen = false;
      await load(projectId);
    } catch (e) {
      showError(e);
    }
  }

  async function remove() {
    if (!editing) return;
    try {
      await api.presets.remove(projectId, editing.id);
      notify(`Preset “${editing.name}” deleted – scenes keep their values`);
      editOpen = false;
      await load(projectId);
    } catch (e) {
      showError(e);
    }
  }
</script>

{#if groups.length > 0}
  <section class="presets">
    <div class="head">
      <h3>Presets</h3>
      <button type="button" class="btn ghost small" class:active={editMode} aria-pressed={editMode}
        onclick={() => (editMode = !editMode)} title="Rename, update or delete presets">{editMode ? 'Done' : '✎ Edit'}</button>
    </div>
    {#each groups as group (group.id)}
      {@const available = presetsFor(presets, group.id, selection)}
      <div class="row">
        <span class="label dim">{group.label}</span>
        <div class="chips">
          {#each available as preset (preset.id)}
            {@const use = presetUse(preset, selection, live.programmerPresets)}
            {@const color = presetColor(preset, modes)}
            <button type="button" class="chip" class:all={use === 'all'} class:some={use === 'some'} class:editing={editMode}
              onclick={() => apply(preset)}
              title={editMode ? `Edit “${preset.name}”` : use === 'all' ? `The selection uses “${preset.name}”` : `Apply “${preset.name}”`}>
              {#if color}<span class="swatch" style="--color: {color}"></span>{/if}
              {preset.name}
            </button>
          {/each}
          <button type="button" class="chip add" onclick={() => openCreate(group.id)}
            title="Record the {group.label.toLowerCase()} values of the selected fixtures as a preset">+</button>
        </div>
      </div>
    {/each}
    <p class="hint dim">
      Scenes remember the preset – update it and every scene using it changes. Setting a value by hand detaches the
      fixture from its preset.
    </p>
  </section>
{/if}

<Modal bind:open={createOpen} title="New {createGroup} preset">
  <form id="preset-form" onsubmit={(e) => { e.preventDefault(); create(); }}>
    <p class="dim">Records the {createGroup} values of the {selection.length} selected fixtures from the programmer.</p>
    <label class="field">Name<input bind:value={createName} placeholder={createGroup === 'beam' ? 'Narrow' : 'Warm'} required /></label>
  </form>
  {#snippet actions()}
    <button class="btn ghost" type="button" onclick={() => (createOpen = false)}>Cancel</button>
    <button class="btn record" type="submit" form="preset-form">⏺ Record</button>
  {/snippet}
</Modal>

<Modal bind:open={editOpen} title="Preset “{editing?.name ?? ''}”">
  <form id="preset-edit-form" onsubmit={(e) => { e.preventDefault(); rename(); }}>
    <label class="field">Name<input bind:value={editName} required /></label>
    <p class="dim">
      “Update” takes the programmer values of the {selection.length} selected fixtures into the preset; its other
      fixtures keep theirs. All scenes using the preset change with it.
    </p>
  </form>
  {#snippet actions()}
    <button class="btn danger" type="button" onclick={remove}>Delete</button>
    <button class="btn ghost" type="button" onclick={rename}>Rename</button>
    <button class="btn record" type="button" onclick={update} disabled={selection.length === 0}>⏺ Update</button>
  {/snippet}
</Modal>

<style>
  .presets {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .head {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .head h3 {
    margin: 0;
  }

  .head .active {
    color: var(--accent);
  }

  .row {
    display: flex;
    align-items: flex-start;
    gap: 8px;
  }

  .label {
    flex: 0 0 62px;
    padding-top: 5px;
    font-size: 0.8rem;
  }

  .chips {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .chip {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 4px 10px;
    border: 1px solid var(--border-strong);
    border-radius: 999px;
    background: transparent;
    color: var(--text);
    font: inherit;
    font-size: 0.85rem;
    cursor: pointer;
  }

  .chip:hover {
    border-color: var(--accent);
  }

  .chip.all {
    border-color: var(--accent);
    background: color-mix(in srgb, var(--accent) 18%, transparent);
  }

  .chip.some {
    border-style: dashed;
    border-color: var(--accent);
  }

  .chip.editing {
    border-style: dotted;
  }

  .chip.add {
    padding: 4px 9px;
    color: var(--text-dim);
  }

  .swatch {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: var(--color);
    box-shadow: 0 0 6px var(--color);
  }

  .hint {
    margin: 0;
    font-size: 0.75rem;
  }

  form {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  form p {
    margin: 0;
  }
</style>
