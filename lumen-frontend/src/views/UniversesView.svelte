<script lang="ts">
  import Modal from '../components/Modal.svelte';
  import { api } from '../lib/api';
  import { navigate } from '../lib/router.svelte';
  import { app } from '../lib/state.svelte';
  import { notify, showError } from '../lib/toasts.svelte';
  import type { Universe } from '../lib/types';

  let universes = $state<Universe[]>([]);
  let editing = $state<Universe | null>(null);
  let formOpen = $state(false);
  let form = $state({ number: 1, name: '', net: 0, subnet: 0, universe: 0, broadcast: true, ip: '' });

  const project = $derived(app.active);

  $effect(() => {
    if (project) load(project.id);
  });

  async function load(projectId: number) {
    try {
      universes = await api.universes.list(projectId);
    } catch (e) {
      showError(e);
    }
  }

  function openCreate() {
    const number = Math.max(0, ...universes.map((u) => u.number)) + 1;
    const port = number - 1;
    editing = null;
    form = { number, name: '', net: port >> 8, subnet: (port >> 4) & 15, universe: port & 15, broadcast: true, ip: '' };
    formOpen = true;
  }

  function openEdit(universe: Universe) {
    editing = universe;
    form = {
      number: universe.number,
      name: universe.name ?? '',
      ...universe.artnet,
      broadcast: universe.target === 'broadcast',
      ip: universe.target === 'broadcast' ? '' : universe.target,
    };
    formOpen = true;
  }

  async function save() {
    if (!project) return;
    const request = {
      number: form.number,
      name: form.name || null,
      artnet: { net: form.net, subnet: form.subnet, universe: form.universe },
      target: form.broadcast ? 'broadcast' : form.ip,
    };
    try {
      if (editing) {
        await api.universes.update(project.id, editing.id, request);
      } else {
        await api.universes.create(project.id, request);
      }
      notify(`Saved universe ${request.number}`);
      formOpen = false;
      await load(project.id);
    } catch (e) {
      showError(e);
    }
  }

  async function remove(universe: Universe) {
    if (!project) return;
    try {
      await api.universes.remove(project.id, universe.id);
      notify(`Deleted ${universe.displayName}`);
      await load(project.id);
    } catch (e) {
      showError(e);
    }
  }

  const portAddress = $derived((form.net << 8) | (form.subnet << 4) | form.universe);
</script>

<section class="view">
  {#if !project}
    <div class="glass empty">
      <h2>No active project</h2>
      <button class="btn primary" type="button" onclick={() => navigate('/setup/projects')}>Go to projects</button>
    </div>
  {:else}
    <header class="head">
      <div>
        <h1>Universes</h1>
        <p class="dim">Each universe carries 512 DMX channels and is sent via Art-Net to a node or as broadcast.</p>
      </div>
      <button class="btn primary" type="button" onclick={openCreate}>+ New universe</button>
    </header>

    {#if universes.length === 0}
      <div class="glass empty">
        <h2>No universes yet</h2>
        <p>Add a universe before patching fixtures.</p>
        <button class="btn primary" type="button" onclick={openCreate}>+ New universe</button>
      </div>
    {:else}
      <div class="cards">
        {#each universes as universe (universe.id)}
          <article class="card glass">
            <div class="number mono">{universe.number}</div>
            <div class="info">
              <h2>{universe.name ?? `Universe ${universe.number}`}</h2>
              <div class="meta">
                <span>Art-Net <span class="mono">{universe.artnet.net}:{universe.artnet.subnet}:{universe.artnet.universe}</span></span>
                <span class="dim mono">port {universe.portAddress}</span>
              </div>
              <div class="meta">
                {#if universe.target === 'broadcast'}
                  <span class="badge">Broadcast</span>
                {:else}
                  <span class="badge accent mono">{universe.target}</span>
                {/if}
              </div>
            </div>
            <div class="actions">
              <button class="btn small ghost" type="button" onclick={() => openEdit(universe)}>Edit</button>
              <button class="btn small ghost danger" type="button" onclick={() => remove(universe)}>Delete</button>
            </div>
          </article>
        {/each}
      </div>
    {/if}
  {/if}
</section>

<Modal bind:open={formOpen} title={editing ? 'Edit universe' : 'New universe'}>
  <form id="universe-form" onsubmit={(e) => { e.preventDefault(); save(); }}>
    <div class="row">
      <label class="field">Number<input type="number" min="1" bind:value={form.number} required /></label>
      <label class="field">Name<input bind:value={form.name} placeholder="Stage (optional)" /></label>
    </div>
    <div class="row">
      <label class="field">Art-Net net<input type="number" min="0" max="127" bind:value={form.net} /></label>
      <label class="field">Subnet<input type="number" min="0" max="15" bind:value={form.subnet} /></label>
      <label class="field">Universe<input type="number" min="0" max="15" bind:value={form.universe} /></label>
    </div>
    <p class="field"><span class="hint">Port address <span class="mono">{portAddress}</span> – must match the setting of your Art-Net node.</span></p>
    <fieldset class="target">
      <legend class="field">Send to</legend>
      <label><input type="radio" bind:group={form.broadcast} value={true} /> Broadcast</label>
      <label><input type="radio" bind:group={form.broadcast} value={false} /> Node IP address</label>
    </fieldset>
    {#if !form.broadcast}
      <label class="field">IP address<input bind:value={form.ip} placeholder="192.168.1.50" required /></label>
    {/if}
  </form>
  {#snippet actions()}
    <button class="btn ghost" type="button" onclick={() => (formOpen = false)}>Cancel</button>
    <button class="btn primary" type="submit" form="universe-form">Save</button>
  {/snippet}
</Modal>

<style>
  .view {
    display: flex;
    flex-direction: column;
    gap: 20px;
    padding: 12px 8px;
  }

  .head {
    display: flex;
    align-items: flex-end;
    justify-content: space-between;
    gap: 16px;
  }

  .head p {
    margin: 6px 0 0;
  }

  form {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .target {
    display: flex;
    gap: 20px;
    margin: 0;
    padding: 0;
    border: 0;
  }

  .target legend {
    margin-bottom: 8px;
  }

  .target label {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .cards {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
    gap: var(--gap);
  }

  .card {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 16px;
  }

  .number {
    display: grid;
    place-items: center;
    width: 52px;
    height: 52px;
    border-radius: 12px;
    background: var(--accent-soft);
    color: var(--accent);
    font-size: 22px;
    font-weight: 700;
    box-shadow: 0 0 20px var(--accent-soft);
  }

  .info {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .meta {
    display: flex;
    gap: 12px;
    font-size: 12px;
  }

  .actions {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }
</style>
