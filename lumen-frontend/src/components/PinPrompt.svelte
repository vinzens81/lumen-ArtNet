<script lang="ts">
  import { access } from '../lib/access.svelte';
  import { live } from '../lib/live.svelte';
  import Modal from './Modal.svelte';

  let pin = $state('');
  let error = $state<string | null>(null);
  let busy = $state(false);
  let input: HTMLInputElement | undefined = $state();

  $effect(() => {
    if (access.promptOpen) {
      pin = '';
      error = null;
      queueMicrotask(() => input?.focus());
    }
  });

  function setOpen(value: boolean) {
    if (!value) access.cancel();
  }

  function press(digit: string) {
    if (pin.length < 12) pin += digit;
    input?.focus();
  }

  async function submit() {
    if (pin.length === 0 || busy) return;
    busy = true;
    error = await access.unlock(pin);
    busy = false;
    if (error === null) {
      live.reconnect(); // the live connection picks up the unlocked session
    } else {
      pin = '';
      input?.focus();
    }
  }
</script>

<Modal bind:open={() => access.promptOpen, setOpen} title="Enter PIN">
  <form id="pin-form" onsubmit={(e) => { e.preventDefault(); submit(); }}>
    <p class="dim">Setup is locked. Enter the configuration PIN to make changes.</p>
    <input
      bind:this={input}
      bind:value={pin}
      class="pin mono"
      type="password"
      inputmode="numeric"
      autocomplete="off"
      maxlength="12"
      aria-label="PIN"
      aria-invalid={error !== null}
    />
    {#if error}<p class="error" role="alert">{error}</p>{/if}
    <div class="keypad">
      {#each ['1', '2', '3', '4', '5', '6', '7', '8', '9'] as digit (digit)}
        <button type="button" class="btn" onclick={() => press(digit)}>{digit}</button>
      {/each}
      <button type="button" class="btn ghost" onclick={() => (pin = pin.slice(0, -1))} aria-label="Delete">⌫</button>
      <button type="button" class="btn" onclick={() => press('0')}>0</button>
      <button type="submit" class="btn primary" disabled={pin.length === 0 || busy}>OK</button>
    </div>
  </form>
</Modal>

<style>
  form {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  p {
    margin: 0;
  }

  .pin {
    min-height: 52px;
    padding: 0 14px;
    border-radius: var(--radius-sm);
    border: 1px solid var(--border-strong);
    background: rgba(0, 0, 0, 0.3);
    color: var(--text);
    font-size: 28px;
    letter-spacing: 0.4em;
    text-align: center;
  }

  .pin:focus {
    outline: none;
    border-color: var(--accent);
    box-shadow: 0 0 0 3px var(--accent-soft);
  }

  .error {
    color: var(--danger);
    font-weight: 600;
  }

  .keypad {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 8px;
  }

  .keypad .btn {
    min-height: 54px;
    font-size: 20px;
    font-weight: 600;
  }
</style>
