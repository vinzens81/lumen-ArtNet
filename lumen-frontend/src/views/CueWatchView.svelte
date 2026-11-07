<script lang="ts">
  import { onMount } from 'svelte';
  import SpeechProgress from '../components/SpeechProgress.svelte';
  import { showClock } from '../lib/format';
  import { live } from '../lib/live.svelte';
  import { parseScript, stopOf, stops } from '../lib/script';
  import { app } from '../lib/state.svelte';
  import { wakeLock } from '../lib/wakeLock.svelte';
  import { watchCaption, watchUpcoming } from '../lib/watch';
  import { watchScenes } from '../lib/watchScenes.svelte';

  /**
   * The cue list on a phone, view only: the show time, the scene on stage with how far its script is, and the next
   * three, each with its name and cue note. Nothing here changes the show.
   */

  const scenes = watchScenes();
  let now = $state(performance.now());

  const status = $derived(live.playback);
  const current = $derived(status?.currentSceneId != null
    ? scenes.list.find((s) => s.id === status.currentSceneId) : undefined);
  const caption = $derived(watchCaption(scenes.list, status));
  const upcoming = $derived(watchUpcoming(scenes.list, status));
  const scriptStops = $derived(stops(parseScript(current?.script ?? '')));
  const stopIndex = $derived(stopOf(scriptStops, status?.scriptLine ?? 0, status?.section ?? 0));

  onMount(() => {
    // the show clock only arrives with each status; count on in between
    const timer = setInterval(() => (now = performance.now()), 250);
    const releaseWakeLock = wakeLock.hold();
    return () => {
      clearInterval(timer);
      releaseWakeLock();
    };
  });
</script>

<section class="cue-watch">
  <header class="bar">
    <span class="project dim">{app.active?.name ?? 'No active project'}</span>
    <span class="badge" class:off={!live.connected} title={live.connected ? 'Live' : 'Connection lost – reconnecting'}>
      <span class="dot"></span>{live.connected ? 'Live' : 'Offline'}
    </span>
    {#if live.master.blackout}<span class="badge danger">Blackout</span>{/if}
    <a class="link" href="#/setup/scenes" title="Leave the view-only page">✕</a>
  </header>

  <div class="clock mono" class:stopped={!status?.showTimeRunning} title="Show time since the GO into the first scene">
    {showClock(status, now - live.playbackReceivedAt)}
  </div>
  {#if wakeLock.state === 'needs-tap'}
    <p class="tap">Tap to keep the screen on</p>
  {/if}

  <article class="cue now glass">
    <h3>Now {#if status?.paused}<span class="paused">Paused</span>{/if}</h3>
    {#if current}
      <div class="name"><span class="mono">{current.number}</span> {current.name}</div>
      {#if caption.part}<div class="part">💡 {caption.part}</div>{/if}
      {#if current.cueNote}<div class="note">{current.cueNote}</div>{/if}
      {#if scriptStops.length > 1}
        <div class="progress"><SpeechProgress index={stopIndex} count={scriptStops.length} /></div>
      {/if}
    {:else}
      <div class="name dim">{caption.title}</div>
    {/if}
  </article>

  {#if upcoming.scenes.length > 0 || upcoming.end}
    <h3 class="next-head">Next</h3>
    <ol class="next">
      {#each upcoming.scenes as scene, i (scene.id)}
        <li class="cue glass" class:first={i === 0}>
          <div class="name"><span class="mono">{scene.number}</span> {scene.name}</div>
          {#if scene.cueNote}<div class="note">{scene.cueNote}</div>{/if}
        </li>
      {/each}
      {#if upcoming.end}
        <li class="cue glass end" class:first={upcoming.scenes.length === 0}>
          <div class="name dim">End of the show</div>
        </li>
      {/if}
    </ol>
  {/if}
</section>

<style>
  .cue-watch {
    display: flex;
    flex-direction: column;
    gap: 10px;
    max-width: 640px;
    min-height: 100dvh;
    margin: 0 auto;
    padding: max(10px, env(safe-area-inset-top)) max(16px, env(safe-area-inset-right))
      max(16px, env(safe-area-inset-bottom)) max(16px, env(safe-area-inset-left));
  }

  .bar {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .project {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 13px;
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

  .badge.danger {
    border-color: var(--danger);
    color: var(--danger);
    font-weight: 700;
  }

  .dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--ok);
    box-shadow: 0 0 8px var(--ok);
  }

  .badge.off .dot {
    background: var(--danger);
    box-shadow: 0 0 8px var(--danger);
  }

  .link {
    padding: 4px 8px;
    color: var(--text-dim);
    text-decoration: none;
  }

  .clock {
    text-align: center;
    font-size: 56px;
    font-weight: 700;
    line-height: 1.1;
    color: var(--accent);
    font-variant-numeric: tabular-nums;
  }

  .clock.stopped {
    color: var(--text-dim);
  }

  .tap {
    margin: 0;
    text-align: center;
    font-size: 12px;
    color: var(--warning);
  }

  h3 {
    margin: 0 0 6px;
    font-size: 12px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--text-dim);
  }

  .next-head {
    margin: 6px 0 0;
  }

  .paused {
    margin-left: 6px;
    color: var(--warning);
  }

  .cue {
    padding: 12px 14px;
    overflow-wrap: anywhere;
  }

  .now {
    border-color: var(--accent);
  }

  .now .name {
    font-size: 24px;
    font-weight: 700;
  }

  .name {
    font-size: 18px;
    font-weight: 600;
  }

  .name .mono {
    color: var(--accent);
    margin-right: 4px;
  }

  .part {
    margin-top: 4px;
    color: var(--warning);
    font-size: 14px;
  }

  .note {
    margin-top: 6px;
    color: var(--text-dim);
    font-size: 15px;
    line-height: 1.35;
  }

  /* the bar across the card, so it can be read at a glance */
  .progress {
    display: flex;
    margin-top: 10px;
  }

  .progress > :global(.speech-progress) {
    flex: 1;
    padding: 6px 10px;
    font-size: 15px;
  }

  .now .note {
    color: var(--text);
    font-size: 17px;
  }

  .next {
    display: flex;
    flex-direction: column;
    gap: 8px;
    margin: 0;
    padding: 0;
    list-style: none;
  }

  .next .cue:not(.first) {
    opacity: 0.7;
  }
</style>
