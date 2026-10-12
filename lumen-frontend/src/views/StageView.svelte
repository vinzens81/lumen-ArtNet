<script lang="ts">
  import { api } from '../lib/api';
  import { headPreviewsOf, modesOf, previewOf, rgbToCss } from '../lib/color';
  import ClearAllButton from '../components/ClearAllButton.svelte';
  import ProgrammerPanel from '../components/ProgrammerPanel.svelte';
  import { live } from '../lib/live.svelte';
  import { programmerEntries } from '../lib/programmer';
  import {
    beamOffset, clampLength, DEFAULT_ASPECT, DEFAULT_SHAPE, drawnShape, freeSpots, rotate, SHAPE_KINDS, toStage,
    type DrawnShape,
  } from '../lib/stage';
  import { app } from '../lib/state.svelte';
  import { notify, showError } from '../lib/toasts.svelte';
  import type { Fixture, Position, StageShape } from '../lib/types';

  interface Glow {
    color: string;
    intensity: number;
  }

  /** what is drawn for a fixture: its form and, for bars and strips, one lit segment per head */
  interface Look {
    shape: DrawnShape;
    rotation: number;
    length: number;
    segments: Glow[];
  }

  /**
   * embedded in the show view or the view-only page: only looking – no tray, no dragging, no shapes, no programmer,
   * no floor plan buttons, and no keyboard shortcuts that would change the stage. `reservedHeight` is the height of
   * the page around the stage, so the stage fits the window.
   */
  let { embedded = false, reservedHeight = embedded ? 280 : 310 }: { embedded?: boolean; reservedHeight?: number } =
    $props();

  let fixtures = $state<Fixture[]>([]);
  let planVersion = $state(0);
  let hasPlan = $state(false);
  let aspect = $state(DEFAULT_ASPECT);
  let stageElement: HTMLElement | undefined = $state();
  let fileInput: HTMLInputElement | undefined = $state();
  let selectedId = $state<number | null>(null);

  /** fixture being dragged: where it would land, null while outside the stage */
  let drag = $state<{ id: number; at: Position | null; x: number; y: number } | null>(null);

  const projectId = $derived(app.active?.id ?? null);
  const modes = $derived(modesOf(fixtures, (id) => app.fixtureType(id)));
  const placed = $derived(fixtures.filter((f) => f.position !== null));
  const unplaced = $derived(fixtures.filter((f) => f.position === null));
  const masterLevel = $derived(live.master.blackout ? 0 : live.master.level);
  const selected = $derived(placed.find((f) => f.id === selectedId) ?? null);
  /** the programmer controls of the selected fixture, as in the Programmer tab */
  const entries = $derived(programmerEntries(fixtures, (id) => app.fixtureType(id)));
  let controlsOpen = $state(true);
  // selecting another fixture opens the controls again
  $effect(() => {
    void selectedId;
    controlsOpen = true;
  });

  $effect(() => {
    void live.patchRevision;
    if (projectId !== null) load(projectId);
  });

  // live output only while the stage is visible
  $effect(() => live.watch('output'));

  async function load(id: number) {
    try {
      fixtures = await api.fixtures.list(id);
      planVersion++;
    } catch (e) {
      showError(e);
    }
  }

  function label(fixture: Fixture): string {
    return fixture.shortName ?? fixture.name;
  }

  function glow(fixture: Fixture): { color: string; intensity: number; movable: boolean } {
    const mode = modes.get(fixture.id);
    if (!mode) return { color: 'rgb(255 255 255)', intensity: 0, movable: false };
    const preview = previewOf(mode, live.output[fixture.id] ?? {}, masterLevel);
    const movable = mode.channels.some((c) => c.function === 'pan' || c.function === 'tilt');
    return { color: rgbToCss(preview.color), intensity: preview.intensity, movable };
  }

  function shapeOf(fixture: Fixture): StageShape {
    return fixture.stageShape ?? DEFAULT_SHAPE;
  }

  function look(fixture: Fixture, g: Glow): Look {
    const mode = modes.get(fixture.id);
    const own = shapeOf(fixture);
    const shape = drawnShape(own.kind, mode, app.fixtureType(fixture.fixtureTypeId)?.category);
    const heads = shape !== 'spot' && mode ? headPreviewsOf(mode, live.output[fixture.id] ?? {}, masterLevel) : [];
    const segments = heads.length > 0
      ? heads.map((p) => ({ color: rgbToCss(p.color), intensity: p.intensity }))
      : [{ color: g.color, intensity: g.intensity }];
    return { shape, rotation: own.rotation, length: own.length, segments };
  }

  /** soft light along a bar or strip, following the colors of its segments */
  function linePool(segments: Glow[]): string {
    const stops = segments.map((s) => `color-mix(in srgb, ${s.color} ${Math.round(s.intensity * 70)}%, transparent)`);
    return `linear-gradient(90deg, ${(stops.length > 1 ? stops : [stops[0], stops[0]]).join(', ')})`;
  }

  /** how far the label sits below a turned bar or strip, as a share of the stage width */
  function labelDrop(l: Look): number {
    return Math.abs(Math.sin((l.rotation * Math.PI) / 180)) * l.length / 2;
  }

  /** Changes the shape on screen at once; saves it unless the change is still in progress (slider). */
  function reshape(fixture: Fixture, change: Partial<StageShape>, save = true) {
    const shape = { ...shapeOf(fixture), ...change };
    fixtures = fixtures.map((f) => (f.id === fixture.id ? { ...f, stageShape: shape } : f));
    if (save) saveShape(fixture.id, shape);
  }

  async function saveShape(fixtureId: number, shape: StageShape) {
    if (projectId === null) return;
    try {
      await api.stage.shape(projectId, fixtureId, shape);
    } catch (e) {
      showError(e);
      await load(projectId);
    }
  }

  function sliderValue(event: Event): number {
    return Number((event.currentTarget as HTMLInputElement).value);
  }

  async function place(positions: Record<number, Position | null>) {
    if (projectId === null) return;
    // move at once on screen, the server confirms via the patch event
    fixtures = fixtures.map((f) => (f.id in positions ? { ...f, position: positions[f.id] } : f));
    try {
      await api.stage.place(projectId, positions);
    } catch (e) {
      showError(e);
      await load(projectId);
    }
  }

  function startDrag(event: PointerEvent, fixture: Fixture) {
    if (embedded || event.button !== 0) return;
    event.preventDefault();
    selectedId = fixture.id;
    (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
    drag = { id: fixture.id, at: fixture.position, x: event.clientX, y: event.clientY };
  }

  function moveDrag(event: PointerEvent) {
    if (!drag || !stageElement) return;
    const rect = stageElement.getBoundingClientRect();
    const inside = event.clientX >= rect.left && event.clientX <= rect.right
      && event.clientY >= rect.top && event.clientY <= rect.bottom;
    drag = { ...drag, at: inside ? toStage(event.clientX, event.clientY, rect) : null, x: event.clientX, y: event.clientY };
  }

  function endDrag() {
    if (!drag) return;
    const { id, at } = drag;
    drag = null;
    const fixture = fixtures.find((f) => f.id === id);
    if (!fixture) return;
    const unchanged = at === null ? fixture.position === null
      : fixture.position !== null && fixture.position.x === at.x && fixture.position.y === at.y;
    if (!unchanged) place({ [id]: at });
  }

  function placeRemaining() {
    const spots = freeSpots(unplaced.length, placed.map((f) => f.position!));
    place(Object.fromEntries(unplaced.map((f, i) => [f.id, spots[i]])));
  }

  function removeSelected() {
    if (selectedId !== null) place({ [selectedId]: null });
    selectedId = null;
  }

  function onkeydown(event: KeyboardEvent) {
    if (embedded) return;
    if ((event.target as HTMLElement).closest('input, textarea, select, .programmer-overlay')) return;
    if ((event.key === 'Delete' || event.key === 'Backspace') && selectedId !== null) {
      event.preventDefault();
      removeSelected();
    } else if ((event.key === 'r' || event.key === 'R') && selected && !event.metaKey && !event.ctrlKey) {
      event.preventDefault();
      reshape(selected, { rotation: rotate(shapeOf(selected).rotation, event.shiftKey ? -15 : 15) });
    }
  }

  async function uploadPlan(event: Event) {
    const input = event.currentTarget as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file || projectId === null) return;
    try {
      await api.stage.uploadPlan(projectId, file);
      planVersion++;
      notify('Floor plan uploaded');
    } catch (e) {
      showError(e);
    }
  }

  async function removePlan() {
    if (projectId === null) return;
    try {
      await api.stage.removePlan(projectId);
      planVersion++;
    } catch (e) {
      showError(e);
    }
  }

  function planLoaded(event: Event) {
    const image = event.currentTarget as HTMLImageElement;
    hasPlan = true;
    if (image.naturalWidth > 0 && image.naturalHeight > 0) aspect = image.naturalWidth / image.naturalHeight;
  }

  function planMissing() {
    hasPlan = false;
    aspect = DEFAULT_ASPECT;
  }

  function positionOf(fixture: Fixture): Position | null {
    return drag?.id === fixture.id ? drag.at : fixture.position;
  }
</script>

<svelte:window {onkeydown} />

<section class="view" class:embedded>
  {#if !embedded}
  <header class="head">
    <div>
      <h1>Stage</h1>
    </div>
    <div class="actions">
      <ClearAllButton />
      <button class="btn ghost" type="button" onclick={() => fileInput?.click()} disabled={projectId === null}>
        {hasPlan ? 'Replace floor plan…' : 'Floor plan…'}
      </button>
      {#if hasPlan}
        <button class="btn ghost danger" type="button" onclick={removePlan}>Remove plan</button>
      {/if}
      <input class="hidden" type="file" accept="image/png,image/jpeg,image/webp" bind:this={fileInput}
        onchange={uploadPlan} aria-label="Floor plan image" />
    </div>
  </header>
  {/if}

  {#if !app.active}
    <div class="glass empty">Activate a project to arrange its fixtures.</div>
  {:else}
    <div class="layout">
      {#if !embedded}
      <aside class="glass tray">
        <header class="tray-head">
          <h3>Not on stage</h3>
          <button class="btn small" type="button" onclick={placeRemaining} disabled={unplaced.length === 0}>
            Place all
          </button>
        </header>
        {#if unplaced.length === 0}
          <p class="dim small">All fixtures are on stage. Drag one out of the stage to take it off.</p>
        {/if}
        <ul>
          {#each unplaced as fixture (fixture.id)}
            {@const g = glow(fixture)}
            <li>
              <button type="button" class="tray-item" class:dragging={drag?.id === fixture.id}
                style="--glow: {g.color}; --intensity: {g.intensity}"
                onpointerdown={(e) => startDrag(e, fixture)} onpointermove={moveDrag} onpointerup={endDrag}
                onpointercancel={() => (drag = null)} title="Drag onto the stage">
                <span class="dot"></span>
                <span class="name">{fixture.name}</span>
                <span class="mono dim small">{fixture.address}</span>
              </button>
            </li>
          {/each}
        </ul>
        {#if selected}
          {@const own = shapeOf(selected)}
          {@const l = look(selected, glow(selected))}
          <section class="shape-panel" aria-label="Shape of {selected.name}">
            <h3>{selected.name}</h3>
            <div class="kinds" role="group" aria-label="Shape">
              {#each SHAPE_KINDS as option (option.kind)}
                <button type="button" class="kind" class:active={own.kind === option.kind}
                  aria-pressed={own.kind === option.kind} onclick={() => reshape(selected, { kind: option.kind })}>
                  <span class="kind-icon {option.kind === 'auto' ? l.shape : option.kind}" aria-hidden="true"></span>
                  {option.label}
                </button>
              {/each}
            </div>
            {#if own.kind === 'auto'}
              <p class="dim small">Drawn as {l.shape} – from the fixture type.</p>
            {/if}
            <label class="slider">
              <span>Rotation <span class="mono dim">{own.rotation}°</span></span>
              <input type="range" min="0" max="355" step="5" value={own.rotation}
                oninput={(e) => reshape(selected, { rotation: sliderValue(e) }, false)}
                onchange={(e) => reshape(selected, { rotation: sliderValue(e) })} />
            </label>
            {#if l.shape !== 'spot'}
              <label class="slider">
                <span>Length <span class="mono dim">{Math.round(own.length * 100)} % of the stage</span></span>
                <input type="range" min="2" max="100" step="1" value={Math.round(own.length * 100)}
                  oninput={(e) => reshape(selected, { length: clampLength(sliderValue(e) / 100) }, false)}
                  onchange={(e) => reshape(selected, { length: clampLength(sliderValue(e) / 100) })} />
              </label>
            {/if}
            <p class="dim small">R turns by 15°, Shift+R back.</p>
            <button class="btn small ghost" type="button" onclick={removeSelected}>Take off stage</button>
          </section>
        {/if}
      </aside>
      {/if}

      <div class="stage-wrap glass">
        <div class="stage" class:plan={hasPlan} style="aspect-ratio: {aspect}; width: min(100%, calc((100dvh - {reservedHeight}px) * {aspect}))" bind:this={stageElement}
          role="presentation" onpointerdown={(e) => { if (e.target === stageElement) selectedId = null; }}>
          {#if projectId !== null}
            <img class="plan-image" class:hidden={!hasPlan} src={api.stage.planUrl(projectId, planVersion)} alt=""
              onload={planLoaded} onerror={planMissing} draggable="false" />
          {/if}
          <span class="edge upstage">Upstage</span>
          <span class="edge downstage">Audience</span>

          {#each fixtures as fixture (fixture.id)}
            {@const at = positionOf(fixture)}
            {#if at}
              {@const g = glow(fixture)}
              {@const l = look(fixture, g)}
              {#if l.shape === 'spot'}
                {@const beam = beamOffset(live.output[fixture.id], g.movable)}
                <span class="pool" aria-hidden="true"
                  style="left: {(at.x + beam.x) * 100}%; top: {(at.y + beam.y) * 100}%; --glow: {g.color}; --intensity: {g.intensity}"></span>
                <button type="button" class="fixture" class:selected={selectedId === fixture.id}
                  class:dragging={drag?.id === fixture.id}
                  style="left: {at.x * 100}%; top: {at.y * 100}%; --glow: {g.color}; --intensity: {g.intensity}"
                  onpointerdown={(e) => startDrag(e, fixture)} onpointermove={moveDrag} onpointerup={endDrag}
                  onpointercancel={() => (drag = null)}
                  title="{fixture.name} · {Math.round(g.intensity * 100)} %">
                  <span class="lens"></span>
                  <span class="label">{label(fixture)}</span>
                </button>
              {:else}
                <span class="line-pool" aria-hidden="true"
                  style="left: {at.x * 100}%; top: {at.y * 100}%; width: {l.length * 110}%; rotate: {l.rotation}deg; background: {linePool(l.segments)}"></span>
                <button type="button" class="line {l.shape}" class:selected={selectedId === fixture.id}
                  class:dragging={drag?.id === fixture.id}
                  style="left: {at.x * 100}%; top: {at.y * 100}%; width: {l.length * 100}%; rotate: {l.rotation}deg"
                  onpointerdown={(e) => startDrag(e, fixture)} onpointermove={moveDrag} onpointerup={endDrag}
                  onpointercancel={() => (drag = null)}
                  title="{fixture.name} · {Math.round(g.intensity * 100)} %">
                  <span class="body">
                    {#each l.segments as segment, i (i)}
                      <span class="segment" style="--glow: {segment.color}; --intensity: {segment.intensity}"></span>
                    {/each}
                  </span>
                </button>
                <span class="label line-label" aria-hidden="true"
                  style="left: {at.x * 100}%; top: {at.y * 100}%; --drop: {labelDrop(l) * 100}cqw">{label(fixture)}</span>
              {/if}
            {/if}
          {/each}
        </div>
        {#if selected && !embedded}
          {#if controlsOpen}
            <div class="programmer-overlay glass" aria-label="Programmer for {selected.name}">
              <ProgrammerPanel {projectId} {entries} selection={[selected.id]} compact
                onclose={() => (controlsOpen = false)} />
            </div>
          {:else}
            <button class="btn small overlay-open" type="button" onclick={() => (controlsOpen = true)}
              title="Show the programmer controls of {selected.name}">🎛 Controls</button>
          {/if}
        {/if}
      </div>
    </div>
  {/if}

  {#if drag && drag.at === null}
    <!-- dragging outside the stage: drop to take the fixture off -->
    <div class="drag-ghost" style="left: {drag.x}px; top: {drag.y}px">Off stage</div>
  {/if}

  <p class="dim help">
    Drag fixtures to where they hang. They glow like the light on stage right now. Point at one to see its name.
  </p>
</section>

<style>
  .help {
    margin: 0;
    max-width: 820px;
    font-size: 12px;
  }

  .view {
    display: flex;
    flex-direction: column;
    gap: 16px;
    padding: 12px 8px;
    min-height: 0;
  }

  .head {
    display: flex;
    align-items: flex-end;
    justify-content: space-between;
    gap: 16px;
    flex-wrap: wrap;
  }

  .actions {
    display: flex;
    gap: 8px;
  }

  .hidden {
    display: none;
  }

  .small {
    font-size: 12px;
  }

  .layout {
    display: grid;
    grid-template-columns: 240px minmax(0, 1fr);
    gap: 12px;
    align-items: start;
  }

  /* in the show view: just the stage, nothing to grab */
  .view.embedded {
    padding: 0;
  }

  .view.embedded .layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .view.embedded .stage-wrap {
    padding: 0;
    border: 0;
    background: none;
    box-shadow: none;
  }

  .view.embedded :is(.fixture, .line) {
    cursor: default;
  }

  .tray {
    display: flex;
    flex-direction: column;
    gap: 10px;
    padding: 12px;
    max-height: calc(100vh - 230px);
    overflow: auto;
  }

  .tray-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .tray ul {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .tray-item {
    width: 100%;
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px;
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
    background: var(--panel-raised);
    color: var(--text);
    font: inherit;
    text-align: left;
    cursor: grab;
    touch-action: none;
  }

  .tray-item.dragging {
    opacity: 0.4;
  }

  .tray-item .name {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .dot {
    width: 12px;
    height: 12px;
    border-radius: 50%;
    border: 1px solid var(--border-strong);
    background: color-mix(in srgb, var(--glow) calc(var(--intensity) * 100%), transparent);
    box-shadow: 0 0 calc(12px * var(--intensity)) var(--glow);
  }

  /* the programmer controls of the selected fixture, over the corner of the stage */
  .programmer-overlay {
    position: absolute;
    top: 12px;
    right: 12px;
    z-index: 5;
    width: min(360px, calc(100% - 24px));
    max-height: calc(100% - 24px);
    overflow-y: auto;
    padding: 14px;
    background: var(--panel-solid);
    box-shadow: var(--shadow);
  }

  .overlay-open {
    position: absolute;
    top: 12px;
    right: 12px;
    z-index: 5;
  }

  .stage-wrap {
    position: relative;
    padding: 12px;
  }

  .stage {
    position: relative;
    min-width: 280px;
    margin: 0 auto;
    overflow: hidden;
    border-radius: var(--radius-sm);
    border: 1px solid var(--border-strong);
    background:
      linear-gradient(rgba(255, 255, 255, 0.035) 1px, transparent 1px) 0 0 / 5% 10%,
      linear-gradient(90deg, rgba(255, 255, 255, 0.035) 1px, transparent 1px) 0 0 / 5% 10%,
      #07080c;
    touch-action: none;
    user-select: none;
    /* bars and strips are sized in stage widths, also across their height when turned */
    container-type: size;
  }

  .plan-image {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    object-fit: fill;
    opacity: 0.45;
    filter: grayscale(0.6) brightness(0.8);
    pointer-events: none;
  }

  .edge {
    position: absolute;
    left: 50%;
    transform: translateX(-50%);
    font-size: 10px;
    letter-spacing: 0.2em;
    text-transform: uppercase;
    color: var(--text-faint);
    pointer-events: none;
  }

  .upstage {
    top: 6px;
  }

  .downstage {
    bottom: 6px;
  }

  .pool {
    position: absolute;
    width: 18%;
    aspect-ratio: 1;
    transform: translate(-50%, -50%);
    border-radius: 50%;
    background: radial-gradient(circle, color-mix(in srgb, var(--glow) 70%, transparent) 0%, transparent 65%);
    opacity: var(--intensity);
    mix-blend-mode: screen;
    pointer-events: none;
    transition: left 0.1s linear, top 0.1s linear;
  }

  .fixture {
    position: absolute;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
    padding: 0;
    border: 0;
    background: none;
    color: var(--text);
    font: inherit;
    transform: translate(-50%, -14px);
    cursor: grab;
    touch-action: none;
  }

  .fixture.dragging {
    cursor: grabbing;
    z-index: 2;
  }

  .lens {
    width: 28px;
    height: 28px;
    border-radius: 50%;
    border: 2px solid var(--border-strong);
    background: color-mix(in srgb, var(--glow) calc(var(--intensity) * 100%), #111);
    box-shadow:
      0 0 calc(30px * var(--intensity)) var(--glow),
      0 0 calc(60px * var(--intensity)) color-mix(in srgb, var(--glow) 50%, transparent);
  }

  .fixture.selected .lens {
    border-color: var(--accent);
    outline: 3px solid var(--accent-soft);
  }

  .label {
    padding: 1px 6px;
    border-radius: 6px;
    background: rgba(0, 0, 0, 0.6);
    font-size: 11px;
    font-weight: 600;
    white-space: nowrap;
  }

  /* names only on hover, while dragging and for the selected fixture (touch has no hover) */
  .label {
    visibility: hidden;
    opacity: 0;
    transition: opacity 0.12s, visibility 0.12s;
  }

  .fixture:is(:hover, :focus-visible, .selected, .dragging) .label,
  .line:is(:hover, :focus-visible, .selected, .dragging) + .line-label {
    visibility: visible;
    opacity: 1;
  }

  .line-pool {
    position: absolute;
    height: 9%;
    translate: -50% -50%;
    border-radius: 50%;
    filter: blur(14px);
    mix-blend-mode: screen;
    pointer-events: none;
  }

  /* bar or strip: centred on its position and turned around it; the padding makes thin strips easy to grab */
  .line {
    position: absolute;
    display: block;
    padding: 8px 0;
    border: 0;
    background: none;
    translate: -50% -50%;
    cursor: grab;
    touch-action: none;
  }

  .line.dragging {
    cursor: grabbing;
    z-index: 2;
  }

  .line .body {
    display: flex;
    gap: 2px;
    height: 14px;
    padding: 2px;
    border: 1px solid var(--border-strong);
    border-radius: 4px;
    background: #0d0f14;
  }

  .line.strip .body {
    gap: 1px;
    height: 6px;
    padding: 0;
    border-color: var(--border);
    border-radius: 999px;
    overflow: visible;
  }

  .segment {
    flex: 1;
    min-width: 0;
    border-radius: 1px;
    background: color-mix(in srgb, var(--glow) calc(var(--intensity) * 100%), #111);
    box-shadow: 0 0 calc(12px * var(--intensity)) var(--glow);
  }

  .line.strip .segment {
    border-radius: 999px;
  }

  .line.selected .body {
    border-color: var(--accent);
    outline: 3px solid var(--accent-soft);
  }

  .line-label {
    position: absolute;
    translate: -50% calc(var(--drop) + 12px);
    pointer-events: none;
  }

  .shape-panel {
    display: flex;
    flex-direction: column;
    gap: 10px;
    padding-top: 10px;
    border-top: 1px solid var(--border);
  }

  .shape-panel h3 {
    margin: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .shape-panel p {
    margin: 0;
  }

  .kinds {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 4px;
  }

  .kind {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px;
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
    background: var(--panel-raised);
    color: var(--text);
    font: inherit;
    font-size: 13px;
    cursor: pointer;
  }

  .kind.active {
    border-color: var(--accent);
    background: var(--accent-soft);
  }

  .kind-icon {
    flex: none;
    width: 18px;
    height: 18px;
    border: 2px solid currentColor;
    border-radius: 50%;
    opacity: 0.8;
  }

  .kind-icon.bar {
    height: 8px;
    border-radius: 2px;
  }

  .kind-icon.strip {
    height: 0;
    border-width: 2px 0 0;
    border-radius: 0;
  }

  .slider {
    display: flex;
    flex-direction: column;
    gap: 4px;
    font-size: 13px;
  }

  .slider input {
    width: 100%;
    accent-color: var(--accent);
  }

  .drag-ghost {
    position: fixed;
    z-index: 50;
    transform: translate(12px, 12px);
    padding: 4px 10px;
    border-radius: 999px;
    background: var(--panel-solid);
    border: 1px solid var(--danger);
    color: var(--danger);
    font-size: 12px;
    pointer-events: none;
  }

  @media (max-width: 900px) {
    .layout {
      grid-template-columns: minmax(0, 1fr);
    }

    .tray {
      order: 2;
      max-height: none;
    }
  }
</style>
