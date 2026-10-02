<script lang="ts">
  import { onMount } from 'svelte';
  import MasterBar from './components/MasterBar.svelte';
  import Modal from './components/Modal.svelte';
  import PinPrompt from './components/PinPrompt.svelte';
  import Toasts from './components/Toasts.svelte';
  import { access } from './lib/access.svelte';
  import { outputIndicator } from './lib/health';
  import { live } from './lib/live.svelte';
  import { isShowMode, navigate, router, type Route } from './lib/router.svelte';
  import { app } from './lib/state.svelte';
  import DmxMonitorView from './views/DmxMonitorView.svelte';
  import PatchView from './views/PatchView.svelte';
  import ProgrammerView from './views/ProgrammerView.svelte';
  import ProjectsView from './views/ProjectsView.svelte';
  import ScenesView from './views/ScenesView.svelte';
  import SettingsView from './views/SettingsView.svelte';
  import ShowView from './views/ShowView.svelte';
  import StageView from './views/StageView.svelte';
  import UniversesView from './views/UniversesView.svelte';

  const tabs: { route: Route; label: string; soon?: boolean }[] = [
    { route: '/setup/projects', label: 'Projects' },
    { route: '/setup/universes', label: 'Universes' },
    { route: '/setup/patch', label: 'Patch' },
    { route: '/setup/programmer', label: 'Programmer' },
    { route: '/setup/stage', label: 'Stage' },
    { route: '/setup/scenes', label: 'Scenes' },
    { route: '/setup/dmx', label: 'DMX' },
    { route: '/setup/settings', label: 'Settings' },
  ];

  const showMode = $derived(isShowMode(router.route));
  const output = $derived(outputIndicator(live.health, live.connected));
  let leaveShowOpen = $state(false);

  // the operating mode is server state: every connected client follows it
  $effect(() => {
    if (!live.modeKnown) return;
    if (live.mode === 'show' && router.route !== '/show') navigate('/show');
    if (live.mode === 'setup' && router.route === '/show') navigate('/setup/scenes');
  });

  async function enterShow() {
    await live.setMode('show');
  }

  async function leaveShow() {
    leaveShowOpen = false;
    await live.setMode('setup');
  }

  /** With a PIN, entering it is the confirmation; without, a confirmation dialog asks. */
  function requestLeaveShow() {
    if (access.locked) access.requestUnlock(leaveShow);
    else leaveShowOpen = true;
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
    document.documentElement.dataset.mode = showMode ? 'show' : 'setup';
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

  // fixture profiles changed on disk (or the connection came back)
  $effect(() => {
    if (live.libraryRevision > 1) app.reloadLibrary();
  });

  function onkeydown(event: KeyboardEvent) {
    const target = event.target as HTMLElement;
    if (target.closest('input, textarea, select, [contenteditable]') || event.metaKey || event.ctrlKey) return;
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

<div class="shell">
  <header class="topbar glass">
    <div class="brand">
      <span class="orb" aria-hidden="true"></span>
      <span class="name">LUMEN</span>
    </div>

    {#if app.projects.length > 0}
      <label class="project">
        <span class="sr-only">Active project</span>
        <select value={app.active?.id ?? ''} onchange={onProjectChange} disabled={showMode}>
          {#if !app.active}<option value="">No active project</option>{/if}
          {#each app.projects as project (project.id)}
            <option value={project.id}>{project.name}</option>
          {/each}
        </select>
      </label>
    {/if}

    <nav aria-label="Setup">
      {#if !showMode}
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

    {#if access.status?.pinSet && !showMode}
      <button type="button" class="lock" class:locked={access.locked} onclick={toggleLock}
        title={access.locked ? 'Setup is locked – click to enter the PIN' : 'Setup is unlocked – click to lock it'}>
        {access.locked ? '🔒 Locked' : '🔓 Unlocked'}
      </button>
    {/if}

    <div class="status" class:tone-error={!app.online} title={app.online ? 'Server connected' : 'Server not reachable'}>
      <span class="dot"></span>{app.online ? 'Online' : 'Offline'}
    </div>

    <div class="mode" role="group" aria-label="Mode">
      <button type="button" class:on={!showMode} onclick={() => showMode && requestLeaveShow()}>Setup</button>
      <button type="button" class:on={showMode} onclick={() => !showMode && enterShow()}>Show</button>
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
    {:else if router.route === '/setup/dmx'}
      <DmxMonitorView />
    {:else if router.route === '/setup/settings'}
      <SettingsView />
    {:else}
      <ShowView />
    {/if}
  </main>

  {#if app.active}
    <MasterBar />
  {/if}
</div>

<svelte:window {onkeydown} />

<Modal bind:open={leaveShowOpen} title="Leave show mode?">
  <p>The playback keeps running. In setup mode the programmer is unlocked again and can change the light on stage.</p>
  {#snippet actions()}
    <button class="btn ghost" type="button" onclick={() => (leaveShowOpen = false)}>Stay in show</button>
    <button class="btn primary" type="button" onclick={leaveShow}>Leave show mode</button>
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
