<script lang="ts">
  import { onMount } from 'svelte';
  import FaderBar from '../components/FaderBar.svelte';
  import KeyBar from '../components/KeyBar.svelte';
  import ScriptText from '../components/ScriptText.svelte';
  import DmxMonitorView from './DmxMonitorView.svelte';
  import Swatches from '../components/Swatches.svelte';
  import { api } from '../lib/api';
  import { modesOf, swatchesOf, type Swatch } from '../lib/color';
  import { compositionIcon, lookMap, sceneStartValues } from '../lib/compositions';
  import { live } from '../lib/live.svelte';
  import { fadeInProgress as fadeInProgressOf, timingLabel } from '../lib/sceneTiming';
  import { parseScript, passageOf, passages, sectionOf, stepPassage } from '../lib/script';
  import { showStack } from '../lib/showStack';
  import { app } from '../lib/state.svelte';
  import { showError } from '../lib/toasts.svelte';
  import { wakeLock } from '../lib/wakeLock.svelte';
  import type { Composition, Fixture, Look, PlaybackStatus, Scene } from '../lib/types';

  const GO_GUARD_MS = 300;

  let scenes = $state<Scene[]>([]);
  let fixtures = $state<Fixture[]>([]);
  let compositions = $state<Composition[]>([]);
  let looks = $state<Look[]>([]);
  let now = $state(performance.now());
  let lastGo = 0;
  let listElement: HTMLElement | undefined = $state();

  const TAB_KEY = 'lumen.showTab';
  type Tab = 'cues' | 'script' | 'dmx';
  /** left side of the show view: the cue list, the script or the live DMX values; GO and the keys stay on the right */
  let tab = $state<Tab>(readTab());

  function readTab(): Tab {
    try {
      const stored = localStorage.getItem(TAB_KEY);
      return stored === 'dmx' || stored === 'script' ? stored : 'cues';
    } catch {
      return 'cues';
    }
  }

  function showTab(next: Tab) {
    tab = next;
    try {
      localStorage.setItem(TAB_KEY, next);
    } catch {
      // only a convenience
    }
  }

  const projectId = $derived(app.active?.id ?? null);
  const status = $derived(live.playback);
  const byId = $derived(new Map(scenes.map((s) => [s.id, s])));
  const modes = $derived(modesOf(fixtures, (id) => app.fixtureType(id)));
  const compositionsById = $derived(new Map(compositions.map((c) => [c.id, c])));
  const swatches = $derived.by(() => {
    const looksById = lookMap(looks);
    return new Map<number, Swatch[]>(scenes.map((s) => [s.id,
      swatchesOf(fixtures, modes, sceneStartValues(s, compositionsById, looksById))]));
  });
  const current = $derived(status?.currentSceneId != null ? byId.get(status.currentSceneId) : undefined);
  const next = $derived(status?.nextSceneId != null ? byId.get(status.nextSceneId) : undefined);
  const resumeScene = $derived(status?.resumeSceneId != null ? byId.get(status.resumeSceneId) : undefined);
  const stack = $derived(showStack(scenes, status));
  const script = $derived(parseScript(current?.script ?? ''));
  const scriptLine = $derived(status?.scriptLine ?? 0);
  /** what ↑/↓ step through: the whole speech of a speaker */
  const scriptPassages = $derived(passages(script));
  const passageIndex = $derived(passageOf(scriptPassages, scriptLine));
  /** the light marker whose part is on stage, null at the start of the scene */
  const marker = $derived((status?.section ?? 0) > 0 ? script.markers[(status?.section ?? 0) - 1] ?? null : null);

  /** Time since the status arrived, frozen while paused. */
  const sinceStatus = $derived(status?.paused ? 0 : Math.max(0, now - live.playbackReceivedAt));
  const elapsed = $derived((status?.fade?.elapsedMs ?? 0) + sinceStatus);
  const fadeInProgress = $derived(
    status?.fade ? fadeInProgressOf(elapsed, status.fade.fadeInMs, status.fade.delayMs) : 1,
  );
  /** The fade-in still waits for the delay of the scene. */
  const delayLeft = $derived(status?.fade ? Math.max(0, status.fade.delayMs - elapsed) : 0);
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
    void live.compositionsRevision;
    void live.looksRevision;
    if (projectId !== null) {
      Promise.all([api.scenes.list(projectId), api.fixtures.list(projectId), api.compositions.list(projectId),
        api.looks.list(projectId)])
        .then(([sceneList, fixtureList, compositionList, lookList]) => {
          scenes = sceneList;
          fixtures = fixtureList;
          compositions = compositionList;
          looks = lookList;
        })
        .catch(showError);
    }
  });

  /**
   * Scrolls the list so the element is in its middle. Unlike scrollIntoView this moves only the list, never the page
   * or the tabs and heading above it.
   */
  function center(selector: string) {
    const element = listElement?.querySelector<HTMLElement>(selector);
    if (!listElement || !element) return;
    const list = listElement.getBoundingClientRect();
    const box = element.getBoundingClientRect();
    const top = listElement.scrollTop + box.top - list.top - (list.height - box.height) / 2;
    listElement.scrollTo({ top: Math.max(0, top), behavior: 'smooth' });
  }

  // keep the current scene in view
  $effect(() => {
    const id = status?.currentSceneId;
    if (id == null || tab !== 'cues') return;
    center(`[data-scene="${id}"]`);
  });

  // keep the current sentence in the middle, however long the text is
  $effect(() => {
    const line = scriptLine;
    if (tab !== 'script' || current === undefined) return;
    center(`[data-line="${line}"]`);
  });

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

  function chooseNext(scene: Scene) {
    apply(api.playback.setNext(scene.id));
  }

  function moveNext(step: number) {
    if (scenes.length === 0) return;
    const index = next ? scenes.indexOf(next) : (status?.position ?? -1);
    const target = scenes[Math.max(0, Math.min(scenes.length - 1, index + step))];
    if (target) chooseNext(target);
  }

  function stepScript(step: number) {
    if (!current) return;
    const line = stepPassage(script, scriptLine, step);
    // passing a light marker fades to the light of its part
    if (line !== null && line !== scriptLine) live.scriptCommand(current.id, line, sectionOf(script, line));
  }

  function onkeydown(event: KeyboardEvent) {
    if ((event.target as HTMLElement).closest('input, textarea, select') || event.metaKey || event.ctrlKey) return;
    const actions: Record<string, () => void> = {
      ' ': go,
      ArrowLeft: () => live.playbackCommand('back'),
      p: () => live.playbackCommand('pause'),
      P: () => live.playbackCommand('pause'),
      Enter: () => live.playbackCommand('finish'),
      ArrowUp: () => (event.shiftKey ? moveNext(-1) : stepScript(-1)),
      ArrowDown: () => (event.shiftKey ? moveNext(1) : stepScript(1)),
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

{#snippet scriptHead()}
  {#if current}
    <div class="script-head">
      <h2><span class="mono">{current.number}</span> {current.name}</h2>
      {#if script.lineCount > 0}
        <span class="dim small mono" title="Speeches">{passageIndex + 1} / {scriptPassages.length}</span>
        <div class="script-steps">
          <button type="button" class="btn" onclick={() => stepScript(-1)} disabled={passageIndex <= 0}
            aria-label="Previous speech">▲ <kbd>↑</kbd></button>
          <button type="button" class="btn" onclick={() => stepScript(1)}
            disabled={passageIndex >= scriptPassages.length - 1} aria-label="Next speech">▼ <kbd>↓</kbd></button>
        </div>
      {/if}
    </div>
  {/if}
{/snippet}

{#snippet scriptView()}
  {#if !current}
    <p class="dim pad">
      {atEnd ? 'End of show.' : 'The stage is dark – the script of a scene appears here as soon as it is on stage.'}
    </p>
  {:else}
    {#if script.parts.length === 0}
      <p class="dim pad">This scene has no script. Add it in setup mode under Scenes.</p>
    {:else}
      <div class="script"><ScriptText {script} currentLine={scriptLine} section={status?.section ?? 0} /></div>
      <p class="script-end dim small">
        {#if next}Next: GO <span class="mono">{next.number}</span> {next.name}
        {:else if status?.nextIsEnd}Next: end of show{/if}
        · <kbd>Shift</kbd>+<kbd>↑</kbd>/<kbd>↓</kbd> choose the next scene
      </p>
    {/if}
  {/if}
{/snippet}

<section class="show">
  <div class="list glass">
    <header class="list-head">
      <div class="tabs" role="tablist" aria-label="Show view">
        <button type="button" role="tab" aria-selected={tab === 'cues'} class:active={tab === 'cues'}
          onclick={() => showTab('cues')}>Scenes</button>
        <button type="button" role="tab" aria-selected={tab === 'script'} class:active={tab === 'script'}
          onclick={() => showTab('script')}>Script</button>
        <button type="button" role="tab" aria-selected={tab === 'dmx'} class:active={tab === 'dmx'}
          onclick={() => showTab('dmx')}>DMX</button>
      </div>
      {#if wakeLock.state === 'needs-tap'}
        <span class="badge warning" title="The browser only keeps the screen on after a tap or key press">
          Tap to keep the screen on
        </span>
      {/if}
      <span class="dim small">{app.active?.name}</span>
    </header>
    {#if tab === 'script'}{@render scriptHead()}{/if}
    <div class="list-body" bind:this={listElement}>
    {#if tab === 'dmx'}
      <div class="dmx"><DmxMonitorView embedded /></div>
    {:else if tab === 'script'}
      {@render scriptView()}
    {:else}
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
        {#if scene.compositions.length > 1}
          {@const used = scene.compositions.flatMap((use) => compositionsById.get(use.compositionId) ?? [])}
          <span class="badge" title={used.map((c) => c.name).join(', ')}>
            {used.map((c) => compositionIcon(c)).join(' ')}
          </span>
        {/if}
        {#if timingLabel(scene)}<span class="badge" title="Own fade times">⏱ {timingLabel(scene)}</span>{/if}
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
    {/if}
    </div>
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
    <ol class="progress" aria-label="Show progress">
      <li class="step previous" class:empty={!stack.previous}>
        {#if stack.previous}
          {@const fadingOut = status?.fade && fadeOutProgress < 1 && status.fade.fromSceneId === stack.previous.id}
          <article class="panel glass small-panel">
            <h3>{fadingOut ? 'Fading out' : 'Before'}</h3>
            <div class="medium"><span class="mono">{stack.previous.number}</span> {stack.previous.name}</div>
            {#if fadingOut && status?.fade}
              <div class="bar out"><span style="width: {(1 - fadeOutProgress) * 100}%"></span></div>
              <div class="dim small mono">{seconds(Math.max(0, status.fade.fadeOutMs - elapsed))} left</div>
            {/if}
          </article>
        {:else}
          <div class="placeholder dim small">{atEnd || current ? '' : 'Start of the show'}</div>
        {/if}
      </li>

      <li class="step now">
        <article class="panel glass now-panel">
          <h3>Now {#if status?.paused}<span class="badge warning">Paused</span>{/if}</h3>
          {#if current}
            <div class="big"><span class="mono">{current.number}</span> {current.name}</div>
            {#if marker}
              <div class="now-marker" title={marker.info}>💡 {marker.name}{#if marker.info}{' '}<span class="dim">– {marker.info}</span>{/if}</div>
            {/if}
          {:else if atEnd}
            <div class="big dim">End of show</div>
          {:else}
            <div class="big dim">Ready – stage is dark</div>
          {/if}
          <!-- always rendered, so the panel keeps its height when a fade ends -->
          {#if status?.fade && fadeInProgress < 1}
            <div class="bar"><span style="width: {fadeInProgress * 100}%"></span></div>
            {#if delayLeft > 0}
              <div class="dim small mono">Fade in starts in {seconds(delayLeft)}</div>
            {:else}
              <div class="dim small mono">
                Fade in · {seconds(Math.max(0, status.fade.delayMs + status.fade.fadeInMs - elapsed))} left
              </div>
            {/if}
          {:else if autoFollowLeft !== null}
            <div class="bar follow"><span style="width: {(1 - autoFollowLeft / (current?.autoFollowMs ?? 1)) * 100}%"></span></div>
            <div class="small mono follow-text">Continues automatically in {seconds(autoFollowLeft)}</div>
          {:else}
            <div class="bar idle"></div>
            <div class="dim small mono">{status?.paused ? 'Paused' : 'Stays until GO'}</div>
          {/if}
        </article>
      </li>

      <li class="step next">
        <article class="panel glass next-panel">
          <h3>Next</h3>
          {#if stack.next?.kind === 'scene'}
            <div class="big"><span class="mono">{stack.next.scene.number}</span> {stack.next.scene.name}</div>
            {#if stack.next.scene.cueNote}<div class="cue">{stack.next.scene.cueNote}</div>{/if}
          {:else if stack.next?.kind === 'end'}
            <div class="big">End – fade to black</div>
          {:else}
            <div class="big dim">—</div>
          {/if}
        </article>
      </li>

      {#if stack.afterNext}
        <li class="step after">
          <article class="panel glass small-panel">
            <h3>Then</h3>
            {#if stack.afterNext.kind === 'scene'}
              <div class="medium"><span class="mono">{stack.afterNext.scene.number}</span> {stack.afterNext.scene.name}</div>
            {:else}
              <div class="medium dim">End – fade to black</div>
            {/if}
          </article>
        </li>
      {/if}

      {#if stack.more > 0}
        <li class="step more">
          <div class="more-cards" aria-hidden="true"><span></span><span></span></div>
          <span class="dim small">+ {stack.more} more {stack.more === 1 ? 'scene' : 'scenes'}</span>
        </li>
      {/if}
    </ol>

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
    <div class="fader-bar"><FaderBar faders={status?.faders ?? []} /></div>
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

  /* tabs and the scene heading stay, only the list body below them scrolls */
  .list {
    display: flex;
    flex-direction: column;
    padding: 8px;
    min-height: 0;
    overflow: hidden;
  }

  .list-body {
    display: flex;
    flex-direction: column;
    flex: 1;
    min-height: 0;
    overflow: auto;
  }

  .tabs {
    display: flex;
    gap: 4px;
  }

  .tabs button {
    padding: 6px 12px;
    border: 0;
    border-radius: var(--radius-sm);
    background: transparent;
    color: var(--text-dim);
    font: inherit;
    font-size: 12px;
    font-weight: 700;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    cursor: pointer;
  }

  .tabs button.active {
    color: var(--accent);
    background: var(--accent-soft);
    box-shadow: inset 0 -2px 0 var(--accent);
  }

  .dmx {
    padding: 0 6px 6px;
  }

  .script-head {
    display: flex;
    align-items: center;
    gap: 12px;
    margin: 0 -8px;
    padding: 10px 18px;
    background: var(--panel-solid);
    border-bottom: 1px solid var(--border);
  }

  .script-head h2 {
    flex: 1;
    min-width: 0;
    margin: 0;
    font-size: 18px;
  }

  .script-head h2 .mono {
    color: var(--accent);
  }

  .script-steps {
    display: flex;
    gap: 6px;
  }

  .script {
    /* room above (and below the end line), so the first and last sentence can be centered too */
    padding: 30vh 18px 0;
    font-size: 22px;
  }

  .script-end {
    margin: 0;
    padding: 20px 18px 30vh;
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

  .now-marker {
    color: var(--warning);
    font-size: 14px;
    font-weight: 600;
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

  /* the progress of the show as a stack: the scene on stage big, before and after it smaller and indented */
  .progress {
    position: relative;
    display: flex;
    flex-direction: column;
    gap: 10px;
    margin: 0;
    padding: 0 0 0 22px;
    list-style: none;
  }

  /* the rail of the show */
  .progress::before {
    content: '';
    position: absolute;
    left: 7px;
    top: 14px;
    bottom: 14px;
    width: 2px;
    border-radius: 1px;
    background: linear-gradient(to bottom, var(--text-faint), var(--accent) 30%, var(--border-strong) 60%,
      transparent);
  }

  .step {
    position: relative;
  }

  .step::before {
    content: '';
    position: absolute;
    left: -20px;
    top: 18px;
    width: 10px;
    height: 10px;
    border-radius: 50%;
    border: 2px solid var(--border-strong);
    background: var(--bg);
  }

  .step.previous::before {
    border-color: var(--text-faint);
    background: var(--text-faint);
  }

  .step.now::before {
    left: -22px;
    width: 14px;
    height: 14px;
    top: 22px;
    border-color: var(--accent);
    background: var(--accent);
    box-shadow: 0 0 12px var(--accent-glow);
  }

  .step.empty::before,
  .step.more::before {
    display: none;
  }

  .step.previous {
    margin-left: 18px;
    opacity: 0.6;
  }

  .step.next {
    margin-left: 12px;
  }

  .step.after {
    margin-left: 28px;
    opacity: 0.75;
  }

  .step.more {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-left: 44px;
  }

  .small-panel {
    gap: 6px;
    padding: 10px 14px;
  }

  .small-panel h3 {
    font-size: 11px;
  }

  .now-panel {
    border-color: var(--accent);
    box-shadow: 0 0 30px var(--accent-soft);
  }

  .placeholder {
    min-height: 20px;
    padding: 0 4px;
  }

  /* more scenes waiting: the edges of a pile of cards */
  .more-cards {
    position: relative;
    width: 34px;
    height: 18px;
  }

  .more-cards span {
    position: absolute;
    inset: 0;
    border: 1px solid var(--border-strong);
    border-radius: 6px;
    background: var(--panel);
  }

  .more-cards span:last-child {
    transform: translate(5px, 5px);
    opacity: 0.6;
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

    /* below status and GO: a fixed height, so the list scrolls inside instead of the page */
    .list {
      height: 70vh;
      min-height: 400px;
    }
  }

  /* phone as a remote: now, next, GO and Back – nothing else to hit by accident */
  @media (max-width: 600px) {
    .list,
    .key-bar,
    .fader-bar,
    .step.previous,
    .step.after,
    .step.more,
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
