<script lang="ts">
  import { api } from '../lib/api';
  import { kindIcon } from '../lib/compositions';
  import { live } from '../lib/live.svelte';
  import { showError } from '../lib/toasts.svelte';
  import type { ExtraKey, Scene } from '../lib/types';

  let { projectId, sceneId }: { projectId: number; sceneId: number } = $props();

  let keys = $state<ExtraKey[]>([]);
  let scenes = $state<Scene[]>([]);
  let enabled = $state<number[]>([]);

  /** key that plays a scene, by scene id */
  const keyOfScene = $derived(new Map(keys.filter((k) => k.sceneId !== null).map((k) => [k.sceneId!, k])));
  /** scenes only for extra keys: picked here directly, they get a key automatically */
  const keyScenes = $derived(scenes.filter((s) => s.keyOnly && s.id !== sceneId));
  /** all other keys: compositions, and cue-list scenes put on a key in the Compositions tab */
  const otherKeys = $derived(keys.filter((k) => !keyScenes.some((s) => s.id === k.sceneId)));
  const sceneNames = $derived(new Map(scenes.map((s) => [s.id, `${s.number} ${s.name}`])));

  $effect(() => {
    void live.compositionsRevision;
    void live.scenesRevision;
    load(projectId, sceneId);
  });

  async function load(project: number, scene: number) {
    try {
      const [keyList, slots, sceneList] = await Promise.all([
        api.extraKeys.list(project),
        api.extraKeys.ofScene(project, scene),
        api.scenes.list(project),
      ]);
      keys = keyList;
      enabled = slots.slots;
      scenes = sceneList;
    } catch (e) {
      showError(e);
    }
  }

  async function toggleKey(slot: number, on: boolean) {
    const slots = on ? [...enabled, slot] : enabled.filter((s) => s !== slot);
    try {
      enabled = (await api.extraKeys.setForScene(projectId, sceneId, slots)).slots;
    } catch (e) {
      showError(e);
    }
  }

  async function toggleScene(scene: Scene, on: boolean) {
    const key = keyOfScene.get(scene.id);
    if (!on) {
      if (key) await toggleKey(key.slot, false);
      return;
    }
    try {
      // puts the scene on a free key if no key plays it yet
      enabled = (await api.extraKeys.enableKeyScene(projectId, sceneId, scene.id)).slots;
      keys = await api.extraKeys.list(projectId);
    } catch (e) {
      showError(e);
    }
  }

  function sceneEnabled(scene: Scene): boolean {
    const key = keyOfScene.get(scene.id);
    return key !== undefined && enabled.includes(key.slot);
  }
</script>

{#if keyScenes.length > 0 || otherKeys.length > 0}
  <section class="scene-keys">
    <h3>Extra keys in this scene</h3>
    <p class="hint dim">What can be switched on over this scene in show mode; everything else fades out at GO.</p>
    <div class="list">
      {#each keyScenes as scene (scene.id)}
        {@const key = keyOfScene.get(scene.id)}
        <label class="key">
          <input type="checkbox" checked={sceneEnabled(scene)} onchange={(e) => toggleScene(scene, e.currentTarget.checked)} />
          <span class="mono slot" title={key ? `Key ${key.slot}` : 'Gets a free key when ticked'}>{key ? key.slot : '–'}</span>
          {kindIcon('scene')} {scene.number} {scene.name}
        </label>
      {/each}
      {#each otherKeys as key (key.slot)}
        <label class="key">
          <input type="checkbox" checked={enabled.includes(key.slot)}
            onchange={(e) => toggleKey(key.slot, e.currentTarget.checked)} />
          <span class="mono slot">{key.slot}</span>
          {#if key.composition}{kindIcon(key.composition.kind)} {key.composition.name}{:else}{kindIcon('scene')} {sceneNames.get(key.sceneId ?? 0) ?? 'Scene'}{/if}
        </label>
      {/each}
    </div>
  </section>
{/if}

<style>
  .scene-keys {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .hint {
    margin: 0;
    font-size: 12px;
  }

  .list {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .key {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
  }

  .key input {
    accent-color: var(--accent);
  }

  .slot {
    min-width: 12px;
    color: var(--accent);
    font-weight: 700;
  }
</style>
