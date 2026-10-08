<script lang="ts">
  import { programmerSelection } from '../lib/selection.svelte';
  import type { AttributeGroup, RecordRequest } from '../lib/types';
  import Modal from './Modal.svelte';

  const ATTRIBUTE_GROUPS: { id: AttributeGroup; label: string }[] = [
    { id: 'intensity', label: 'Intensity' },
    { id: 'color', label: 'Color' },
    { id: 'beam', label: 'Beam' },
    { id: 'other', label: 'Other' },
  ];

  /** Asks how to take the programmer into a look: replace it, or merge only parts of the programmer. */
  let {
    open = $bindable(false),
    subject,
    note,
    onconfirm,
  }: {
    open?: boolean;
    /** e.g. “look ‘Warm’” */
    subject: string;
    /** shown below the options, e.g. where the change shows */
    note?: string;
    onconfirm: (request: RecordRequest) => void;
  } = $props();

  let update = $state({ merge: false, onlySelected: false, groups: [] as AttributeGroup[] });

  $effect(() => {
    if (open) update = { merge: false, onlySelected: false, groups: [] };
  });

  function confirm() {
    onconfirm(update.merge
      ? { merge: true, fixtureIds: update.onlySelected ? programmerSelection.ids : null, groups: update.groups }
      : { merge: false });
  }
</script>

<Modal bind:open title="Update from the programmer">
  <div class="options">
    <p>Take the programmer into {subject}:</p>
    <label class="check">
      <input type="radio" name="update-mode" checked={!update.merge} onchange={() => (update.merge = false)} />
      Replace everything <span class="hint dim">– fixtures without programmer values go dark</span>
    </label>
    <label class="check">
      <input type="radio" name="update-mode" checked={update.merge} onchange={() => (update.merge = true)} />
      Merge <span class="hint dim">– take only what the programmer sets, keep the rest</span>
    </label>
    {#if update.merge}
      <fieldset class="merge-filter">
        <legend>Only these attributes <span class="hint dim">(none = all)</span></legend>
        {#each ATTRIBUTE_GROUPS as group (group.id)}
          <label class="check">
            <input type="checkbox" checked={update.groups.includes(group.id)} onchange={(e) => {
              const on = (e.currentTarget as HTMLInputElement).checked;
              update.groups = on ? [...update.groups, group.id] : update.groups.filter((g) => g !== group.id);
            }} />
            {group.label}
          </label>
        {/each}
      </fieldset>
      <label class="check">
        <input type="checkbox" bind:checked={update.onlySelected} disabled={programmerSelection.ids.length === 0} />
        Only the {programmerSelection.ids.length} fixtures selected in the programmer
      </label>
    {/if}
    <p class="hint dim">The content before stays in the history – “↶ Undo” puts it back.{note ? ` ${note}` : ''}</p>
  </div>
  {#snippet actions()}
    <button class="btn ghost" type="button" onclick={() => (open = false)}>Cancel</button>
    <button class="btn record" type="button" onclick={confirm}>⏺ {update.merge ? 'Merge' : 'Replace'}</button>
  {/snippet}
</Modal>

<style>
  .options {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .options p {
    margin: 0;
  }

  .merge-filter {
    display: flex;
    flex-wrap: wrap;
    gap: 6px 16px;
    margin: 0;
    padding: 8px 12px;
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
  }
</style>
