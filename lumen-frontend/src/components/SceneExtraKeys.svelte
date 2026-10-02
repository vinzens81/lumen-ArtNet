<script lang="ts">
  import { api } from '../lib/api';
  import { kindIcon } from '../lib/compositions';
  import { live } from '../lib/live.svelte';
  import { showError } from '../lib/toasts.svelte';
  import type { ExtraKey } from '../lib/types';

  let { projectId, sceneId }: { projectId: number; sceneId: number } = $props();

  let keys = $state<ExtraKey[]>([]);
  let enabled = $state<number[]>([]);

  $effect(() => {
    void live.compositionsRevision;
    void live.scenesRevision;
    load(projectId, sceneId);
  });

  async function load(project: number, scene: number) {
    try {
      const [list, slots] = await Promise.all([api.extraKeys.list(project), api.extraKeys.ofScene(project, scene)]);
      keys = list;
      enabled = slots.slots;
    } catch (e) {
      showError(e);
    }
  }

  async function toggle(slot: number, on: boolean) {
    const slots = on ? [...enabled, slot] : enabled.filter((s) => s !== slot);
    try {
      enabled = (await api.extraKeys.setForScene(projectId, sceneId, slots)).slots;
    } catch (e) {
      showError(e);
    }
  }
</script>

{#if keys.length > 0}
  <section class="scene-keys">
    <h3>Extra keys in this scene</h3>
    <p class="hint dim">Only these keys can be switched on while the scene is on stage; others fade out at GO.</p>
    <div class="list">
      {#each keys as key (key.slot)}
        <label class="key">
          <input type="checkbox" checked={enabled.includes(key.slot)}
            onchange={(e) => toggle(key.slot, e.currentTarget.checked)} />
          <span class="mono slot">{key.slot}</span>
          {kindIcon(key.composition.kind)} {key.composition.name}
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
    color: var(--accent);
    font-weight: 700;
  }
</style>
