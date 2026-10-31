<script lang="ts">
  import { onMount } from 'svelte';
  import FaderBar from '../components/FaderBar.svelte';
  import KeyBar from '../components/KeyBar.svelte';
  import MasterBar from '../components/MasterBar.svelte';
  import NextSceneText from '../components/NextSceneText.svelte';
  import ScriptText from '../components/ScriptText.svelte';
  import SpeechProgress from '../components/SpeechProgress.svelte';
  import { api } from '../lib/api';
  import { showClock } from '../lib/format';
  import { live } from '../lib/live.svelte';
  import { nowProgress, remoteBlocked, stepNext } from '../lib/remote';
  import {
    NEXT_SCENE_ARM_MS, parseScript, scriptDownAction, stepStop, stopOf, stops, stopSelector,
  } from '../lib/script';
  import { topIn } from '../lib/scroll';
  import { app } from '../lib/state.svelte';
  import { showError } from '../lib/toasts.svelte';
  import { wakeLock } from '../lib/wakeLock.svelte';
  import { watchScenes } from '../lib/watchScenes.svelte';
  import type { PlaybackStatus } from '../lib/types';

  /**
   * The show on a tablet: now and next, GO, Back, Pause, Finish fade, Stop, the script of the scene with ▲/▼ through
   * its speeches, the keys of the scene, the faders, master and blackout – nothing else. No scene list, stage or DMX.
   * Works only in show mode, like the show view needs no PIN and does not follow the operating mode.
   */

  const GO_GUARD_MS = 300;
  const SIZE_KEY = 'lumen.remoteTextSize';
  const SIZES = [18, 20, 22, 24, 28, 32, 36];

  const scenes = watchScenes();
  let now = $state(performance.now());
  let lastGo = 0;

  const status = $derived(live.playback);
  const byId = $derived(new Map(scenes.list.map((s) => [s.id, s])));
  const current = $derived(status?.currentSceneId != null ? byId.get(status.currentSceneId) : undefined);
  const next = $derived(status?.nextSceneId != null ? byId.get(status.nextSceneId) : undefined);
  const atEnd = $derived(status !== null && status.position >= status.sceneCount && status.sceneCount > 0);
  const blocked = $derived(remoteBlocked(live.mode, live.modeKnown, status));
  const canGo = $derived(blocked === null && (next !== undefined || status?.nextIsEnd === true));
  const progress = $derived(nowProgress(status, now - live.playbackReceivedAt, current?.autoFollowMs ?? null));
  const script = $derived(parseScript(current?.script ?? ''));
  const scriptLine = $derived(status?.scriptLine ?? 0);
  const scriptSection = $derived(status?.section ?? 0);
  /** the places ▲/▼ stop at: speeches, pauses, and light markers without speech or pause after them */
  const scriptStops = $derived(stops(script));
  const stopIndex = $derived(stopOf(scriptStops, scriptLine, scriptSection));
  let scriptElement: HTMLElement | undefined = $state();
  let textSize = $state(readSize());

  function readSize(): number {
    try {
      const stored = Number(localStorage.getItem(SIZE_KEY));
      return SIZES.includes(stored) ? stored : 24;
    } catch {
      return 24;
    }
  }

  function resize(step: number) {
    textSize = SIZES[Math.min(SIZES.length - 1, Math.max(0, SIZES.indexOf(textSize) + step))];
    try {
      localStorage.setItem(SIZE_KEY, String(textSize));
    } catch {
      // only a convenience
    }
  }

  // the current speech at the top of the script panel, what comes next below it – in the scene on stage, not the next one
  $effect(() => {
    const selector = `.current-scene ${stopSelector(scriptStops[stopIndex])}`;
    const element = scriptElement;
    void current;
    void textSize;
    // after the layout, so it also works when the page opens in the middle of a scene
    const frame = requestAnimationFrame(() => topIn(element, selector));
    return () => cancelAnimationFrame(frame);
  });
  const stoppable = $derived(status !== null && status.available && (status.position >= 0
    || status.keys.some((k) => k.level > 0) || status.faders.some((f) => f.level > 0)));

  onMount(() => {
    let frame = requestAnimationFrame(function tick() {
      now = performance.now();
      frame = requestAnimationFrame(tick);
    });
    const releaseWakeLock = wakeLock.hold();
    return () => {
      cancelAnimationFrame(frame);
      releaseWakeLock();
    };
  });

  function go() {
    const t = performance.now();
    if (t - lastGo < GO_GUARD_MS || !canGo) return;
    lastGo = t;
    live.playbackCommand('go');
  }

  function apply(request: Promise<PlaybackStatus>) {
    request
      .then((s) => {
        live.playback = s;
        live.playbackReceivedAt = performance.now();
      })
      .catch(showError);
  }

  function chooseNext(step: number) {
    const target = stepNext(scenes.list, status, step);
    if (target) apply(api.playback.setNext(target.id));
  }

  /** a first ▼ at the last speech asks; a second tap within a few seconds is a GO into the next scene */
  let nextArmedFor = $state<number | null>(null);
  let nextArmTimer: ReturnType<typeof setTimeout> | null = null;
  const nextArmed = $derived(nextArmedFor !== null && nextArmedFor === current?.id);
  const downAction = $derived(current
    ? scriptDownAction(stopIndex, scriptStops.length, canGo, nextArmed)
    : 'none');
  const nextLabel = $derived(next ? `${next.number} ${next.name}` : 'end of show');

  function scriptDown() {
    if (downAction === 'speech') {
      stepScript(1);
    } else if (downAction === 'arm' && current) {
      nextArmedFor = current.id;
      if (nextArmTimer) clearTimeout(nextArmTimer);
      nextArmTimer = setTimeout(() => (nextArmedFor = null), NEXT_SCENE_ARM_MS);
    } else if (downAction === 'go') {
      nextArmedFor = null;
      go();
    }
  }

  function stepScript(step: number) {
    nextArmedFor = null;
    if (!current) return;
    const target = stepStop(scriptStops, scriptLine, scriptSection, step);
    // passing a light marker fades to the light of its part
    if (target.line !== scriptLine || target.section !== scriptSection) {
      live.scriptCommand(current.id, target.line, target.section);
    }
  }

  /** the first tap on Stop only asks; a second one within a few seconds stops */
  let stopArmed = $state(false);
  let stopTimer: ReturnType<typeof setTimeout> | null = null;

  function stop() {
    if (!stopArmed) {
      stopArmed = true;
      stopTimer = setTimeout(() => (stopArmed = false), 3000);
      return;
    }
    if (stopTimer) clearTimeout(stopTimer);
    stopArmed = false;
    apply(api.playback.stop());
  }
</script>

<section class="remote">
  <header class="bar glass">
    <span class="orb" aria-hidden="true"></span>
    <span class="what">
      <strong>{app.active?.name ?? 'No active project'}</strong>
      <span class="dim small">Show remote · {live.mode} mode</span>
    </span>
    {#if wakeLock.state === 'needs-tap'}
      <span class="badge warning" title="The browser only keeps the screen on after a tap">Tap to keep the screen on</span>
    {/if}
    <span class="show-clock mono" class:stopped={!status?.showTimeRunning}
      title="Show time since the GO into the first scene; ■ Stop resets it">
      ⏱ {showClock(status, now - live.playbackReceivedAt)}
    </span>
    <a class="link" href="#/show" title="Leave the remote">✕</a>
  </header>

  <!-- every panel has a fixed size: long names and cue notes are cut, the script scrolls in its panel – nothing moves -->
  <div class="main">
    {#if blocked}
      <p class="notice glass" role="status">{blocked}</p>
    {/if}

    <div class="left" inert={blocked !== null}>
      <article class="panel glass now-panel">
        <div class="panel-head">
          <h3>Now {#if status?.paused}<span class="badge warning">Paused</span>{/if}</h3>
          <span class="small mono progress-text" class:follow={progress.kind === 'follow'}>{progress.text}</span>
        </div>
        {#if current}
          <div class="big"><span class="mono">{current.number}</span> {current.name}</div>
        {:else if atEnd}
          <div class="big dim">End of show</div>
        {:else}
          <div class="big dim">Ready – stage is dark</div>
        {/if}
        <div class="progress {progress.kind}"><span style="width: {progress.value * 100}%"></span></div>
      </article>

      <article class="panel glass next-panel">
        <div class="panel-head">
          <h3>Next</h3>
          <div class="choose" role="group" aria-label="Choose the next scene">
            <button type="button" class="btn" onclick={() => chooseNext(-1)}
              disabled={stepNext(scenes.list, status, -1) === null} aria-label="Previous scene as next">▲</button>
            <button type="button" class="btn" onclick={() => chooseNext(1)}
              disabled={stepNext(scenes.list, status, 1) === null} aria-label="Following scene as next">▼</button>
          </div>
        </div>
        {#if next}
          <div class="big"><span class="mono">{next.number}</span> {next.name}</div>
          {#if next.cueNote}<div class="cue">{next.cueNote}</div>{/if}
        {:else if status?.nextIsEnd}
          <div class="big">End – fade to black</div>
        {:else}
          <div class="big dim">—</div>
        {/if}
      </article>

      <button type="button" class="go" disabled={!canGo} onpointerdown={go} onclick={(e) => e.detail === 0 && go()}>
        GO
      </button>
      <div class="controls">
        <button type="button" class="btn" onclick={() => live.playbackCommand('back')}
          disabled={!status || status.position < 0}>◀ Back</button>
        <button type="button" class="btn" onclick={() => live.playbackCommand('pause')} disabled={!status?.available}>
          {status?.paused ? '▶ Resume' : '⏸ Pause'}
        </button>
        <button type="button" class="btn" onclick={() => live.playbackCommand('finish')}
          disabled={!status?.fade}>⏭ Finish fade</button>
        <button type="button" class="btn stop" class:armed={stopArmed} onclick={stop} disabled={!stoppable}
          title="Stop the show at once: scene, keys and faders to 0, the next GO starts from the beginning">
          {stopArmed ? 'Really stop?' : '■ Stop'}
        </button>
      </div>

      <div class="keys glass"><KeyBar keys={status?.keys ?? []} /></div>
      <!-- on the left too, so the script on the right keeps its full height -->
      {#if (status?.faders.length ?? 0) > 0}
        <div class="faders glass"><FaderBar faders={status?.faders ?? []} /></div>
      {/if}
    </div>

    <div class="right" inert={blocked !== null}>
      <!-- always there, also for a scene without text, so nothing moves -->
      <article class="panel glass text-panel">
        <div class="panel-head">
          <h3>Text
            {#if scriptStops.length > 1}
              <SpeechProgress index={stopIndex} count={scriptStops.length} />
            {/if}
          </h3>
          <div class="text-tools">
            <button type="button" class="btn small" onclick={() => resize(-1)} disabled={textSize === SIZES[0]}
              aria-label="Smaller text">A−</button>
            <button type="button" class="btn small" onclick={() => resize(1)}
              disabled={textSize === SIZES[SIZES.length - 1]} aria-label="Larger text">A+</button>
            <button type="button" class="btn small back-speech" onclick={() => stepScript(-1)}
              disabled={!current || stopIndex <= 0} aria-label="Previous speech">▲</button>
          </div>
        </div>
        {#if current}
          <div class="script" style="font-size: {textSize}px" bind:this={scriptElement}>
            <div class="script-body">
              <div class="current-scene">
                {#if script.parts.length > 0}
                  <ScriptText {script} currentLine={scriptLine} section={status?.section ?? 0} />
                {:else}
                  <p class="dim">This scene has no text.</p>
                {/if}
              </div>
              <!-- what comes after the cut, to read ahead -->
              <NextSceneText {next} end={status?.nextIsEnd ?? false} />
            </div>
          </div>
        {:else}
          <p class="script-empty dim">The text of a scene appears here once it is on stage.</p>
        {/if}
        <!-- pressed most during the show: across the whole panel at the bottom, where the thumb is -->
        <!-- at the last speech it leads on: tap twice for a GO into the next scene -->
        <button type="button" class="next-speech" class:to-scene={downAction !== 'speech'} class:armed={nextArmed}
          onclick={scriptDown} disabled={downAction === 'none'}
          aria-label={downAction === 'speech' ? 'Next speech' : 'Next scene, tap twice'}>
          {#if downAction === 'speech'}▼{:else if nextArmed}GO {nextLabel}?{:else}▼ Next scene{/if}
        </button>
      </article>
    </div>
  </div>

  <MasterBar />
</section>

<style>
  /*
   * A tablet in landscape: a narrow column on the left with now, next, GO, the playback buttons, the keys and the
   * faders, all compact; the script gets the rest of the screen, so it can be read from a distance; master below. The page fills the screen exactly and every panel has a size that does not depend on
   * what it shows.
   */
  .remote {
    display: flex;
    flex-direction: column;
    gap: 10px;
    height: 100dvh;
    padding: 10px;
    /* no double-tap zoom and no text selection when tapping fast */
    touch-action: manipulation;
    user-select: none;
    -webkit-user-select: none;
  }

  .remote > :global(*) {
    flex: none;
  }

  .bar {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 8px 14px;
    white-space: nowrap;
  }

  .orb {
    flex: none;
    width: 12px;
    height: 12px;
    border-radius: 50%;
    background: radial-gradient(circle, #fff 0 25%, var(--accent) 60%);
    box-shadow: 0 0 12px var(--accent-glow);
  }

  .what {
    flex: 1;
    min-width: 0;
    display: flex;
    align-items: baseline;
    gap: 10px;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .show-clock {
    font-size: 22px;
    font-weight: 700;
    color: var(--accent);
  }

  .show-clock.stopped {
    color: var(--text-dim);
  }

  .link {
    padding: 6px 10px;
    border-radius: var(--radius-sm);
    color: var(--text-dim);
    text-decoration: none;
  }

  .remote > .main {
    position: relative;
    flex: 1 1 0;
    display: grid;
    grid-template-columns: minmax(280px, 30%) minmax(0, 1fr);
    gap: 10px;
    min-height: 0;
  }

  /* over the panels instead of above them, so nothing moves when the mode changes */
  .notice {
    position: absolute;
    top: 50%;
    left: 50%;
    z-index: 5;
    width: min(520px, 90%);
    margin: 0;
    padding: 18px 22px;
    transform: translate(-50%, -50%);
    border-color: var(--warning);
    background: var(--panel-solid);
    box-shadow: var(--shadow);
    color: var(--warning);
    font-size: 16px;
    font-weight: 600;
    text-align: center;
  }

  .left,
  .right {
    display: flex;
    flex-direction: column;
    gap: 10px;
    min-height: 0;
  }

  .left[inert],
  .right[inert] {
    opacity: 0.35;
  }

  .left > *,
  .right > * {
    flex: none;
  }

  .panel {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 10px 14px;
    overflow: hidden;
  }

  .panel h3 {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  /* next with its cue note gets the room that is left, never less than it needs; a short screen scrolls the column */
  .left > .next-panel {
    flex: 1 0 auto;
  }

  .left {
    overflow-y: auto;
  }

  @media (max-width: 900px) {
    .left {
      overflow: visible;
    }
  }

  .now-panel {
    border-color: var(--accent);
    box-shadow: 0 0 30px var(--accent-soft);
  }

  .panel-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
  }

  .choose {
    display: flex;
    gap: 6px;
  }

  .choose .btn {
    min-width: 40px;
    min-height: 32px;
    padding: 0 8px;
  }

  /* cut after a few lines instead of growing; max-height as well, some browsers only draw the "…" */
  .big,
  .cue {
    display: -webkit-box;
    overflow: hidden;
    -webkit-box-orient: vertical;
  }

  .big {
    flex: none;
    font-size: 22px;
    font-weight: 700;
    line-height: 1.2;
    max-height: 2.4em;
    -webkit-line-clamp: 2;
    line-clamp: 2;
  }

  .big .mono {
    color: var(--accent);
  }

  .cue {
    flex: none;
    padding: 8px 12px;
    border-radius: var(--radius-sm);
    background: var(--warning-soft);
    color: var(--warning);
    font-size: 16px;
    font-weight: 600;
    line-height: 1.3;
    max-height: calc(3.9em + 16px);
    -webkit-line-clamp: 3;
    line-clamp: 3;
  }

  .progress {
    flex: none;
    height: 8px;
    margin-top: auto;
    border-radius: 999px;
    background: rgba(255, 255, 255, 0.08);
    overflow: hidden;
  }

  .progress span {
    display: block;
    height: 100%;
    border-radius: 999px;
    background: var(--accent);
    box-shadow: 0 0 12px var(--accent-glow);
  }

  .progress.follow span {
    background: var(--ok);
  }

  .progress-text {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    color: var(--text-dim);
    white-space: nowrap;
  }

  .progress-text.follow {
    color: var(--ok);
  }

  .small {
    font-size: 12px;
  }

  .keys {
    padding: 10px;
  }

  .keys :global(.key) {
    min-height: 40px;
  }

  .left > .go {
    height: 64px;
  }

  .go {
    border-radius: 24px;
    border: 2px solid var(--accent);
    background: radial-gradient(circle at 50% 40%, color-mix(in srgb, var(--accent) 35%, transparent), var(--accent-soft));
    color: var(--accent);
    font: inherit;
    font-size: 30px;
    font-weight: 900;
    letter-spacing: 0.25em;
    text-indent: 0.25em;
    cursor: pointer;
    box-shadow: 0 0 24px var(--accent-glow), inset 0 0 24px var(--accent-soft);
    transition: transform 0.06s, box-shadow 0.15s;
    touch-action: manipulation;
  }

  .go:active:not(:disabled) {
    transform: scale(0.98);
    box-shadow: 0 0 70px var(--accent-glow), inset 0 0 60px var(--accent-glow);
  }

  .go:disabled {
    opacity: 0.3;
    cursor: not-allowed;
    box-shadow: none;
  }

  .controls {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 6px;
  }

  .controls .btn {
    height: 38px;
    overflow: hidden;
    font-size: 13px;
    white-space: nowrap;
  }

  .controls .stop:not(:disabled) {
    border-color: var(--danger);
    color: var(--danger);
  }

  .controls .stop.armed {
    background: var(--danger);
    color: #fff;
  }

  .right > .text-panel {
    flex: 1 1 0;
    min-height: 0;
  }

  .text-tools {
    display: flex;
    gap: 6px;
  }

  .text-tools .btn {
    min-width: 40px;
    min-height: 32px;
  }

  /* the whole script of the scene, scrolling inside the panel; the current speech stands at the top */
  .script {
    flex: 1 1 0;
    min-height: 0;
    overflow-y: auto;
    line-height: 1.45;
  }

  /* room below, so the last speech can reach the top too – inside the scrolling box, so the panel keeps its size */
  .script-body {
    padding: 0 4px 60vh;
  }

  .script-empty {
    flex: 1 1 0;
    margin: 0;
  }

  .next-speech {
    flex: none;
    height: 56px;
    margin-top: auto;
    border-radius: var(--radius);
    border: 2px solid var(--warning);
    background: var(--warning-soft);
    color: var(--warning);
    font: inherit;
    font-size: 26px;
    font-weight: 900;
    cursor: pointer;
    touch-action: manipulation;
    transition: transform 0.06s;
  }

  .next-speech:active:not(:disabled) {
    transform: scale(0.98);
  }

  .next-speech.to-scene {
    font-size: 18px;
    font-weight: 700;
  }

  .next-speech.armed {
    background: var(--warning);
    color: var(--bg);
  }

  .next-speech:disabled {
    opacity: 0.3;
    cursor: not-allowed;
  }

  .faders {
    padding: 10px 14px;
  }

  /* a tablet in portrait: one column that scrolls, every panel with a fixed height */
  @media (max-width: 900px) {
    .remote {
      height: auto;
      min-height: 100dvh;
    }

    .remote > .main {
      flex: none;
      display: flex;
      flex-direction: column;
    }

    /* the panels of both sides in one column, in the order of the show: now, next, GO, buttons, text, keys */
    .left,
    .right {
      display: contents;
    }

    .left > .now-panel,
    .left > .next-panel {
      flex: none;
      height: 130px;
    }

    .left > .go {
      order: 3;
    }

    /* the screen between the buttons and master, so ▼ is in reach without scrolling */
    .right > .text-panel {
      flex: none;
      height: max(330px, calc(100dvh - 550px));
    }

    .next-panel {
      order: 2;
    }

    .controls {
      order: 4;
      grid-template-columns: repeat(4, minmax(0, 1fr));
    }

    .text-panel {
      order: 5;
    }

    .keys {
      order: 6;
    }

    .faders {
      order: 7;
    }
  }
</style>
