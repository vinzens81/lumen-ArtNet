<script lang="ts">
  import { dismiss, toasts } from '../lib/toasts.svelte';
</script>

<div class="toasts" aria-live="polite">
  {#each toasts.items as toast (toast.id)}
    <div class="toast glass" class:error={toast.kind === 'error'}>
      <div class="text">
        <strong>{toast.message}</strong>
        {#each toast.details as detail, i (i)}
          <div class="detail">{detail}</div>
        {/each}
      </div>
      <button class="btn ghost small" type="button" onclick={() => dismiss(toast.id)} aria-label="Dismiss">✕</button>
    </div>
  {/each}
</div>

<style>
  .toasts {
    position: fixed;
    right: 16px;
    /* above the master bar, so the blackout button is never covered */
    bottom: 96px;
    z-index: 100;
    display: flex;
    flex-direction: column;
    gap: 10px;
    width: min(380px, calc(100vw - 32px));
  }

  .toast {
    display: flex;
    align-items: flex-start;
    gap: 10px;
    padding: 12px 12px 12px 16px;
    border-left: 3px solid var(--accent);
    animation: slide-in 0.2s ease-out;
  }

  .toast.error {
    border-left-color: var(--danger);
  }

  .text {
    flex: 1;
  }

  .detail {
    margin-top: 4px;
    font-size: 12px;
    color: var(--text-dim);
  }

  @keyframes slide-in {
    from {
      opacity: 0;
      transform: translateY(8px);
    }
  }
</style>
