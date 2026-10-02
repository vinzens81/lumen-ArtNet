import { ApiError, api } from './api';
import { showError } from './toasts.svelte';
import type { AccessStatus } from './types';

/** Configuration PIN: whether this browser may change the setup, and the PIN prompt. */
class AccessState {
  status = $state<AccessStatus | null>(null);
  promptOpen = $state(false);
  /** setup is protected by a PIN and this browser has not unlocked it */
  locked = $derived(this.status !== null && this.status.pinSet && !this.status.unlocked);

  private afterUnlock: (() => void) | null = null;

  async load() {
    try {
      this.status = await api.access.status();
    } catch (e) {
      showError(e);
    }
  }

  /** Asks for the PIN; `then` runs once this browser is unlocked. */
  requestUnlock(then?: () => void) {
    this.afterUnlock = then ?? null;
    this.promptOpen = true;
  }

  /** @returns an error message, or null when unlocked */
  async unlock(pin: string): Promise<string | null> {
    try {
      this.status = await api.access.unlock(pin);
    } catch (e) {
      return e instanceof ApiError ? e.message : String(e);
    }
    this.promptOpen = false;
    const then = this.afterUnlock;
    this.afterUnlock = null;
    then?.();
    return null;
  }

  cancel() {
    this.afterUnlock = null;
    this.promptOpen = false;
  }

  async lock() {
    try {
      await api.access.lock();
      await this.load();
    } catch (e) {
      showError(e);
    }
  }
}

export const access = new AccessState();
