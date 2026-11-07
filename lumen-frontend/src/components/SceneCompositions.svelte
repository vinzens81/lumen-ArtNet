<script lang="ts">
  import { api } from '../lib/api';
  import { compositionIcon, effectLabel, isFast, lookMap, overlaps, scenesUsing } from '../lib/compositions';
  import { live } from '../lib/live.svelte';
  import { app } from '../lib/state.svelte';
  import { showError } from '../lib/toasts.svelte';
  import { fadeEnded, fadeInProgress, sceneRequestOf, secondsToMs } from '../lib/sceneTiming';
  import { parseScript } from '../lib/script';
  import { stagePreview } from '../lib/stagePreview.svelte';
  import type {
    Composition, Fixture, Group, Look, Mode, Move, Scene, SceneComposition, SceneLook, SceneMove,
  } from '../lib/types';
  import CompositionEditor from './CompositionEditor.svelte';
  import Modal from './Modal.svelte';

  /**
   * The light of a scene or of one part of it (after a light marker of the script): compositions with level, tempo
   * and restart, looks used directly, and moves for where the pan/tilt fixtures point. Each part can be shown on
   * stage on its own, or or faded into from the light on stage. Its fade times: for the start those of the GO into the
   * scene, for a part after a marker its own (empty = the scene's).
   */
  let {
    projectId,
    scene,
    marker = null,
    info = '',
    hasMarkers = false,
    scenes,
    compositions,
    looks,
    moves = [],
    fixtures,
    groups,
    modes,
  }: {
    projectId: number;
    scene: Scene;
    scenes: Scene[];
    compositions: Composition[];
    looks: Look[];
    moves?: Move[];
    fixtures: Fixture[];
    groups: Group[];
    modes: Map<number, Mode>;
    /** the light marker of the part; null = the start of the scene */
    marker?: string | null;
    /** the text in brackets of the marker */
    info?: string;
    /** the scene has light markers: this part is its start */
    hasMarkers?: boolean;
  } = $props();

  let uses = $state<SceneComposition[]>([]);
  let lookUses = $state<SceneLook[]>([]);
  let moveUses = $state<SceneMove[]>([]);
  let addMoveId = $state('');
  let addId = $state('');
  let addLookId = $state('');
  let editing = $state<{ id: number; generate: boolean } | null>(null);

  const byId = $derived(new Map(compositions.map((c) => [c.id, c])));
  const editingComposition = $derived(editing ? byId.get(editing.id) ?? null : null);
  const available = $derived(compositions.filter((c) => !uses.some((u) => u.compositionId === c.id)));
  const warnings = $derived(overlaps(uses.flatMap((u) => byId.get(u.compositionId) ?? []), lookMap(looks), fixtures));
  const programmerHasValues = $derived(Object.keys(live.programmer).length > 0);

  const looksById = $derived(lookMap(looks));
  /** looks a scene can use: the shared ones (a composition's own looks stay in their composition) */
  const availableLooks = $derived(looks.filter((l) => l.owner === null && !lookUses.some((u) => u.lookId === l.id)));
  const section = $derived(marker === null ? scene : scene.sections.find((s) => s.marker === marker)
    ?? { compositions: [], looks: [], moves: [], fadeInMs: null, fadeOutMs: null });
  const movesById = $derived(new Map(moves.map((m) => [m.id, m])));
  const availableMoves = $derived(moves.filter((m) => !moveUses.some((u) => u.moveId === m.id)));

  // the uses of the scene or part; slider moves change them here first and are saved when let go
  $effect(() => {
    uses = section.compositions.map((u) => ({ ...u }));
    lookUses = section.looks.map((u) => ({ ...u }));
    moveUses = section.moves.map((u) => ({ ...u }));
  });

  async function save(next: SceneComposition[], nextLooks: SceneLook[] = lookUses, nextMoves: SceneMove[] = moveUses) {
    uses = next;
    lookUses = nextLooks;
    moveUses = nextMoves;
    try {
      await api.scenes.setSection(projectId, scene.id, { marker, compositions: next, looks: nextLooks,
        moves: nextMoves, fadeInMs: section.fadeInMs, fadeOutMs: section.fadeOutMs });
    } catch (e) {
      showError(e);
    }
  }

  /** where empty fade fields of the frame come from: the project for the start, the scene for a part */
  const fadeFallback = $derived(marker === null
    ? { fadeIn: app.active?.fadeInMs ?? null, fadeOut: app.active?.fadeOutMs ?? null }
    : { fadeIn: scene.fadeInMs ?? app.active?.fadeInMs ?? null, fadeOut: scene.fadeOutMs ?? app.active?.fadeOutMs ?? null });

  /** The fade times of the frame: of the GO into the scene for its start, of the part after a marker. */
  async function saveFades(patch: { fadeInMs?: number | null; fadeOutMs?: number | null }) {
    try {
      if (marker === null) {
        await api.scenes.update(projectId, scene.id, { ...sceneRequestOf(scene), ...patch });
      } else {
        await api.scenes.setSection(projectId, scene.id, { marker, compositions: uses, looks: lookUses,
          moves: moveUses, fadeInMs: section.fadeInMs, fadeOutMs: section.fadeOutMs, ...patch });
      }
    } catch (e) {
      showError(e);
    }
  }

  function changeLook(index: number, patch: Partial<SceneLook>) {
    save(uses, lookUses.map((u, i) => (i === index ? { ...u, ...patch } : u)));
  }

  function addLook() {
    const id = Number(addLookId);
    addLookId = '';
    if (id) save(uses, [...lookUses, { lookId: id, level: 1 }]);
  }

  function addMove() {
    const id = Number(addMoveId);
    addMoveId = '';
    if (id) save(uses, lookUses, [...moveUses, { moveId: id }]);
  }

  function reorderMove(index: number, step: number) {
    const next = [...moveUses];
    const other = index + step;
    if (other < 0 || other >= next.length) return;
    [next[index], next[other]] = [next[other], next[index]];
    save(uses, lookUses, next);
  }

  function moveDetail(move: Move): string {
    const heads = `${move.fixtures.length} ${move.fixtures.length === 1 ? 'head' : 'heads'}`;
    return [heads, scenesUsingMove(move.id).length > 1 ? 'also in other scenes' : null].filter(Boolean).join(' · ');
  }

  function scenesUsingMove(moveId: number): Scene[] {
    return scenes.filter((s) => s.moves.some((u) => u.moveId === moveId)
      || s.sections.some((part) => part.moves.some((u) => u.moveId === moveId)));
  }

  /** the scene's own fade-in as a hint in empty fields */
  const sceneFadeIn = $derived(scene.fadeInMs !== null ? String(scene.fadeInMs / 1000) : 'scene');

  function msToSeconds(ms: number | null): number | null {
    return ms === null ? null : ms / 1000;
  }

  function change(index: number, patch: Partial<SceneComposition>) {
    save(uses.map((u, i) => (i === index ? { ...u, ...patch } : u)));
  }

  function move(index: number, step: number) {
    const next = [...uses];
    const other = index + step;
    if (other < 0 || other >= next.length) return;
    [next[index], next[other]] = [next[other], next[index]];
    save(next);
  }

  function add() {
    const id = Number(addId);
    addId = '';
    if (id) save([...uses, { compositionId: id, level: 1, speedFactor: 1, restart: false, fadeInMs: null,
      fadeOutMs: null }]);
  }

  /** A new composition for this scene: the programmer as its first step, or an effect to generate. */
  async function create(kind: 'programmer' | 'effect') {
    const count = uses.length + 1;
    try {
      let created = await api.compositions.create(projectId, {
        name: `${scene.name}${marker ? ` – ${marker}` : ''} – ${kind === 'effect' ? 'effect' : count}`,
      });
      if (kind === 'programmer') created = await api.compositions.recordStep(projectId, created.id);
      await save([...uses, { compositionId: created.id, level: 1, speedFactor: 1, restart: false,
        fadeInMs: null, fadeOutMs: null }]);
      if (kind === 'effect') editing = { id: created.id, generate: true };
    } catch (e) {
      showError(e);
    }
  }

  function detail(composition: Composition): string {
    const steps = `${composition.steps.length} ${composition.steps.length === 1 ? 'step' : 'steps'}`;
    const others = scenesUsing(composition.id, scenes).filter((s) => s.id !== scene.id).length;
    return [
      composition.generator ? effectLabel(composition.generator.kind) : steps,
      composition.intensityMode === 'relative' ? 'relative' : null,
      others > 0 ? `also in ${others} ${others === 1 ? 'scene' : 'scenes'}` : null,
    ].filter(Boolean).join(' · ');
  }

  // --- on stage: this part alone, or the fade into it -----------------------------------------------------------

  /** markers of the saved script in their order – the server plays what is saved */
  const savedMarkers = $derived(parseScript(scene.script).markers.map((m) => m.name));
  /** the part exists on the server: the start always, a marker once the script with it is saved */
  const saved = $derived(marker === null || savedMarkers.includes(marker));
  const onStage = $derived(stagePreview.status.sceneId === scene.id && stagePreview.status.marker === marker);
  // progress of a fade into this light, interpolated from the moment the status arrived
  let now = $state(performance.now());
  const stageFade = $derived(onStage ? stagePreview.status.fade : null);
  const stageFadeElapsed = $derived(stageFade ? stageFade.elapsedMs + (now - stagePreview.receivedAt) : 0);
  const stageFadeRunning = $derived(stageFade !== null && !fadeEnded(stageFadeElapsed, stageFade));

  $effect(() => {
    if (stageFade === null) return;
    let frame = requestAnimationFrame(function tick() {
      now = performance.now();
      if (!fadeEnded(stageFadeElapsed, stageFade)) frame = requestAnimationFrame(tick);
    });
    return () => cancelAnimationFrame(frame);
  });

  function sliderValue(event: Event): number {
    return Number((event.currentTarget as HTMLInputElement).value);
  }
</script>

<section class="compositions" class:part={marker !== null}>
  {#if marker === null}
    <h3>Compositions{hasMarkers ? ' · Start' : ''}</h3>
    <p class="hint dim">
      Run side by side from GO. Intensity: the highest wins; colors: the later one. A composition that is in the next
      scene too keeps running.
    </p>
  {:else}
    <h3>Compositions · 💡 {marker}</h3>
    <p class="hint dim">
      {#if info}<strong>{info}</strong>{' – '}{/if}Fades in when the script passes the marker. This part stands on its own:
      choose again what should stay on. What is in both parts does not move.
    </p>
  {/if}
  <div class="frame-fades">
    <label title={marker === null
      ? 'How long the GO into this scene fades in. Empty = the project\'s'
      : 'How long this light fades in when the script passes the marker. Empty = the scene\'s'}>
      Fade in
      <input type="number" min="0" max="600" step="0.1" value={msToSeconds(section.fadeInMs)}
        placeholder={fadeFallback.fadeIn === null ? '' : String(fadeFallback.fadeIn / 1000)}
        onchange={(e) => saveFades({ fadeInMs: secondsToMs(e.currentTarget.valueAsNumber) })} /> s
    </label>
    <label title={marker === null
      ? 'How long the light before fades out at the GO into this scene. Empty = the project\'s'
      : 'How long the light before the marker fades out. Empty = the scene\'s'}>
      Fade out
      <input type="number" min="0" max="600" step="0.1" value={msToSeconds(section.fadeOutMs)}
        placeholder={fadeFallback.fadeOut === null ? '' : String(fadeFallback.fadeOut / 1000)}
        onchange={(e) => saveFades({ fadeOutMs: secondsToMs(e.currentTarget.valueAsNumber) })} /> s
    </label>
    <span class="dim small">{marker === null ? 'empty = the project\'s' : 'empty = the scene\'s'}</span>
  </div>

  {#if lookUses.length > 0}
    <ol class="list">
      {#each lookUses as use, i (use.lookId)}
        {@const look = looksById.get(use.lookId)}
        <li>
          <div class="use-head">
            <span class="icon" aria-hidden="true">◆</span>
            <span class="name">{look?.name ?? '(deleted look)'}</span>
            <span class="dim small">look</span>
            <span class="actions">
              <button class="btn small ghost danger" type="button"
                onclick={() => save(uses, lookUses.filter((_, j) => j !== i))} aria-label="Remove look">✕</button>
            </span>
          </div>
          <div class="use">
            <label class="mini">Level <span class="mono">{Math.round(use.level * 100)} %</span>
              <input type="range" min="0" max="1" step="0.01" value={use.level}
                oninput={(e) => (lookUses[i].level = sliderValue(e))}
                onchange={(e) => changeLook(i, { level: sliderValue(e) })} /></label>
          </div>
        </li>
      {/each}
    </ol>
  {/if}

  {#if uses.length > 0}
    <ol class="list">
      {#each uses as use, i (use.compositionId)}
        {@const composition = byId.get(use.compositionId)}
        <li>
          <div class="use-head">
            <span class="icon" aria-hidden="true">{composition ? compositionIcon(composition) : '?'}</span>
            <button type="button" class="link" onclick={() => (editing = { id: use.compositionId, generate: false })}
              title="Edit the composition – it changes in every scene that uses it">
              <span class="name">{composition?.name ?? '(deleted)'}</span>
              {#if composition}<span class="dim small">{detail(composition)}</span>{/if}
            </button>
            {#if composition && isFast(composition, use.speedFactor)}<span class="fast" title="Fast flashing">⚡</span>{/if}
            <span class="actions">
              <button class="btn small ghost" type="button" onclick={() => move(i, -1)} aria-label="Move up">↑</button>
              <button class="btn small ghost" type="button" onclick={() => move(i, 1)} aria-label="Move down">↓</button>
              <button class="btn small ghost danger" type="button" onclick={() => save(uses.filter((_, j) => j !== i))}
                aria-label="Remove from scene">✕</button>
            </span>
          </div>
          <div class="use">
            <label class="mini">Level <span class="mono">{Math.round(use.level * 100)} %</span>
              <input type="range" min="0" max="1" step="0.01" value={use.level}
                oninput={(e) => (uses[i].level = sliderValue(e))} onchange={(e) => change(i, { level: sliderValue(e) })} /></label>
            <label class="mini">Tempo <span class="mono">{use.speedFactor.toFixed(2)}×</span>
              <input type="range" min="0.25" max="4" step="0.05" value={use.speedFactor}
                oninput={(e) => (uses[i].speedFactor = sliderValue(e))}
                onchange={(e) => change(i, { speedFactor: sliderValue(e) })} /></label>
            <label class="check small" title="Start from the beginning at GO, even if it already runs in the scene before">
              <input type="checkbox" checked={use.restart} onchange={(e) => change(i, { restart: e.currentTarget.checked })} />
              Restart at GO
            </label>
            <label class="mini fade" title="Its own fade-in at GO, after the delay of the scene. Empty = the scene's">
              Fade in
              <span><input type="number" min="0" max="600" step="0.1" value={msToSeconds(use.fadeInMs)}
                placeholder={sceneFadeIn} onchange={(e) => change(i, { fadeInMs: secondsToMs(e.currentTarget.valueAsNumber) })} />
                s</span></label>
            <label class="mini fade"
              title="Its own fade-out when it leaves the stage (next GO, a light marker). Empty = the one of that GO">
              Fade out
              <span><input type="number" min="0" max="600" step="0.1" value={msToSeconds(use.fadeOutMs)}
                placeholder="scene" onchange={(e) => change(i, { fadeOutMs: secondsToMs(e.currentTarget.valueAsNumber) })} />
                s</span></label>
          </div>
        </li>
      {/each}
    </ol>
  {:else if lookUses.length === 0}
    <p class="dim small">{marker === null ? 'No compositions – the scene is dark.' : 'Nothing chosen – the stage goes dark at this marker.'}</p>
  {/if}

  {#if moveUses.length > 0}
    <ol class="list">
      {#each moveUses as use, i (use.moveId)}
        {@const move = movesById.get(use.moveId)}
        <li>
          <div class="use-head">
            <span class="icon" aria-hidden="true">↻</span>
            <span class="name grow">{move?.name ?? '(deleted move)'}</span>
            {#if move}<span class="dim small">{moveDetail(move)}</span>{/if}
            <span class="actions">
              <button class="btn small ghost" type="button" onclick={() => reorderMove(i, -1)} aria-label="Move up">↑</button>
              <button class="btn small ghost" type="button" onclick={() => reorderMove(i, 1)} aria-label="Move down">↓</button>
              <button class="btn small ghost danger" type="button"
                onclick={() => save(uses, lookUses, moveUses.filter((_, j) => j !== i))} aria-label="Remove move">✕</button>
            </span>
          </div>
        </li>
      {/each}
    </ol>
  {/if}
  {#each warnings as warning, i (i)}<p class="hint warning-text">{warning}</p>{/each}

  <div class="add">
    <label class="field grow">
      <span class="sr-only">Add a composition</span>
      <select bind:value={addId} onchange={add} disabled={available.length === 0}>
        <option value="">{available.length === 0 ? 'No other compositions yet' : '+ Add a composition…'}</option>
        {#each available as composition (composition.id)}
          <option value={String(composition.id)}>{compositionIcon(composition)} {composition.name}</option>
        {/each}
      </select>
    </label>
    <button class="btn small record-outline" type="button" onclick={() => create('programmer')}
      disabled={!programmerHasValues} title="A new composition with the programmer as its step">⏺ From programmer</button>
    <button class="btn small" type="button" onclick={() => create('effect')}
      title="A new composition generated from a chase, rainbow, flicker, …">✦ Effect</button>
  </div>
  <label class="field">
    <span class="sr-only">Add a look</span>
    <select bind:value={addLookId} onchange={addLook} disabled={availableLooks.length === 0}>
      <option value="">{availableLooks.length === 0 ? 'Shared looks are made in the Looks tab' : '+ Add a look…'}</option>
      {#each availableLooks as look (look.id)}<option value={String(look.id)}>◆ {look.name}</option>{/each}
    </select>
  </label>
  <label class="field">
    <span class="sr-only">Add a move</span>
    <select bind:value={addMoveId} onchange={addMove} disabled={availableMoves.length === 0}
      title="Where the moving heads point: pan and tilt come only from moves. Heads without a move stay where they are.">
      <option value="">{availableMoves.length === 0 ? 'Moves are made in the Moves tab' : '+ Add a move…'}</option>
      {#each availableMoves as move (move.id)}<option value={String(move.id)}>↻ {move.name}</option>{/each}
    </select>
  </label>
  <div class="stage">
    {#if onStage}
      <button class="btn small primary" type="button" onclick={() => stagePreview.stop()}
        title="Stop playing this on stage">■ Stop showing</button>
    {:else}
      <button class="btn small" type="button" disabled={!saved}
        onclick={() => stagePreview.showScene(projectId, scene.id, marker)}
        title={saved ? `Play ${marker === null ? 'the start of the scene' : 'this part'} on stage, above the playback`
          : 'Save the scene first – the marker is not in the saved script yet'}>▶ Show on stage</button>
    {/if}
    <button class="btn small" type="button" disabled={!saved}
      onclick={() => stagePreview.fadeIn(projectId, scene.id, marker)}
      title={saved ? `Fade from the light on stage into ${marker === null ? 'this scene' : 'this part'}, with the fade times of the show`
        : 'Save the scene first – the marker is not in the saved script yet'}>⇢ Fade in</button>
  </div>
  {#if stageFade && stageFadeRunning}
    {@const delayLeft = Math.max(0, stageFade.delayMs - stageFadeElapsed)}
    <div class="stage-fade" role="status">
      <div class="small">
        Fading in from the light on stage
        <span class="mono dim">
          · {delayLeft > 0
            ? `fade in starts in ${(delayLeft / 1000).toFixed(1)} s`
            : `${(Math.max(0, stageFade.delayMs + stageFade.fadeInMs - stageFadeElapsed) / 1000).toFixed(1)} s left`}
        </span>
      </div>
      <div class="bar">
        <span style="width: {fadeInProgress(stageFadeElapsed, stageFade.fadeInMs, stageFade.delayMs) * 100}%"></span>
      </div>
    </div>
  {/if}
  {#if onStage && programmerHasValues}
    <p class="hint warning-text">The programmer is on top of the stage preview – clear it to see the light as it is.</p>
  {/if}
</section>

<Modal wide bind:open={() => editingComposition !== null, (open) => { if (!open) editing = null; }} title="Composition">
  {#if editingComposition && editing}
    {#key editingComposition.id}
      <CompositionEditor {projectId} composition={editingComposition} {looks} {scenes} {fixtures} {groups} {modes}
        startWithGenerator={editing.generate} />
    {/key}
  {/if}
  {#snippet actions()}
    <button class="btn primary" type="button" onclick={() => (editing = null)}>Done</button>
  {/snippet}
</Modal>

<style>
  /* everything that belongs to one part, from its heading to the pickers, in a frame */
  .compositions {
    display: flex;
    flex-direction: column;
    gap: 8px;
    margin-top: 6px;
    padding: 12px 14px 14px;
    border: 1px solid color-mix(in srgb, var(--warning) 40%, transparent);
    border-left: 3px solid var(--warning);
    border-radius: var(--radius-sm);
    background: rgba(255, 255, 255, 0.02);
  }

  /* a part after a light marker */
  .compositions.part h3 {
    color: var(--warning);
  }

  .list {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .list li {
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 6px 8px;
    border-radius: var(--radius-sm);
    background: var(--panel-raised);
  }

  .use-head,
  .use,
  .add {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .use {
    flex-wrap: wrap;
    padding-left: 28px;
  }

  .mini {
    display: flex;
    flex-direction: column;
    gap: 2px;
    width: 130px;
    font-size: 11px;
    color: var(--text-dim);
  }

  .fade {
    width: 80px;
  }

  .fade input {
    width: 56px;
  }

  .icon {
    width: 20px;
    text-align: center;
  }

  .link {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    min-width: 0;
    padding: 0;
    border: 0;
    background: none;
    color: var(--text);
    font: inherit;
    text-align: left;
    cursor: pointer;
  }

  .link:hover .name {
    color: var(--accent);
  }

  .name {
    font-weight: 600;
  }

  .actions {
    display: flex;
    margin-left: auto;
  }

  .fast {
    color: var(--warning);
  }

  .grow {
    flex: 1;
  }

  .hint {
    margin: 0;
    font-size: 12px;
  }

  .warning-text {
    color: var(--warning);
  }

  .frame-fades {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 6px 14px;
    font-size: 13px;
  }

  .frame-fades label {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .frame-fades input {
    width: 64px;
  }

  .stage {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    padding-top: 8px;
    border-top: 1px solid var(--border);
  }

  .stage-fade {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .stage-fade .bar {
    height: 6px;
    border-radius: 3px;
    background: rgba(255, 255, 255, 0.08);
    overflow: hidden;
  }

  .stage-fade .bar span {
    display: block;
    height: 100%;
    background: var(--accent);
  }

  .small {
    font-size: 11px;
  }

  .sr-only {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip: rect(0 0 0 0);
  }
</style>
