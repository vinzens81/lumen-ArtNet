<script lang="ts">
  import { api } from '../lib/api';
  import { kindIcon } from '../lib/compositions';
  import { live } from '../lib/live.svelte';
  import { showError } from '../lib/toasts.svelte';
  import type { Composition, ExtraKey, Fixture, Group, KeyMode, Mode, Scene } from '../lib/types';
  import CompositionEditor from './CompositionEditor.svelte';
  import Modal from './Modal.svelte';

  let {
    projectId,
    library,
    fixtures,
    groups,
    modes,
  }: { projectId: number; library: Composition[]; fixtures: Fixture[]; groups: Group[]; modes: Map<number, Mode> } =
    $props();

  const SLOTS = [1, 2, 3, 4, 5, 6, 7, 8];

  let keys = $state<ExtraKey[]>([]);
  let scenes = $state<Scene[]>([]);
  let editingSlot = $state<number | null>(null);

  const bySlot = $derived(new Map(keys.map((k) => [k.slot, k])));
  const editing = $derived(editingSlot !== null ? (bySlot.get(editingSlot)?.composition ?? null) : null);
  const sceneById = $derived(new Map(scenes.map((scene) => [scene.id, scene])));
  /** scenes for extra keys first, then the cue list */
  const sceneChoices = $derived([...scenes].sort((a, b) => Number(b.keyOnly) - Number(a.keyOnly)));

  $effect(() => {
    void live.compositionsRevision;
    void live.scenesRevision;
    Promise.all([api.extraKeys.list(projectId), api.scenes.list(projectId)])
      .then(([keyList, sceneList]) => {
        keys = keyList;
        scenes = sceneList;
      })
      .catch(showError);
  });

  function label(key: ExtraKey): string {
    if (key.composition) return `${kindIcon(key.composition.kind)} ${key.composition.name}`;
    const scene = key.sceneId !== null ? sceneById.get(key.sceneId) : undefined;
    return `${kindIcon('scene')} ${scene ? `${scene.number} ${scene.name}` : 'Scene'}`;
  }

  /** The choice is "c:<composition id>" or "s:<scene id>". */
  async function assign(slot: number, event: Event) {
    const select = event.currentTarget as HTMLSelectElement;
    const [type, id] = select.value.split(':');
    select.value = '';
    if (!id) return;
    const current = bySlot.get(slot);
    const mode = current?.mode ?? 'toggle';
    const fade = current?.fadeMs ?? 0;
    try {
      const key = type === 's'
        ? await api.extraKeys.assignScene(projectId, slot, Number(id), mode, fade)
        : await api.extraKeys.assign(projectId, slot, Number(id), mode, fade);
      keys = [...keys.filter((k) => k.slot !== slot), key].sort((a, b) => a.slot - b.slot);
    } catch (e) {
      showError(e);
    }
  }

  async function update(key: ExtraKey, mode: KeyMode, fadeMs: number) {
    try {
      const updated = await api.extraKeys.update(projectId, key.slot, mode, Math.max(0, Math.round(fadeMs)));
      keys = keys.map((k) => (k.slot === updated.slot ? updated : k));
    } catch (e) {
      showError(e);
    }
  }

  async function clear(key: ExtraKey) {
    try {
      await api.extraKeys.clear(projectId, key.slot);
      keys = keys.filter((k) => k.slot !== key.slot);
    } catch (e) {
      showError(e);
    }
  }

  function saved(composition: Composition) {
    keys = keys.map((k) => (k.composition?.id === composition.id ? { ...k, composition } : k));
  }
</script>

<section class="glass panel">
  <header>
    <h2>Extra keys</h2>
    <p class="dim">
      In show mode, keys 1–8 switch a composition or a whole scene on over the scene on stage – a thunderstorm, a
      police light. A composition on a key is its own copy; a scene stays linked (mark it “only for extra keys” in
      the Scenes tab so GO skips it). Choose in the scene editor in which scenes a key can be used.
    </p>
  </header>
  <div class="slots">
    {#each SLOTS as slot (slot)}
      {@const key = bySlot.get(slot)}
      <div class="slot" class:assigned={key}>
        <span class="number mono">{slot}</span>
        {#if key}
          {#if key.composition}
            <button type="button" class="name" onclick={() => (editingSlot = slot)} title="Edit the composition of this key">
              {label(key)}
            </button>
          {:else}
            <span class="name" title="Plays this scene – edit it in the Scenes tab">{label(key)}</span>
          {/if}
          <div class="settings">
            <label class="mini">
              <span class="sr-only">Mode</span>
              <select value={key.mode} onchange={(e) => update(key, e.currentTarget.value as KeyMode, key.fadeMs)}>
                <option value="toggle">Toggle</option>
                <option value="flash">Flash (hold)</option>
              </select>
            </label>
            <label class="mini">Fade
              <input type="number" min="0" max="600" step="0.1" value={key.fadeMs / 1000}
                onchange={(e) => update(key, key.mode, Number(e.currentTarget.value) * 1000)} /> s
            </label>
          </div>
          <div class="actions">
            <select aria-label="Put something else on key {slot}" onchange={(e) => assign(slot, e)}>
              <option value="">Replace…</option>
              {#if library.length > 0}
                <optgroup label="Compositions">
                  {#each library as composition (composition.id)}
                    <option value={`c:${composition.id}`}>{kindIcon(composition.kind)} {composition.name}</option>
                  {/each}
                </optgroup>
              {/if}
              {#if sceneChoices.length > 0}
                <optgroup label="Scenes">
                  {#each sceneChoices as scene (scene.id)}
                    <option value={`s:${scene.id}`}>▣ {scene.number} {scene.name}{scene.keyOnly ? ' (key only)' : ''}</option>
                  {/each}
                </optgroup>
              {/if}
            </select>
            <button class="btn small ghost danger" type="button" onclick={() => clear(key)} aria-label="Clear key {slot}">✕</button>
          </div>
        {:else}
          <select class="assign" aria-label="Put a composition or scene on key {slot}" onchange={(e) => assign(slot, e)}
            disabled={library.length === 0 && scenes.length === 0}>
            <option value="">Empty – choose…</option>
              {#if library.length > 0}
                <optgroup label="Compositions">
                  {#each library as composition (composition.id)}
                    <option value={`c:${composition.id}`}>{kindIcon(composition.kind)} {composition.name}</option>
                  {/each}
                </optgroup>
              {/if}
              {#if sceneChoices.length > 0}
                <optgroup label="Scenes">
                  {#each sceneChoices as scene (scene.id)}
                    <option value={`s:${scene.id}`}>▣ {scene.number} {scene.name}{scene.keyOnly ? ' (key only)' : ''}</option>
                  {/each}
                </optgroup>
              {/if}
          </select>
        {/if}
      </div>
    {/each}
  </div>
</section>

<Modal wide bind:open={() => editing !== null, (open) => { if (!open) editingSlot = null; }} title="Composition on key {editingSlot}">
  {#if editing}
    <CompositionEditor {projectId} composition={editing} {fixtures} {groups} {modes} onSaved={saved} />
    <p class="hint dim">Changes apply to this key only, not to the library.</p>
  {/if}
  {#snippet actions()}
    <button class="btn primary" type="button" onclick={() => (editingSlot = null)}>Done</button>
  {/snippet}
</Modal>

<style>
  .panel {
    display: flex;
    flex-direction: column;
    gap: 14px;
    padding: 18px;
  }

  header p {
    margin: 6px 0 0;
    max-width: 720px;
  }

  .slots {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
    gap: 10px;
  }

  .slot {
    position: relative;
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 12px;
    min-height: 104px;
    border-radius: var(--radius-sm);
    border: 1px dashed var(--border-strong);
  }

  .slot.assigned {
    border-style: solid;
    background: var(--panel-raised);
  }

  .number {
    position: absolute;
    top: 8px;
    right: 10px;
    font-size: 18px;
    font-weight: 700;
    color: var(--accent);
  }

  .name {
    padding: 0 28px 0 0;
    border: 0;
    background: none;
    color: var(--text);
    font: inherit;
    font-weight: 600;
    text-align: left;
    cursor: pointer;
  }

  .name:hover {
    color: var(--accent);
  }

  .settings,
  .actions {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .slot {
    min-width: 0;
  }

  /* long composition and scene names must not push the buttons out of the tile */
  .actions select,
  .assign {
    flex: 1;
    min-width: 0;
    width: 100%;
  }

  .name {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  select,
  input {
    min-height: 30px;
    padding: 0 8px;
    border-radius: 6px;
    border: 1px solid var(--border-strong);
    background: rgba(0, 0, 0, 0.25);
    color: var(--text);
    font: inherit;
    font-size: 12px;
  }

  .assign {
    margin-top: auto;
  }

  .mini {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 11px;
    color: var(--text-dim);
  }

  .mini input {
    width: 56px;
  }

  .hint {
    margin: 0;
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
