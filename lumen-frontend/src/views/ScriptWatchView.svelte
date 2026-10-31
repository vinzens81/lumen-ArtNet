<script lang="ts">
  import { onMount } from 'svelte';
  import NextSceneText from '../components/NextSceneText.svelte';
  import ScriptText from '../components/ScriptText.svelte';
  import WatchHeader from '../components/WatchHeader.svelte';
  import { live } from '../lib/live.svelte';
  import { parseScript, stopOf, stops, stopSelector } from '../lib/script';
  import { centerIn } from '../lib/scroll';
  import { wakeLock } from '../lib/wakeLock.svelte';
  import { watchCaption, watchIdleText } from '../lib/watch';
  import { watchScenes } from '../lib/watchScenes.svelte';

  /**
   * The script of the scene on stage, for following the text on a tablet or phone: the speech the operator is at is
   * highlighted and kept in the middle, the script of the next scene follows below a clear cut. Nothing here changes
   * the light or the position in the script.
   */

  const SIZE_KEY = 'lumen.scriptWatchSize';
  const SIZES = [16, 18, 20, 22, 26, 30, 36];
  /** how long scroll events after centering count as our own smooth scrolling, not the reader's */
  const AUTO_SCROLL_MS = 1000;

  const scenes = watchScenes();
  let body: HTMLElement | undefined = $state();
  let size = $state(readSize());
  /** off once the reader scrolls by hand, e.g. to read ahead; on again with the button or the next scene */
  let following = $state(true);
  let autoScrollUntil = 0;

  const status = $derived(live.playback);
  /** a primitive on purpose: the playback status is a new object with every message */
  const sceneId = $derived(status?.currentSceneId ?? null);
  const current = $derived(sceneId !== null ? scenes.list.find((s) => s.id === sceneId) : undefined);
  const script = $derived(parseScript(current?.script ?? ''));
  const scriptLine = $derived(status?.scriptLine ?? 0);
  /** what to keep in the middle: the current speech, or the light marker the operator stopped at */
  const scriptStops = $derived(stops(script));
  /** only in the scene on stage, not in the next one below it */
  const stopTarget = $derived(
    `.current-scene ${stopSelector(scriptStops[stopOf(scriptStops, scriptLine, status?.section ?? 0)])}`);
  const caption = $derived(watchCaption(scenes.list, status));
  const nextScene = $derived(status?.nextSceneId != null
    ? scenes.list.find((s) => s.id === status.nextSceneId) : undefined);
  const idle = $derived(watchIdleText(status));

  function readSize(): number {
    try {
      const stored = Number(localStorage.getItem(SIZE_KEY));
      return SIZES.includes(stored) ? stored : 22;
    } catch {
      return 22;
    }
  }

  function resize(step: number) {
    const index = Math.min(SIZES.length - 1, Math.max(0, SIZES.indexOf(size) + step));
    size = SIZES[index];
    try {
      localStorage.setItem(SIZE_KEY, String(size));
    } catch {
      // only a convenience
    }
  }

  // a new scene on stage: follow it again from its first speech
  $effect(() => {
    void sceneId;
    following = true;
  });

  // keep the current speech in the middle; the text size changes where it is
  $effect(() => {
    const target = stopTarget;
    void size;
    void current;
    if (following) center(target);
  });

  function center(selector: string) {
    autoScrollUntil = performance.now() + AUTO_SCROLL_MS;
    centerIn(body, selector);
  }

  function follow() {
    following = true;
    center(stopTarget);
  }

  // scrolling by the reader stops following – by wheel, touch, keyboard or scrollbar, not our own centering
  $effect(() => {
    const element = body;
    if (!element) return;
    const stop = () => {
      autoScrollUntil = 0;
      following = false;
    };
    const onScroll = () => {
      if (performance.now() > autoScrollUntil) following = false;
    };
    const onKey = (event: KeyboardEvent) => {
      if (['ArrowUp', 'ArrowDown', 'PageUp', 'PageDown', 'Home', 'End', ' '].includes(event.key)) stop();
    };
    element.addEventListener('wheel', stop, { passive: true });
    element.addEventListener('touchmove', stop, { passive: true });
    element.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('keydown', onKey);
    return () => {
      element.removeEventListener('wheel', stop);
      element.removeEventListener('touchmove', stop);
      element.removeEventListener('scroll', onScroll);
      window.removeEventListener('keydown', onKey);
    };
  });

  onMount(() => wakeLock.hold());
</script>

<section class="script-watch">
  <WatchHeader {caption} leave="#/setup/scenes"
    other={{ href: '#/view', label: '👁 Stage', title: 'The stage as it glows, view only' }}>
    {#if wakeLock.state === 'needs-tap'}
      <span class="badge warning" title="The browser only keeps the screen on after a tap">Tap to keep the screen on</span>
    {/if}
    <div class="sizes" role="group" aria-label="Text size">
      <button type="button" class="btn small" onclick={() => resize(-1)} disabled={size === SIZES[0]}
        aria-label="Smaller text">A−</button>
      <button type="button" class="btn small" onclick={() => resize(1)} disabled={size === SIZES[SIZES.length - 1]}
        aria-label="Larger text">A+</button>
    </div>
  </WatchHeader>

  <!-- tabindex: the text can be scrolled by keyboard as well -->
  <!-- svelte-ignore a11y_no_noninteractive_tabindex -->
  <div class="body glass" bind:this={body} tabindex="0">
    {#if idle}
      <p class="dim pad">{idle}</p>
    {:else if current}
      <div class="script" style="font-size: {size}px">
        <div class="current-scene">
          {#if script.parts.length === 0}
            <p class="dim">This scene has no script.</p>
          {:else}
            <ScriptText {script} currentLine={scriptLine} section={status?.section ?? 0} />
          {/if}
        </div>
        <NextSceneText next={nextScene} end={status?.nextIsEnd ?? false} />
      </div>
    {/if}
  </div>

  {#if !following && current && script.parts.length > 0}
    <button type="button" class="btn primary follow" onclick={follow}>↧ Follow the show</button>
  {/if}
  <p class="dim small hint">View only – the operator moves through the script, this page follows.</p>
</section>

<style>
  .script-watch {
    display: flex;
    flex-direction: column;
    gap: 10px;
    height: 100dvh;
    padding: 10px;
  }

  .sizes {
    display: flex;
    gap: 4px;
  }

  .badge {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 3px 9px;
    border-radius: 999px;
    border: 1px solid var(--border-strong);
    font-size: 12px;
    color: var(--text-dim);
  }

  .badge.warning {
    border-color: var(--warning);
    color: var(--warning);
  }

  .body {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
  }

  .script {
    /* room above and below, so the first and last speech can be centered too */
    max-width: 60em;
    margin: 0 auto;
    padding: 40vh 18px;
  }

  .body:focus-visible {
    outline: 2px solid var(--accent);
  }

  .pad {
    padding: 18px;
  }

  .follow {
    position: fixed;
    right: 28px;
    bottom: 44px;
    box-shadow: var(--shadow);
  }

  .hint {
    margin: 0;
    text-align: center;
  }

  .small {
    font-size: 12px;
  }
</style>
