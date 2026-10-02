<script lang="ts">
  import { access } from '../lib/access.svelte';
  import { api } from '../lib/api';
  import { backupReason, fileSize } from '../lib/format';
  import { notify, showError } from '../lib/toasts.svelte';
  import type { Backups } from '../lib/types';

  let backups = $state<Backups | null>(null);
  let creating = $state(false);

  async function loadBackups() {
    try {
      backups = await api.backups.list();
    } catch (e) {
      showError(e);
    }
  }

  async function backUpNow() {
    creating = true;
    try {
      const backup = await api.backups.create();
      notify(`Backup ${backup.name} created`);
      await loadBackups();
    } catch (e) {
      showError(e);
    } finally {
      creating = false;
    }
  }

  $effect(() => {
    loadBackups();
  });

  let newPin = $state('');
  let confirmPin = $state('');
  const pinValid = $derived(/^\d{4,12}$/.test(newPin) && newPin === confirmPin);

  async function savePin(pin: string | null) {
    try {
      access.status = await api.access.setPin(pin);
      notify(pin === null ? 'PIN removed – setup is open for everyone' : 'PIN saved');
      newPin = '';
      confirmPin = '';
    } catch (e) {
      showError(e);
    }
  }

  async function setNetworkAccess(event: Event) {
    const enabled = (event.currentTarget as HTMLInputElement).checked;
    try {
      access.status = await api.access.setNetworkAccess(enabled);
    } catch (e) {
      showError(e);
      await access.load();
    }
  }

  const restartNeeded = $derived(
    access.status !== null &&
      !access.status.networkAccessForced &&
      access.status.networkAccess !== access.status.networkAccessActive,
  );

  const dateTime = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'medium' });
</script>

<section class="view">
  <header class="head">
    <div>
      <h1>Settings</h1>
      <p class="dim">Settings of this Lumen server, for all projects.</p>
    </div>
  </header>

  <article class="glass panel">
    <header class="panel-head">
      <div>
        <h2>Configuration PIN</h2>
        <p class="dim">
          With a PIN, setup can only be changed after entering it. Starting show mode locks every browser again, so
          nobody leaves the show by accident. GO, master and blackout always work without the PIN.
        </p>
      </div>
      {#if access.status?.pinSet}
        <span class="badge accent">PIN set</span>
      {:else}
        <span class="badge warning">No PIN</span>
      {/if}
    </header>
    {#if access.locked}
      <div class="actions">
        <button class="btn primary" type="button" onclick={() => access.requestUnlock()}>Enter PIN to change it</button>
      </div>
    {:else}
      <form class="pin-form" onsubmit={(e) => { e.preventDefault(); if (pinValid) savePin(newPin); }}>
        <label class="field">{access.status?.pinSet ? 'New PIN' : 'PIN'}
          <input type="password" inputmode="numeric" autocomplete="new-password" data-1p-ignore maxlength="12" bind:value={newPin}
            placeholder="4–12 digits" />
        </label>
        <label class="field">Repeat PIN
          <input type="password" inputmode="numeric" autocomplete="new-password" data-1p-ignore maxlength="12" bind:value={confirmPin} />
        </label>
        <div class="actions">
          <button class="btn primary" type="submit" disabled={!pinValid}>
            {access.status?.pinSet ? 'Change PIN' : 'Set PIN'}
          </button>
          {#if access.status?.pinSet}
            <button class="btn ghost danger" type="button" onclick={() => savePin(null)}>Remove PIN</button>
            <button class="btn ghost" type="button" onclick={() => access.lock()}>Lock now</button>
          {/if}
        </div>
        {#if confirmPin.length > 0 && newPin !== confirmPin}
          <p class="small warning-text">The PINs do not match.</p>
        {/if}
      </form>
    {/if}
  </article>

  <article class="glass panel">
    <header class="panel-head">
      <div>
        <h2>Network access</h2>
        <p class="dim">
          By default only this computer can open Lumen. Allow tablets and phones in the same network to use it as a
          remote control or second screen.
        </p>
      </div>
    </header>
    {#if access.status}
      <label class="toggle">
        <input type="checkbox" checked={access.status.networkAccess || access.status.networkAccessForced}
          disabled={access.status.networkAccessForced || access.locked} onchange={setNetworkAccess} />
        Allow other devices in the network
      </label>
      {#if access.status.networkAccessForced}
        <p class="small dim">Switched on in the server configuration (<span class="mono">lumen.network-access</span>).</p>
      {/if}
      {#if restartNeeded}
        <p class="small warning-text">Restart Lumen for this change to take effect.</p>
      {/if}
      {#if access.status.networkAccessActive}
        {#if access.status.urls.length > 0}
          <p class="small">Reachable at:
            {#each access.status.urls as url, i (url)}{#if i > 0}, {/if}<span class="mono">{url}</span>{/each}
          </p>
        {/if}
        {#if !access.status.pinSet}
          <p class="small warning-text">Without a PIN, everyone in the network can change the setup. Set a PIN above.</p>
        {/if}
      {/if}
    {/if}
  </article>

  <article class="glass panel">
    <header class="panel-head">
      <div>
        <h2>Backups</h2>
        <p class="dim">
          The whole database is copied when the server starts, when show mode starts and when another project is
          activated. To restore a backup, stop Lumen and copy the file over <span class="mono">data/lumen.db</span>.
        </p>
      </div>
      <button class="btn" type="button" onclick={backUpNow} disabled={creating}>
        {creating ? 'Backing up…' : 'Back up now'}
      </button>
    </header>
    {#if backups}
      <p class="small dim">Folder: <span class="mono">{backups.directory}</span></p>
      {#if backups.backups.length === 0}
        <p class="dim">No backups yet.</p>
      {:else}
        <table>
          <thead>
            <tr><th>Created</th><th>Reason</th><th>File</th><th class="num">Size</th></tr>
          </thead>
          <tbody>
            {#each backups.backups as backup (backup.name)}
              <tr>
                <td>{dateTime.format(new Date(backup.createdAt))}</td>
                <td>{backupReason(backup.name)}</td>
                <td class="mono small">{backup.name}</td>
                <td class="num mono">{fileSize(backup.sizeBytes)}</td>
              </tr>
            {/each}
          </tbody>
        </table>
      {/if}
    {/if}
  </article>
</section>

<style>
  .view {
    display: flex;
    flex-direction: column;
    gap: 20px;
    padding: 12px 8px;
    max-width: 980px;
  }

  .head p {
    margin: 6px 0 0;
  }

  .panel {
    display: flex;
    flex-direction: column;
    gap: 14px;
    padding: 20px;
  }

  .panel-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
  }

  .panel-head p {
    margin: 6px 0 0;
    max-width: 640px;
  }

  .small {
    font-size: 12px;
  }

  p.small {
    margin: 0;
  }

  .warning-text {
    color: var(--warning);
  }

  .pin-form {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 220px));
    gap: 12px;
    align-items: end;
  }

  .pin-form .actions,
  .pin-form p {
    grid-column: 1 / -1;
  }

  .actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .toggle {
    display: flex;
    align-items: center;
    gap: 10px;
    font-weight: 500;
  }

  .toggle input {
    width: 18px;
    height: 18px;
    accent-color: var(--accent);
  }

  table {
    width: 100%;
    border-collapse: collapse;
  }

  th {
    text-align: left;
    font-size: 11px;
    font-weight: 600;
    letter-spacing: 0.06em;
    text-transform: uppercase;
    color: var(--text-dim);
  }

  th,
  td {
    padding: 8px 10px;
    border-bottom: 1px solid var(--border);
  }

  .num {
    text-align: right;
  }

  @media (max-width: 700px) {
    .panel-head {
      flex-direction: column;
    }

    th:nth-child(3),
    td:nth-child(3) {
      display: none;
    }
  }
</style>
