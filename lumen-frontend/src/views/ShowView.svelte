<script lang="ts">
  import { onMount } from 'svelte';
  import KeyBar from '../components/KeyBar.svelte';
  import Swatches from '../components/Swatches.svelte';
  import { api } from '../lib/api';
  import { modesOf, swatchesOf, type Swatch } from '../lib/color';
  import { kindIcon } from '../lib/compositions';
  import { live } from '../lib/live.svelte';
  import { app } from '../lib/state.svelte';
  import { showError } from '../lib/toasts.svelte';
  import type { Fixture, PlaybackStatus, Scene } from '../lib/types';

  const GO_GUARD_MS = 300;

  let scenes = $state<Scene[]>([]);
  let fixtures = $state<Fixture[]>([]);
  let now = $state(performance.now());
  let lastGo = 0;
  let listElement: HTMLElement | undefined = $state();

  const projectId = $derived(app.active?.id ?? null);
  const status = $derived(live.playback);
  const byId = $derived(new Map(scenes.map((s) => [s.id, s])));
  const modes = $derived(modesOf(fixtures, (id) => app.fixtureType(id)));
  const swatches = $derived(
    new Map<number, Swatch[]>(scenes.map((s) => [s.id, swatchesOf(fixtures, modes, s.values)])),
  );
  const current = $derived(status?.currentSceneId != null ? byId.get(status.currentSceneId) : undefined);
  const next = $derived(status?.nextSceneId != null ? byId.get(status.nextSceneId) : undefined);
  const resumeScene = $derived(status?.resumeSceneId != null ? byId.get(status.resumeSceneId) : undefined);
  const outgoing = $derived(status?.fade?.fromSceneId != null ? byId.get(status.fade.fromSceneId) : undefined);

  /** Time since the status arrived, frozen while paused. */
  const sinceStatus = $derived(status?.paused ? 0 : Math.max(0, now - live.playbackReceivedAt));
  const elapsed = $derived((status?.fade?.elapsedMs ?? 0) + sinceStatus);
  const fadeInProgress = $derived(progress(elapsed, status?.fade?.fadeInMs ?? 0));
  const fadeOutProgress = $derived(progress(elapsed, status?.fade?.fadeOutMs ?? 0));
  const autoFollowLeft = $derived(
    status?.autoFollowInMs != null ? Math.max(0, status.autoFollowInMs - sinceStatus) : null,
  );
  const atEnd = $derived(status !== null && status.position >= status.sceneCount && status.sceneCount > 0);
  const canGo = $derived(status?.available === true && (next !== undefined || status.nextIsEnd));

  function progress(ms: number, duration: number): number {
    return duration <= 0 ? 1 : Math.min(1, ms / duration);
  }

  function seconds(ms: number): string {
    return `${(ms / 1000).toFixed(1)} s`;
  }

  $effect(() => {
    void live.scenesRevision;
    void live.patchRevision;
    if (projectId !== null) {
      Promise.all([api.scenes.list(projectId), api.fixtures.list(projectId)])
        .then(([sceneList, fixtureList]) => {
          scenes = sceneList;
          fixtures = fixtureList;
        })
        .catch(showError);
    }
  });

  // keep the current scene in view
  $effect(() => {
    const id = status?.currentSceneId;
    if (id == null || !listElement) return;
    listElement.querySelector(`[data-scene="${id}"]`)?.scrollIntoView({ block: 'center', behavior: 'smooth' });
  });

  onMount(() => {
    let frame = requestAnimationFrame(function tick() {
      now = performance.now();
      frame = requestAnimationFrame(tick);
    });
    let wakeLock: WakeLockSentinel | null = null;
    navigator.wakeLock?.request('screen').then((lock) => (wakeLock = lock)).catch(() => {});
    return () => {
      cancelAnimationFrame(frame);
      wakeLock?.release();
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

  function chooseNext(scene: Scene) {
    apply(api.playback.setNext(scene.id));
  }

  function moveNext(step: number) {
    if (scenes.length === 0) return;
    const index = next ? scenes.indexOf(next) : (status?.position ?? -1);
    const target = scenes[Math.max(0, Math.min(scenes.length - 1, index + step))];
    if (target) chooseNext(target);
  }

  function onkeydown(event: KeyboardEvent) {
    if ((event.target as HTMLElement).closest('input, textarea, select') || event.metaKey || event.ctrlKey) return;
    const actions: Record<string, () => void> = {
      ' ': go,
      ArrowLeft: () => live.playbackCommand('back'),
      p: () => live.playbackCommand('pause'),
      P: () => live.playbackCommand('pause'),
      Enter: () => live.playbackCommand('finish'),
      ArrowUp: () => moveNext(-1),
      ArrowDown: () => moveNext(1),
    };
    const action = actions[event.key];
    if (action) {
      event.preventDefault();
      action();
    }
  }

  function rowState(scene: Scene, index: number): string {
    if (scene.id === status?.currentSceneId) return 'current';
    if (scene.id === status?.nextSceneId) return 'next';
    if (status && index < status.position) return 'done';
    return '';
  }
</script>

<svelte:window {onkeydown} />

<section class="show">
  <div class="list glass" bind:this={listElement}>
    <header class="list-head">
      <h3>Scenes</h3>
      <span class="dim small">{app.active?.name}</span>
    </header>
    {#if scenes.length === 0}
      <p class="dim pad">This project has no scenes yet. Record some in setup mode.</p>
    {/if}
    {#each scenes as scene, i (scene.id)}
      <button type="button" class="cue-row {rowState(scene, i)}" data-scene={scene.id} onclick={() => chooseNext(scene)}
        title="Make this the next scene">
        <span class="number mono">{scene.number}</span>
        <span class="text">
          <span class="name">{scene.name}</span>
          {#if scene.cueNote}<span class="note">{scene.cueNote}</span>{/if}
        </span>
        <Swatches swatches={swatches.get(scene.id) ?? []} max={10} />
        {#if scene.compositions.length > 0}
          <span class="badge" title={scene.compositions.map((c) => c.name).join(', ')}>
            {scene.compositions.map((c) => kindIcon(c.kind)).join(' ')}
          </span>
        {/if}
        {#if scene.autoFollowMs !== null}<span class="badge">⟳ {seconds(scene.autoFollowMs)}</span>{/if}
        {#if rowState(scene, i) === 'current'}<span class="tag now">NOW</span>{/if}
        {#if rowState(scene, i) === 'next'}<span class="tag next">NEXT</span>{/if}
      </button>
    {/each}
    {#if scenes.length > 0}
      <div class="cue-row end" class:next={status?.nextIsEnd} class:current={atEnd}>
        <span class="number mono">■</span><span class="text"><span class="name">End – fade to black</span></span>
        {#if status?.nextIsEnd}<span class="tag next">NEXT</span>{/if}
      </div>
    {/if}
  </div>

  <div class="stage">
    <div class="info">
    {#if resumeScene}
      <article class="panel glass resume-panel" aria-live="polite">
        <h3>Resume the show?</h3>
        <p>
          Before the restart, scene <span class="mono">{resumeScene.number}</span> <strong>{resumeScene.name}</strong>
          was on stage. Resuming puts it back on stage at once, without a fade.
        </p>
        <div class="resume-actions">
          <button type="button" class="btn primary" onclick={() => apply(api.playback.resume())}>
            Resume at scene {resumeScene.number}
          </button>
          <button type="button" class="btn ghost" onclick={() => apply(api.playback.dismissResume())}>
            Start from the beginning
          </button>
        </div>
      </article>
    {/if}
    <article class="panel glass now-panel">
      <h3>Now {#if status?.paused}<span class="badge warning">Paused</span>{/if}</h3>
      {#if current}
        <div class="big"><span class="mono">{current.number}</span> {current.name}</div>
      {:else if atEnd}
        <div class="big dim">End of show</div>
      {:else}
        <div class="big dim">Ready – stage is dark</div>
      {/if}
      <!-- always rendered, so the panel keeps its height when a fade ends -->
      {#if status?.fade && fadeInProgress < 1}
        <div class="bar"><span style="width: {fadeInProgress * 100}%"></span></div>
        <div class="dim small mono">Fade in · {seconds(Math.max(0, status.fade.fadeInMs - elapsed))} left</div>
      {:else if autoFollowLeft !== null}
        <div class="bar follow"><span style="width: {(1 - autoFollowLeft / (current?.autoFollowMs ?? 1)) * 100}%"></span></div>
        <div class="small mono follow-text">Continues automatically in {seconds(autoFollowLeft)}</div>
      {:else}
        <div class="bar idle"></div>
        <div class="dim small mono">{status?.paused ? 'Paused' : 'Stays until GO'}</div>
      {/if}
    </article>

    {#if status?.fade && fadeOutProgress < 1}
      <article class="panel glass out-panel">
        <h3>Fading out</h3>
        <div class="medium">{outgoing ? `${outgoing.number} ${outgoing.name}` : 'Previous look'}</div>
        <div class="bar out"><span style="width: {(1 - fadeOutProgress) * 100}%"></span></div>
        <div class="dim small mono">{seconds(Math.max(0, status.fade.fadeOutMs - elapsed))} left</div>
      </article>
    {/if}

    <article class="panel glass next-panel">
      <h3>Next</h3>
      {#if next}
        <div class="big"><span class="mono">{next.number}</span> {next.name}</div>
        {#if next.cueNote}<div class="cue">{next.cueNote}</div>{/if}
      {:else if status?.nextIsEnd}
        <div class="big">End – fade to black</div>
      {:else}
        <div class="big dim">—</div>
      {/if}
    </article>

    </div>

    <div class="controls-area">
    <button type="button" class="go" disabled={!canGo} onpointerdown={go} onclick={(e) => e.detail === 0 && go()}>
      GO
      <span class="hint">Space</span>
    </button>

    <div class="controls">
      <button type="button" class="btn back" onclick={() => live.playbackCommand('back')} disabled={!status || status.position < 0}>◀ Back <kbd>←</kbd></button>
      <button type="button" class="btn secondary" onclick={() => live.playbackCommand('pause')} disabled={!status?.available}>
        {status?.paused ? '▶ Resume' : '⏸ Pause'} <kbd>P</kbd>
      </button>
      <button type="button" class="btn secondary" onclick={() => live.playbackCommand('finish')} disabled={!status?.fade}>⏭ Finish fade <kbd>↵</kbd></button>
    </div>
    <div class="key-bar"><KeyBar keys={status?.keys ?? []} /></div>
    </div>
  </div>
</section>

<style>
  /* fills the space between top bar and master bar, so GO stays at the same place on screen */
  .show {
    height: calc(100vh - 180px);
    min-height: 560px;
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(380px, 520px);
    gap: 12px;
  }

  .list {
    display: flex;
    flex-direction: column;
    padding: 8px;
    min-height: 0;
    overflow: auto;
  }

  .list-head {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    padding: 8px 10px 12px;
  }

  .pad {
    padding: 0 10px;
  }

  .small {
    font-size: 12px;
  }

  .cue-row {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 14px;
    border: 1px solid transparent;
    border-radius: var(--radius-sm);
    background: transparent;
    color: var(--text);
    font: inherit;
    text-align: left;
    cursor: pointer;
  }

  .cue-row:hover {
    background: var(--panel-raised);
  }

  .cue-row.done {
    opacity: 0.35;
  }

  .cue-row.current {
    border-color: var(--accent);
    background: var(--accent-soft);
    box-shadow: 0 0 30px var(--accent-soft);
  }

  .cue-row.next {
    border-color: var(--border-strong);
    border-style: dashed;
  }

  .cue-row.end {
    cursor: default;
  }

  .number {
    min-width: 56px;
    font-size: 20px;
    font-weight: 700;
    color: var(--accent);
  }

  .text {
    flex: 1;
    display: flex;
    flex-direction: column;
    min-width: 0;
  }

  .name {
    font-size: 16px;
    font-weight: 600;
  }

  .note {
    color: var(--text-dim);
    font-size: 13px;
  }

  .tag {
    padding: 3px 8px;
    border-radius: 6px;
    font-size: 11px;
    font-weight: 800;
    letter-spacing: 0.1em;
  }

  .tag.now {
    background: var(--accent);
    color: var(--accent-ink);
  }

  .tag.next {
    border: 1px solid var(--accent);
    color: var(--accent);
  }

  .stage {
    display: flex;
    flex-direction: column;
    gap: 12px;
    min-height: 0;
  }

  /* status panels take the remaining space and scroll if needed – they never push GO around */
  .info {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 12px;
    min-height: 0;
    overflow: auto;
  }

  .controls-area {
    flex: none;
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .panel {
    display: flex;
    flex-direction: column;
    gap: 10px;
    padding: 18px;
  }

  .panel h3 {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .big {
    font-size: 26px;
    font-weight: 700;
    line-height: 1.2;
  }

  .big .mono,
  .medium {
    color: var(--accent);
  }

  .medium {
    font-size: 17px;
    font-weight: 600;
  }

  .cue {
    padding: 12px 14px;
    border-radius: var(--radius-sm);
    background: var(--warning-soft);
    color: var(--warning);
    font-size: 18px;
    font-weight: 600;
  }

  .bar {
    height: 8px;
    border-radius: 999px;
    background: rgba(255, 255, 255, 0.08);
    overflow: hidden;
  }

  .bar span {
    display: block;
    height: 100%;
    border-radius: 999px;
    background: var(--accent);
    box-shadow: 0 0 12px var(--accent-glow);
  }

  .bar.out span {
    background: var(--text-dim);
    box-shadow: none;
  }

  .bar.idle {
    opacity: 0.5;
  }

  .bar.follow span {
    background: var(--ok);
  }

  .follow-text {
    color: var(--ok);
  }

  .resume-panel {
    border-color: var(--warning);
    box-shadow: 0 0 30px var(--warning-soft);
  }

  .resume-panel h3 {
    color: var(--warning);
  }

  .resume-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .resume-actions .btn {
    min-height: 48px;
  }

  .out-panel {
    opacity: 0.85;
  }

  .go {
    position: relative;
    min-height: 150px;
    border-radius: 24px;
    border: 2px solid var(--accent);
    background: radial-gradient(circle at 50% 40%, color-mix(in srgb, var(--accent) 35%, transparent), var(--accent-soft));
    color: var(--accent);
    font: inherit;
    font-size: 64px;
    font-weight: 900;
    letter-spacing: 0.25em;
    text-indent: 0.25em;
    cursor: pointer;
    box-shadow: 0 0 40px var(--accent-glow), inset 0 0 40px var(--accent-soft);
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

  .go .hint {
    position: absolute;
    right: 18px;
    bottom: 12px;
    font-size: 12px;
    font-weight: 600;
    letter-spacing: 0.1em;
    opacity: 0.6;
  }

  .controls {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 8px;
  }

  .controls .btn {
    min-height: 48px;
  }

  kbd {
    padding: 1px 6px;
    border-radius: 5px;
    border: 1px solid var(--border-strong);
    font-family: var(--mono);
    font-size: 11px;
    color: var(--text-dim);
  }

  @media (max-width: 900px) {
    .show {
      height: auto;
      grid-template-columns: minmax(0, 1fr);
    }

    .stage {
      order: -1;
    }

    .list {
      max-height: none;
    }
  }

  /* phone as a remote: now, next, GO and Back – nothing else to hit by accident */
  @media (max-width: 600px) {
    .list,
    .key-bar,
    .out-panel,
    .controls .secondary,
    .go .hint,
    kbd {
      display: none;
    }

    .show {
      min-height: 0;
    }

    .info {
      flex: none;
    }

    .big {
      font-size: 20px;
    }

    .go {
      min-height: 38vh;
    }

    .controls {
      grid-template-columns: 1fr;
    }

    .controls .back {
      min-height: 64px;
      font-size: 18px;
    }
  }
</style>
