<script lang="ts">
  import { api } from '../lib/api';
  import { compositionIcon } from '../lib/compositions';
  import { showError } from '../lib/toasts.svelte';
  import type { Composition, KeyMode, Move, Scene, SceneKey } from '../lib/types';

  const SLOTS = [1, 2, 3, 4, 5, 6, 7, 8];

  /** The extra keys 1–8 of a scene: each switches a composition or a move on and off over the scene in show mode. */
  let { projectId, scene, compositions, moves = [] }: {
    projectId: number;
    scene: Scene;
    compositions: Composition[];
    moves?: Move[];
  } = $props();

  const bySlot = $derived(new Map(scene.keys.map((k) => [k.slot, k])));

  async function save(keys: SceneKey[]) {
    try {
      await api.scenes.setKeys(projectId, scene.id, keys);
    } catch (e) {
      showError(e);
    }
  }

  function setKey(slot: number, patch: Partial<SceneKey> | null) {
    const others = scene.keys.filter((k) => k.slot !== slot);
    if (patch === null) {
      save(others);
      return;
    }
    const current: SceneKey = bySlot.get(slot) ?? { slot, compositionId: null, moveId: null, mode: 'toggle' as KeyMode,
      fadeMs: 0 };
    save([...others, { ...current, ...patch }].sort((a, b) => a.slot - b.slot));
  }

  /** `c:<id>` plays a composition, `m:<id>` a move. */
  function choose(slot: number, value: string) {
    if (value === '') {
      setKey(slot, null);
    } else {
      const id = Number(value.slice(2));
      setKey(slot, value.startsWith('m:') ? { compositionId: null, moveId: id } : { compositionId: id, moveId: null });
    }
  }

  function keyValue(key: SceneKey | undefined): string {
    if (!key) return '';
    return key.moveId !== null ? `m:${key.moveId}` : `c:${key.compositionId}`;
  }
</script>

<section class="keys">
  <h3>Keys</h3>
  <p class="hint dim">
    Switch a composition or a move on over this scene in show mode (keys 1–8). A key that is on stays on at GO if the
    next scene has the same one on a key; a new scene takes the keys of the scene before it.
  </p>
  <ul>
    {#each SLOTS as slot (slot)}
      {@const key = bySlot.get(slot)}
      <li>
        <span class="slot mono">{slot}</span>
        <label class="grow">
          <span class="sr-only">Composition or move on key {slot}</span>
          <select value={keyValue(key)} onchange={(e) => choose(slot, e.currentTarget.value)}>
            <option value="">–</option>
            {#each compositions as composition (composition.id)}
              <option value={`c:${composition.id}`}>{compositionIcon(composition)} {composition.name}</option>
            {/each}
            {#if moves.length > 0}
              <optgroup label="Moves">
                {#each moves as move (move.id)}<option value={`m:${move.id}`}>↻ {move.name}</option>{/each}
              </optgroup>
            {/if}
          </select>
        </label>
        {#if key}
          <label>
            <span class="sr-only">Mode of key {slot}</span>
            <select value={key.mode} onchange={(e) => setKey(slot, { mode: e.currentTarget.value as KeyMode })}>
              <option value="toggle">Toggle</option>
              <option value="flash">Flash (while held)</option>
            </select>
          </label>
          <label class="fade" title="Fade in and out">
            <input type="number" min="0" max="600" step="0.1" value={key.fadeMs / 1000}
              onchange={(e) => setKey(slot, { fadeMs: Math.max(0, Math.round(Number(e.currentTarget.value) * 1000)) })} /> s
          </label>
        {/if}
      </li>
    {/each}
  </ul>
</section>

<style>
  .keys {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  ul {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
    gap: 6px 16px;
  }

  li {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .slot {
    width: 18px;
    color: var(--text-dim);
    text-align: right;
  }

  .grow {
    flex: 1;
    min-width: 0;
  }

  .grow select {
    width: 100%;
  }

  .fade {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    color: var(--text-dim);
  }

  .fade input {
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
