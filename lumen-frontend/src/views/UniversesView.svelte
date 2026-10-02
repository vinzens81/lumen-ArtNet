<script lang="ts">
  import Modal from '../components/Modal.svelte';
  import { api } from '../lib/api';
  import { live } from '../lib/live.svelte';
  import { navigate } from '../lib/router.svelte';
  import { app } from '../lib/state.svelte';
  import { notify, showError } from '../lib/toasts.svelte';
  import type { ArtNetNode, DeliveryMode, Universe } from '../lib/types';

  let universes = $state<Universe[]>([]);
  let nodes = $state<ArtNetNode[]>([]);
  let editing = $state<Universe | null>(null);
  let formOpen = $state(false);
  let form = $state({ number: 1, name: '', port: 0, mode: 'auto' as DeliveryMode, ips: '' });
  let portTouched = $state(false);
  let showAdvanced = $state(false);

  const project = $derived(app.active);
  // effects depend on the id only: reloading the project list yields new objects for the same project
  const projectId = $derived(app.active?.id ?? null);
  const parts = $derived({ net: (form.port >> 8) & 0x7f, subnet: (form.port >> 4) & 0x0f, universe: form.port & 0x0f });
  const listening = $derived(nodes.filter((n) => n.portAddresses.includes(form.port)));
  const usedBy = $derived(universes.find((u) => u.portAddress === form.port && u.id !== editing?.id));

  function setPart(part: 'net' | 'subnet' | 'universe', value: number) {
    const next = { ...parts, [part]: Math.max(0, Math.min(part === 'net' ? 127 : 15, value || 0)) };
    form.port = (next.net << 8) | (next.subnet << 4) | next.universe;
    portTouched = true;
  }

  // suggest port address = number - 1 for new universes until the user picks one
  $effect(() => {
    if (!editing && !portTouched) form.port = Math.max(0, (form.number || 1) - 1);
  });
  const projectPorts = $derived(new Set(universes.map((u) => u.portAddress)));

  $effect(() => {
    void live.patchRevision;
    if (projectId !== null) load(projectId);
  });

  $effect(() => {
    void live.nodesRevision;
    loadNodes();
  });

  async function load(projectId: number) {
    try {
      universes = await api.universes.list(projectId);
    } catch (e) {
      showError(e);
    }
  }

  async function loadNodes() {
    try {
      nodes = await api.artnet.nodes();
    } catch (e) {
      showError(e);
    }
  }

  function openCreate() {
    const number = Math.max(0, ...universes.map((u) => u.number)) + 1;
    editing = null;
    portTouched = false;
    showAdvanced = false;
    form = { number, name: '', port: number - 1, mode: 'auto', ips: '' };
    formOpen = true;
  }

  function openEdit(universe: Universe) {
    editing = universe;
    portTouched = true;
    showAdvanced = false;
    form = {
      number: universe.number,
      name: universe.name ?? '',
      port: universe.portAddress,
      mode: universe.mode,
      ips: universe.ipAddresses.join(', '),
    };
    formOpen = true;
  }

  async function save() {
    if (!project) return;
    const request = {
      number: form.number,
      name: form.name || null,
      artnet: parts,
      mode: form.mode,
      ipAddresses: form.mode === 'broadcast' ? [] : form.ips.split(/[\s,;]+/).filter(Boolean),
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

  /** Online nodes that receive this universe in auto mode. */
  function receivers(universe: Universe): ArtNetNode[] {
    return universe.mode === 'auto'
      ? nodes.filter((n) => n.online && n.portAddresses.includes(universe.portAddress))
      : [];
  }

  function universeLabel(port: number): string {
    const universe = universes.find((u) => u.portAddress === port);
    return universe ? `Art-Net ${port} → ${universe.name ?? `Universe ${universe.number}`}` : `Art-Net ${port} – not in project`;
  }

  function addIp(ip: string) {
    const ips = form.ips.split(/[\s,;]+/).filter(Boolean);
    if (!ips.includes(ip)) form.ips = [...ips, ip].join(', ');
  }

  function ago(iso: string): string {
    const seconds = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 1000));
    return seconds < 60 ? `${seconds} s ago` : `${Math.round(seconds / 60)} min ago`;
  }
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
        <p class="dim">Each universe carries 512 DMX channels. By default it goes by unicast to every Art-Net node that reports it.</p>
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
          {@const found = receivers(universe)}
          {@const silent = universe.mode === 'auto' && found.length === 0 && universe.ipAddresses.length === 0}
          <article class="card glass" class:silent>
            <div class="number mono">{universe.number}</div>
            <div class="info">
              <h2>{universe.name ?? `Universe ${universe.number}`}</h2>
              <div class="address">
                <span class="label">Art-Net universe</span>
                <span class="port mono">{universe.portAddress}</span>
                <span class="dim mono small" title="Net : Subnet : Universe">{universe.artnet.net}:{universe.artnet.subnet}:{universe.artnet.universe}</span>
              </div>
              <div class="meta wrap">
                {#if universe.mode === 'broadcast'}
                  <span class="badge warning" title="Every device on the network receives this universe">Broadcast</span>
                {:else}
                  {#if universe.mode === 'auto'}<span class="badge accent">Auto</span>{/if}
                  {#each found as node (node.ipAddress + node.bindIndex)}
                    <span class="badge node" title={node.ipAddress}><span class="dot"></span>{node.shortName || node.ipAddress}</span>
                  {/each}
                  {#each universe.ipAddresses as ip (ip)}
                    <span class="badge mono">{ip}</span>
                  {/each}
                {/if}
              </div>
              {#if silent}
                <p class="warn">No node receives this universe yet. Set your node to Art-Net universe {universe.portAddress} or add its IP.</p>
              {/if}
            </div>
            <div class="actions">
              <button class="btn small ghost" type="button" onclick={() => openEdit(universe)}>Edit</button>
              <button class="btn small ghost danger" type="button" onclick={() => remove(universe)}>Delete</button>
            </div>
          </article>
        {/each}
      </div>
    {/if}

    <section class="nodes glass">
      <header>
        <h3>Art-Net nodes on the network</h3>
        <span class="dim small">Found automatically via ArtPoll every few seconds</span>
      </header>
      {#if nodes.length === 0}
        <p class="dim">No nodes found yet. Nodes that do not answer ArtPoll can still be reached by adding their IP to a universe.</p>
      {:else}
        <div class="node-list">
          {#each nodes as node (node.ipAddress + node.bindIndex)}
            <article class="node-card" class:offline={!node.online}>
              <span class="status" title={node.online ? 'online' : `offline, last seen ${ago(node.lastSeen)}`}></span>
              <div class="node-info">
                <strong>{node.shortName || '(no name)'}</strong>
                <span class="dim small">{node.longName}</span>
                <span class="mono small">{node.ipAddress} · {node.macAddress}</span>
                {#if node.nodeReport}<span class="dim small">{node.nodeReport}</span>{/if}
              </div>
              <div class="node-ports">
                {#each node.portAddresses as port (port)}
                  <span class="badge port-badge" class:accent={projectPorts.has(port)} class:warning={!projectPorts.has(port)}
                    title={projectPorts.has(port) ? '' : 'No universe of the active project uses this Art-Net universe'}>
                    {universeLabel(port)}
                  </span>
                {:else}
                  <span class="dim small">no DMX outputs</span>
                {/each}
              </div>
            </article>
          {/each}
        </div>
      {/if}
    </section>
  {/if}
</section>

<Modal bind:open={formOpen} title={editing ? 'Edit universe' : 'New universe'}>
  <form id="universe-form" onsubmit={(e) => { e.preventDefault(); save(); }}>
    <div class="row">
      <label class="field number-field">Number in Lumen<input type="number" min="1" bind:value={form.number} required /></label>
      <label class="field">Name<input bind:value={form.name} placeholder="Stage (optional)" /></label>
    </div>
    <p class="field"><span class="hint">Only used inside Lumen (patch, scenes) – it is not sent to any device.</span></p>

    <div class="artnet-box">
      <label class="field">Art-Net universe (port address)
        <input class="port-input mono" type="number" min="0" max="32767" bind:value={form.port}
          oninput={() => (portTouched = true)} required />
      </label>
      <p class="field"><span class="hint">This number is sent in every packet. Set the same number as “Universe” on your node. Art-Net counts from 0.</span></p>
      {#if listening.length > 0}
        <p class="listen ok">✔ {listening.map((n) => `${n.shortName || n.ipAddress} (${n.ipAddress})`).join(', ')} {listening.length === 1 ? 'listens' : 'listen'} on {form.port}</p>
      {:else if nodes.length > 0}
        <p class="listen">No node found on {form.port} yet – found nodes listen on {[...new Set(nodes.flatMap((n) => n.portAddresses))].join(', ')}.</p>
      {/if}
      {#if usedBy}
        <p class="listen warn-text">Universe {usedBy.number} already sends on {form.port}.</p>
      {/if}
      <button type="button" class="link" onclick={() => (showAdvanced = !showAdvanced)} aria-expanded={showAdvanced}>
        {showAdvanced ? '▾' : '▸'} Net / Subnet / Universe <span class="mono">{parts.net}:{parts.subnet}:{parts.universe}</span>
      </button>
      {#if showAdvanced}
        <div class="row">
          <label class="field">Net<input type="number" min="0" max="127" value={parts.net} oninput={(e) => setPart('net', Number(e.currentTarget.value))} /></label>
          <label class="field">Subnet<input type="number" min="0" max="15" value={parts.subnet} oninput={(e) => setPart('subnet', Number(e.currentTarget.value))} /></label>
          <label class="field">Universe<input type="number" min="0" max="15" value={parts.universe} oninput={(e) => setPart('universe', Number(e.currentTarget.value))} /></label>
        </div>
        <p class="field"><span class="hint">Some consoles and nodes show the address in three parts: port address = Net × 256 + Subnet × 16 + Universe.</span></p>
      {/if}
    </div>

    <fieldset class="modes">
      <legend class="field">Send to</legend>
      <label class:active={form.mode === 'auto'}>
        <input type="radio" bind:group={form.mode} value="auto" />
        <span><strong>Auto</strong> <span class="dim">– nodes found on the network that output this universe (recommended)</span></span>
      </label>
      <label class:active={form.mode === 'unicast'}>
        <input type="radio" bind:group={form.mode} value="unicast" />
        <span><strong>Fixed IPs</strong> <span class="dim">– only the addresses below</span></span>
      </label>
      <label class:active={form.mode === 'broadcast'}>
        <input type="radio" bind:group={form.mode} value="broadcast" />
        <span><strong>Broadcast</strong> <span class="dim">– everybody; unreliable over Wi-Fi</span></span>
      </label>
    </fieldset>

    {#if form.mode !== 'broadcast'}
      <label class="field">{form.mode === 'auto' ? 'Additional IP addresses (optional)' : 'IP addresses'}
        <input bind:value={form.ips} placeholder="192.168.1.50, 192.168.1.51" required={form.mode === 'unicast'} />
      </label>
      {#if nodes.length > 0}
        <div class="suggest">
          {#each nodes as node (node.ipAddress + node.bindIndex)}
            <button type="button" class="btn small" onclick={() => addIp(node.ipAddress)}>+ {node.shortName || node.ipAddress}</button>
          {/each}
        </div>
      {/if}
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

  .modes {
    display: flex;
    flex-direction: column;
    gap: 6px;
    margin: 0;
    padding: 0;
    border: 0;
  }

  .modes legend {
    margin-bottom: 8px;
  }

  .modes label {
    display: flex;
    align-items: flex-start;
    gap: 10px;
    padding: 9px 12px;
    border-radius: var(--radius-sm);
    border: 1px solid var(--border);
    font-size: 13px;
    cursor: pointer;
  }

  .modes label.active {
    border-color: var(--accent);
    background: var(--accent-soft);
  }

  .modes input {
    margin-top: 3px;
  }

  .number-field {
    flex: 0 0 140px;
  }

  .artnet-box {
    display: flex;
    flex-direction: column;
    gap: 10px;
    padding: 14px;
    border-radius: var(--radius-sm);
    border: 1px solid var(--border-strong);
    background: rgba(0, 0, 0, 0.18);
  }

  .artnet-box p {
    margin: 0;
  }

  .port-input {
    font-size: 20px !important;
    font-weight: 700;
    color: var(--accent) !important;
  }

  .listen {
    font-size: 12px;
    color: var(--text-dim);
  }

  .listen.ok {
    color: var(--ok);
  }

  .warn-text {
    color: var(--warning);
  }

  .link {
    align-self: flex-start;
    padding: 0;
    border: 0;
    background: none;
    color: var(--text-dim);
    font: inherit;
    font-size: 12px;
    cursor: pointer;
  }

  .address {
    display: flex;
    align-items: baseline;
    gap: 8px;
  }

  .address .label {
    font-size: 11px;
    text-transform: uppercase;
    letter-spacing: 0.08em;
    color: var(--text-faint);
  }

  .address .port {
    font-size: 18px;
    font-weight: 700;
    color: var(--accent);
  }

  .port-badge {
    text-transform: none;
    letter-spacing: 0;
  }

  .suggest {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: -6px;
  }

  .cards {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
    gap: var(--gap);
  }

  .card {
    display: flex;
    align-items: flex-start;
    gap: 16px;
    padding: 16px;
  }

  .card.silent {
    border-color: rgba(255, 181, 71, 0.45);
  }

  .number {
    display: grid;
    place-items: center;
    flex: none;
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
    min-width: 0;
  }

  .meta {
    display: flex;
    gap: 8px;
    font-size: 12px;
  }

  .meta.wrap {
    flex-wrap: wrap;
  }

  .badge.node {
    color: var(--ok);
    border-color: rgba(77, 255, 166, 0.45);
    text-transform: none;
    letter-spacing: 0;
  }

  .badge .dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--ok);
    box-shadow: 0 0 6px var(--ok);
  }

  .warn {
    margin: 2px 0 0;
    font-size: 12px;
    color: var(--warning);
  }

  .actions {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .nodes {
    display: flex;
    flex-direction: column;
    gap: 12px;
    padding: 16px;
  }

  .nodes header {
    display: flex;
    align-items: baseline;
    gap: 12px;
  }

  .nodes p {
    margin: 0;
  }

  .small {
    font-size: 11px;
  }

  .node-list {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
    gap: 10px;
  }

  .node-card {
    display: flex;
    align-items: flex-start;
    gap: 12px;
    padding: 12px;
    border-radius: var(--radius-sm);
    border: 1px solid var(--border);
    background: var(--panel-raised);
  }

  .node-card.offline {
    opacity: 0.55;
  }

  .status {
    flex: none;
    width: 10px;
    height: 10px;
    margin-top: 5px;
    border-radius: 50%;
    background: var(--ok);
    box-shadow: 0 0 10px var(--ok);
  }

  .offline .status {
    background: var(--text-faint);
    box-shadow: none;
  }

  .node-info {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
  }

  .node-ports {
    display: flex;
    flex-wrap: wrap;
    justify-content: flex-end;
    gap: 4px;
    max-width: 50%;
  }
</style>
