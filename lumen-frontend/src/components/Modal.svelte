<script lang="ts">
  import type { Snippet } from 'svelte';

  let {
    open = $bindable(false),
    title,
    children,
    actions,
  }: { open?: boolean; title: string; children: Snippet; actions?: Snippet } = $props();

  let dialog: HTMLDialogElement | undefined = $state();

  $effect(() => {
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  });
</script>

<dialog bind:this={dialog} class="glass" onclose={() => (open = false)} aria-label={title}>
  {#if open}
    <header>
      <h2>{title}</h2>
      <button class="btn ghost small" type="button" onclick={() => (open = false)} aria-label="Close">✕</button>
    </header>
    <div class="body">{@render children()}</div>
    {#if actions}
      <footer>{@render actions()}</footer>
    {/if}
  {/if}
</dialog>

<style>
  dialog {
    width: min(520px, calc(100vw - 32px));
    padding: 0;
    color: var(--text);
    background: var(--panel-solid);
  }

  dialog::backdrop {
    background: rgba(3, 4, 8, 0.65);
    backdrop-filter: blur(4px);
  }

  header,
  footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    padding: 16px 20px;
  }

  header {
    border-bottom: 1px solid var(--border);
  }

  footer {
    justify-content: flex-end;
    border-top: 1px solid var(--border);
  }

  .body {
    display: flex;
    flex-direction: column;
    gap: 14px;
    padding: 20px;
  }
</style>
