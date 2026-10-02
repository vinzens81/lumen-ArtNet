<script lang="ts">
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
