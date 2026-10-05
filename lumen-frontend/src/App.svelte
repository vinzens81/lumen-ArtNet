<script lang="ts">
  import { onMount } from 'svelte';
  import MasterBar from './components/MasterBar.svelte';
  import Modal from './components/Modal.svelte';
  import PinPrompt from './components/PinPrompt.svelte';
  import Toasts from './components/Toasts.svelte';
  import { access } from './lib/access.svelte';
  import { outputIndicator } from './lib/health';
  import { live } from './lib/live.svelte';
  import { stagePreview } from './lib/stagePreview.svelte';
  import { isPrintPage, modeOfRoute, navigate, router, type Route } from './lib/router.svelte';
  import { app } from './lib/state.svelte';
  import CompositionsView from './views/CompositionsView.svelte';
  import CuePrintView from './views/CuePrintView.svelte';
  import DmxMonitorView from './views/DmxMonitorView.svelte';
  import PartyButtonsView from './views/PartyButtonsView.svelte';
  import PartyView from './views/PartyView.svelte';
  import PatchView from './views/PatchView.svelte';
  import ProgrammerView from './views/ProgrammerView.svelte';
  import ProjectsView from './views/ProjectsView.svelte';
  import ScenesView from './views/ScenesView.svelte';
  import SettingsView from './views/SettingsView.svelte';
  import ShowView from './views/ShowView.svelte';
  import StageView from './views/StageView.svelte';
  import UniversesView from './views/UniversesView.svelte';
  import type { OperatingMode } from './lib/types';

  const tabs: { route: Route; label: string; soon?: boolean }[] = [
    { route: '/setup/projects', label: 'Projects' },
    { route: '/setup/universes', label: 'Universes' },
    { route: '/setup/patch', label: 'Patch' },
    { route: '/setup/programmer', label: 'Programmer' },
    { route: '/setup/stage', label: 'Stage' },
    { route: '/setup/compositions', label: 'Compositions' },
    { route: '/setup/scenes', label: 'Scenes' },
    { route: '/setup/party', label: 'Party' },
    { route: '/setup/dmx', label: 'DMX' },
    { route: '/setup/settings', label: 'Settings' },
  ];

  /** the mode of the page shown; show and party have their own page without the setup tabs */
  const pageMode = $derived(modeOfRoute(router.route));
  const liveMode = $derived(pageMode !== 'setup');
  const output = $derived(outputIndicator(live.health, live.connected));
  /** the mode to switch to after confirming to leave show or party mode */
  let leaveTarget = $state<OperatingMode | null>(null);
  let leaveOpen = $state(false);

  // the operating mode is server state: every connected client follows it
  $effect(() => {
    if (!live.modeKnown || isPrintPage(router.route)) return;
    if (live.mode === 'show' && router.route !== '/show') navigate('/show');
    if (live.mode === 'party' && router.route !== '/party') navigate('/party');
    if (live.mode === 'setup' && liveMode) navigate('/setup/scenes');
  });

  /** From setup any mode starts at once; leaving show or party mode needs the PIN or a confirmation. */
  function switchTo(target: OperatingMode) {
    if (target === pageMode) return;
    if (pageMode === 'setup') {
      live.setMode(target);
      return;
    }
    leaveTarget = target;
    if (access.locked) access.requestUnlock(confirmLeave);
    else leaveOpen = true;
  }

  async function confirmLeave() {
    leaveOpen = false;
    if (leaveTarget) await live.setMode(leaveTarget);
    leaveTarget = null;
  }

  function toggleLock() {
    if (access.locked) access.requestUnlock();
    else access.lock();
  }

  // PIN set or removed, or all browsers locked (show mode started)
  $effect(() => {
    void live.accessRevision;
    access.load();
  });

  onMount(() => {
    const onLocked = () => {
      access.load();
      if (!access.promptOpen) access.requestUnlock();
    };
    window.addEventListener('lumen:locked', onLocked);
    return () => window.removeEventListener('lumen:locked', onLocked);
  });

  $effect(() => {
    document.documentElement.dataset.mode = pageMode;
  });

  onMount(() => {
    app.load().then(() => {
      if (app.projects.length === 0 && router.route !== '/setup/projects') {
        navigate('/setup/projects');
      }
    });
    live.connect();
    const timer = setInterval(() => app.checkHealth(), 5000);
    return () => clearInterval(timer);
  });

  // other clients changed projects: refresh the project list
  $effect(() => {
    if (live.projectsRevision > 0) app.reloadProjects();
  });

  // a stage preview was started or stopped (by this or another browser)
  $effect(() => {
    void live.compositionsRevision;
    stagePreview.load();
  });

  // fixture profiles changed on disk (or the connection came back)
  $effect(() => {
    if (live.libraryRevision > 1) app.reloadLibrary();
  });

  function onkeydown(event: KeyboardEvent) {
    const target = event.target as HTMLElement;
    if (target.closest('input, textarea, select, [contenteditable]') || event.metaKey || event.ctrlKey) return;
    if (isPrintPage(router.route)) return;
    if (event.key === 'b' || event.key === 'B') {
      event.preventDefault();
      live.setBlackout(!live.master.blackout);
    }
  }

  function onProjectChange(event: Event) {
    const id = Number((event.currentTarget as HTMLSelectElement).value);
    if (id) app.activate(id);
  }
</script>

{#if isPrintPage(router.route)}
  <CuePrintView />
{:else}
<div class="shell">
  <header class="topbar glass">
    <div class="brand">
      <span class="orb" aria-hidden="true"></span>
      <span class="name">LUMEN</span>
    </div>

    {#if app.projects.length > 0}
      <label class="project">
        <span class="sr-only">Active project</span>
        <select value={app.active?.id ?? ''} onchange={onProjectChange} disabled={liveMode}>
          {#if !app.active}<option value="">No active project</option>{/if}
          {#each app.projects as project (project.id)}
            <option value={project.id}>{project.name}</option>
          {/each}
        </select>
      </label>
    {/if}

    <nav aria-label="Setup">
      {#if !liveMode}
        {#each tabs as tab (tab.route)}
          <a href={'#' + tab.route} class:active={router.route === tab.route} class:soon={tab.soon}>
            {tab.label}
          </a>
        {/each}
      {/if}
    </nav>

    <div class="status tone-{output.tone}" title={output.details}>
      <span class="dot"></span>{output.label}
    </div>

    {#if stagePreview.active && !liveMode}
      <button type="button" class="preview-pill" onclick={() => stagePreview.stop()}
        title="A {stagePreview.status.sceneId !== null ? 'scene' : 'composition'} is playing on stage above the playback – click to stop">
        <span class="pulse" aria-hidden="true"></span>
        On stage: {stagePreview.status.sceneId !== null ? 'scene' : 'composition'} <strong>■ Stop</strong>
      </button>
    {/if}

    {#if access.status?.pinSet && !liveMode}
      <button type="button" class="lock" class:locked={access.locked} onclick={toggleLock}
        title={access.locked ? 'Setup is locked – click to enter the PIN' : 'Setup is unlocked – click to lock it'}>
        {access.locked ? '🔒 Locked' : '🔓 Unlocked'}
      </button>
    {/if}

    <div class="status" class:tone-error={!app.online} title={app.online ? 'Server connected' : 'Server not reachable'}>
      <span class="dot"></span>{app.online ? 'Online' : 'Offline'}
    </div>

    <div class="mode" role="group" aria-label="Mode">
      <button type="button" class:on={pageMode === 'setup'} onclick={() => switchTo('setup')}>Setup</button>
      <button type="button" class:on={pageMode === 'show'} onclick={() => switchTo('show')}>Show</button>
      <button type="button" class:on={pageMode === 'party'} onclick={() => switchTo('party')}>Party</button>
    </div>
  </header>

  <main>
    {#if !app.loaded}
      <div class="empty">Loading…</div>
    {:else if router.route === '/setup/projects'}
      <ProjectsView />
    {:else if router.route === '/setup/universes'}
      <UniversesView />
    {:else if router.route === '/setup/patch'}
      <PatchView />
    {:else if router.route === '/setup/programmer'}
      <ProgrammerView />
    {:else if router.route === '/setup/stage'}
      <StageView />
    {:else if router.route === '/setup/scenes'}
      <ScenesView />
    {:else if router.route === '/setup/compositions'}
      <CompositionsView />
    {:else if router.route === '/setup/dmx'}
      <DmxMonitorView />
    {:else if router.route === '/setup/settings'}
      <SettingsView />
    {:else if router.route === '/setup/party'}
      <PartyButtonsView />
    {:else if router.route === '/party'}
      <PartyView />
    {:else}
      <ShowView />
    {/if}
  </main>

  {#if app.active}
    <MasterBar />
  {/if}
</div>
{/if}

<svelte:window {onkeydown} />

<Modal bind:open={leaveOpen} title="Leave {pageMode} mode?">
  <p>
    {#if pageMode === 'show'}
      The playback keeps its place.
    {:else}
      The party look is kept for later.
    {/if}
    {#if leaveTarget === 'setup'}
      In setup mode the programmer is unlocked again and can change the light on stage.
    {:else if leaveTarget === 'party'}
      Party mode takes over the stage; the playback gives no light until show mode returns.
    {:else}
      Show mode plays the cue list from where it stands.
    {/if}
  </p>
  {#snippet actions()}
    <button class="btn ghost" type="button" onclick={() => (leaveOpen = false)}>Stay in {pageMode}</button>
    <button class="btn primary" type="button" onclick={confirmLeave}>Switch to {leaveTarget}</button>
  {/snippet}
</Modal>

<PinPrompt />

<Toasts />

<style>
  .shell {
    display: flex;
    flex-direction: column;
    min-height: 100vh;
    padding: 12px;
    gap: 12px;
  }

  .topbar {
    position: sticky;
    top: 12px;
    z-index: 10;
    display: flex;
    align-items: center;
    gap: 18px;
    padding: 10px 14px;
  }

  .brand {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .orb {
    width: 14px;
    height: 14px;
    border-radius: 50%;
    background: radial-gradient(circle, #fff 0 25%, var(--accent) 60%);
    box-shadow: 0 0 14px var(--accent-glow), 0 0 30px var(--accent-glow);
  }

  .name {
    font-weight: 700;
    letter-spacing: 0.28em;
    font-size: 13px;
  }

  .project select {
    min-height: 34px;
    max-width: 240px;
    padding: 0 10px;
    border-radius: var(--radius-sm);
    border: 1px solid var(--border-strong);
    background: rgba(0, 0, 0, 0.3);
    color: var(--text);
    font: inherit;
    font-weight: 600;
  }

  nav {
    display: flex;
    gap: 4px;
    flex: 1;
  }

  nav a {
    padding: 7px 12px;
    border-radius: var(--radius-sm);
    color: var(--text-dim);
    text-decoration: none;
    font-weight: 500;
  }

  nav a:hover {
    color: var(--text);
    background: var(--panel-raised);
  }

  nav a.active {
    color: var(--accent);
    background: var(--accent-soft);
    box-shadow: inset 0 -2px 0 var(--accent);
  }

  nav a.soon {
    opacity: 0.55;
  }

  .status {
    display: flex;
    align-items: center;
    gap: 7px;
    font-size: 12px;
    color: var(--text-dim);
  }

  .dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--ok);
    box-shadow: 0 0 8px var(--ok);
  }

  .status.tone-error {
    color: var(--danger);
  }

  .status.tone-error .dot {
    background: var(--danger);
    box-shadow: 0 0 8px var(--danger);
  }

  .status.tone-warning .dot {
    background: var(--warning);
    box-shadow: 0 0 8px var(--warning);
  }

  .status.tone-off .dot {
    background: var(--text-dim);
    box-shadow: none;
  }

  .preview-pill {
    display: flex;
    align-items: center;
    gap: 7px;
    min-height: 28px;
    padding: 0 12px;
    border-radius: 999px;
    border: 1px solid var(--accent);
    background: var(--accent-soft);
    color: var(--accent);
    font: inherit;
    font-size: 12px;
    cursor: pointer;
  }

  .pulse {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--accent);
    box-shadow: 0 0 8px var(--accent);
    animation: pulse 1.2s ease-in-out infinite;
  }

  @keyframes pulse {
    50% {
      opacity: 0.3;
    }
  }

  .lock {
    min-height: 28px;
    padding: 0 10px;
    border-radius: 999px;
    border: 1px solid var(--border-strong);
    background: transparent;
    color: var(--text-dim);
    font: inherit;
    font-size: 12px;
    cursor: pointer;
  }

  .lock.locked {
    border-color: var(--warning);
    color: var(--warning);
  }

  .mode {
    display: flex;
    padding: 3px;
    border-radius: 999px;
    border: 1px solid var(--border-strong);
    background: rgba(0, 0, 0, 0.3);
  }

  .mode button {
    min-height: 28px;
    padding: 0 14px;
    border: 0;
    border-radius: 999px;
    background: transparent;
    color: var(--text-dim);
    font: inherit;
    font-weight: 600;
    cursor: pointer;
  }

  .mode button.on {
    background: var(--accent);
    color: var(--accent-ink);
    box-shadow: 0 0 14px var(--accent-glow);
  }

  main {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
  }

  .sr-only {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip: rect(0 0 0 0);
  }

  @media (max-width: 900px) {
    .topbar {
      flex-wrap: wrap;
    }

    nav {
      order: 10;
      flex-basis: 100%;
      overflow-x: auto;
    }
  }
</style>
