import NoSleep from 'nosleep.js';

/**
 * - `active`: the screen stays on
 * - `needs-tap`: the browser only allows it after a user gesture (tablets over plain HTTP); the next tap or key
 *   press switches it on
 * - `off`: nobody asked to keep the screen on
 */
export type WakeLockState = 'active' | 'needs-tap' | 'off';

/**
 * Keeps the screen from sleeping while a view needs it, e.g. during the show. Uses the Screen Wake Lock API where
 * available (HTTPS or localhost; NoSleep.js takes it again after the tab was hidden) and otherwise NoSleep.js's
 * invisible looping video, which works over plain HTTP but may only start after a tap.
 */
class ScreenWakeLock {
  state = $state<WakeLockState>('off');

  private noSleep: NoSleep | null = null;
  private holders = 0;

  /** Keeps the screen on until the returned function is called. */
  hold(): () => void {
    this.holders++;
    if (this.holders === 1) this.start();
    let released = false;
    return () => {
      if (released) return;
      released = true;
      this.holders--;
      if (this.holders === 0) this.stop();
    };
  }

  private start() {
    this.noSleep ??= new NoSleep();
    document.addEventListener('pointerdown', this.onGesture, true);
    document.addEventListener('keydown', this.onGesture, true);
    document.addEventListener('visibilitychange', this.onVisible);
    this.enable();
  }

  private stop() {
    document.removeEventListener('pointerdown', this.onGesture, true);
    document.removeEventListener('keydown', this.onGesture, true);
    document.removeEventListener('visibilitychange', this.onVisible);
    this.noSleep?.disable();
    this.state = 'off';
  }

  private enable() {
    this.noSleep!.enable().then(
      () => {
        if (this.holders > 0) this.state = 'active';
      },
      () => {
        if (this.holders > 0) this.state = 'needs-tap';
      },
    );
  }

  private onGesture = () => {
    if (this.state === 'needs-tap') this.enable();
  };

  /** The video stops while the page is hidden; the native lock is taken again by NoSleep.js itself. */
  private onVisible = () => {
    if (document.visibilityState === 'visible' && !('wakeLock' in navigator)) this.enable();
  };
}

export const wakeLock = new ScreenWakeLock();
