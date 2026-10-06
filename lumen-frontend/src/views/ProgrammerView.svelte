<script lang="ts">
  import FixtureIcon from '../components/FixtureIcon.svelte';
  import Modal from '../components/Modal.svelte';
  import ProgrammerPanel from '../components/ProgrammerPanel.svelte';
  import RecordSceneModal from '../components/RecordSceneModal.svelte';
  import { api } from '../lib/api';
  import { headPreviewsOf, previewOf, rgbToCss } from '../lib/color';
  import { live } from '../lib/live.svelte';
  import { programmerEntries, type ProgrammerEntry } from '../lib/programmer';
  import { navigate } from '../lib/router.svelte';
  import { programmerSelection } from '../lib/selection.svelte';
  import { app } from '../lib/state.svelte';
  import { notify, showError } from '../lib/toasts.svelte';
  import type { Fixture, Group, Universe } from '../lib/types';

  let universes = $state<Universe[]>([]);
  let fixtures = $state<Fixture[]>([]);
  let groups = $state<Group[]>([]);
  let groupOpen = $state(false);
  let editingGroup = $state<Group | null>(null);
  let groupForm = $state({ name: '', color: '#3cf0ff', useSelection: true });
  let recordOpen = $state(false);

  const project = $derived(app.active);
  // effects depend on the id only: reloading the project list yields new objects for the same project
  const projectId = $derived(app.active?.id ?? null);

  $effect(() => {
    void live.patchRevision;
    if (projectId !== null) load(projectId);
  });

  async function load(projectId: number) {
    try {
      [universes, fixtures, groups] = await Promise.all([
        api.universes.list(projectId),
        api.fixtures.list(projectId),
        api.groups.list(projectId),
      ]);
      select(selection.filter((id) => fixtures.some((f) => f.id === id)));
    } catch (e) {
      showError(e);
    }
  }

  const entries = $derived(programmerEntries(fixtures, (id) => app.fixtureType(id)));

  // shared with the scene editor, which can merge only the selected fixtures
  const selection = $derived(programmerSelection.ids);
  const selected = $derived(entries.filter((e) => selection.includes(e.fixture.id)));
  const universeNumber = $derived(new Map(universes.map((u) => [u.id, u.number])));
  const masterLevel = $derived(live.master.blackout ? 0 : live.master.level);

  // --- selection ---------------------------------------------------------------------------------------------

  function select(ids: number[]) {
    programmerSelection.ids = ids;
  }

  function toggle(id: number, event: MouseEvent) {
    if (event.shiftKey || event.metaKey || event.ctrlKey) {
      select(selection.includes(id) ? selection.filter((s) => s !== id) : [...selection, id]);
    } else {
      select(selection.length === 1 && selection[0] === id ? [] : [id]);
    }
  }

  function selectGroup(group: Group) {
    select(group.fixtureIds.filter((id) => entries.some((e) => e.fixture.id === id)));
  }

  function preview(entry: ProgrammerEntry) {
    const p = previewOf(entry.mode, live.programmer[entry.fixture.id] ?? {}, masterLevel);
    return `--glow: ${rgbToCss(p.color)}; --intensity: ${p.intensity.toFixed(3)}`;
  }

  // --- groups ------------------------------------------------------------------------------------------------

  function openGroup(group: Group | null) {
    editingGroup = group;
    groupForm = { name: group?.name ?? '', color: group?.color ?? '#3cf0ff', useSelection: group === null };
    groupOpen = true;
  }

  async function saveGroup() {
    if (!project) return;
    const request = {
      name: groupForm.name,
      color: groupForm.color,
      sortOrder: editingGroup?.sortOrder ?? groups.length,
      fixtureIds: groupForm.useSelection ? selection : (editingGroup?.fixtureIds ?? []),
    };
    try {
      if (editingGroup) await api.groups.update(project.id, editingGroup.id, request);
      else await api.groups.create(project.id, request);
      notify(`Saved group "${request.name}"`);
      groupOpen = false;
      await load(project.id);
    } catch (e) {
      showError(e);
    }
  }

  async function deleteGroup() {
    if (!project || !editingGroup) return;
    try {
      await api.groups.remove(project.id, editingGroup.id);
      groupOpen = false;
      await load(project.id);
    } catch (e) {
      showError(e);
    }
  }

  function onkeydown(event: KeyboardEvent) {
    if (event.key === 'Escape' && !groupOpen) select([]);
  }
</script>

<svelte:window {onkeydown} />

{#if !project}
  <div class="glass empty">
    <h2>No active project</h2>
    <button class="btn primary" type="button" onclick={() => navigate('/setup/projects')}>Go to projects</button>
  </div>
{:else if entries.length === 0}
  <div class="glass empty">
    <h2>Nothing to control yet</h2>
    <p>Patch some fixtures first.</p>
    <button class="btn primary" type="button" onclick={() => navigate('/setup/patch')}>Go to patch</button>
  </div>
{:else}
  <div class="programmer">
    <!-- Groups ------------------------------------------------------------------------------------------ -->
    <aside class="groups glass">
      <h3>Groups</h3>
      <button type="button" class="group" onclick={() => select(entries.map((e) => e.fixture.id))}>
        <span class="swatch all"></span>All fixtures<span class="count mono">{entries.length}</span>
      </button>
      {#each groups as group (group.id)}
        <div class="group-row">
          <button type="button" class="group" onclick={() => selectGroup(group)}>
            <span class="swatch" style="--color: {group.color ?? '#8a93a6'}"></span>
            {group.name}<span class="count mono">{group.fixtureIds.length}</span>
          </button>
          <button type="button" class="btn ghost small edit" onclick={() => openGroup(group)} aria-label="Edit {group.name}">✎</button>
        </div>
      {/each}
      <button type="button" class="btn small" disabled={selection.length === 0} onclick={() => openGroup(null)}>
        + Group from selection
      </button>
      <p class="hint dim">Click selects one fixture, Shift/⌘-click adds more. Esc clears the selection.</p>
    </aside>

    <!-- Fixture list ------------------------------------------------------------------------------------ -->
    <div class="center">
    <div class="toolbar">
      <span class="dim">{Object.keys(live.programmer).length} fixtures with programmer values</span>
      <button class="btn small ghost" type="button" onclick={() => live.clear(Object.keys(live.programmer).map(Number))}
        disabled={Object.keys(live.programmer).length === 0}>Clear programmer</button>
      <button class="btn small record" type="button" onclick={() => (recordOpen = true)}
        disabled={Object.keys(live.programmer).length === 0}>⏺ Record scene</button>
    </div>
    <section class="fixtures glass" aria-label="Fixtures">
      <div class="fixture-head dim" aria-hidden="true">
        <span></span><span>Fixture</span><span>Address</span><span class="level-head">Level</span>
      </div>
      {#each entries as entry (entry.fixture.id)}
        {@const own = live.programmer[entry.fixture.id]}
        <button
          type="button"
          class="fixture"
          class:selected={selection.includes(entry.fixture.id)}
          class:programmed={own !== undefined}
          style={preview(entry)}
          onclick={(e) => toggle(entry.fixture.id, e)}
          aria-pressed={selection.includes(entry.fixture.id)}
        >
          <span class="lamp"><FixtureIcon category={entry.type.category} size={16} /></span>
          <span class="fixture-text">
            <span class="fixture-name">{entry.fixture.name}</span>
            <span class="fixture-type">{entry.type.model} · {entry.mode.name}</span>
            {#if entry.mode.heads > 0}
              <span class="fixture-heads" aria-hidden="true">
                {#each headPreviewsOf(entry.mode, own ?? {}, masterLevel) as head, i (i)}
                  <span style="--glow: {rgbToCss(head.color)}; --intensity: {head.intensity.toFixed(3)}"></span>
                {/each}
              </span>
            {/if}
          </span>
          <span class="fixture-address mono">{universeNumber.get(entry.fixture.universeId)}·{entry.fixture.address}</span>
          <span class="fixture-level mono">{own?.dimmer !== undefined ? `${Math.round(own.dimmer * 100)}%` : '–'}</span>
        </button>
      {/each}
    </section>
    </div>

    <!-- Programmer -------------------------------------------------------------------------------------- -->
    <aside class="panel glass">
      {#if selected.length === 0}
        <div class="none">
          <h3>Programmer</h3>
          <p class="dim">Select fixtures to control them. When the look is right, record it as a scene.</p>
        </div>
      {:else}
        <ProgrammerPanel {projectId} {entries} {selection} />
      {/if}
    </aside>
  </div>
{/if}

<RecordSceneModal bind:open={recordOpen} />

<Modal bind:open={groupOpen} title={editingGroup ? 'Edit group' : 'New group'}>
  <form id="group-form" onsubmit={(e) => { e.preventDefault(); saveGroup(); }}>
    <div class="row">
      <label class="field">Name<input bind:value={groupForm.name} placeholder="Front light" required /></label>
      <label class="field color-field">Color<input type="color" bind:value={groupForm.color} /></label>
    </div>
    <label class="check">
      <input type="checkbox" bind:checked={groupForm.useSelection} disabled={!editingGroup} />
      Use the current selection as members ({selection.length} fixtures)
    </label>
  </form>
  {#snippet actions()}
    {#if editingGroup}<button class="btn danger" type="button" onclick={deleteGroup}>Delete</button>{/if}
    <button class="btn ghost" type="button" onclick={() => (groupOpen = false)}>Cancel</button>
    <button class="btn primary" type="submit" form="group-form">Save</button>
  {/snippet}
</Modal>

<style>
  .programmer {
    display: grid;
    /* the controls get most of the room: complex fixtures have many attributes */
    grid-template-columns: 200px minmax(260px, 2fr) minmax(360px, 3fr);
    gap: 12px;
    align-items: start;
  }

  .groups,
  .panel {
    position: sticky;
    top: 84px;
    display: flex;
    flex-direction: column;
    gap: 10px;
    min-width: 0;
    padding: 16px;
    max-height: calc(100vh - 170px);
    overflow: auto;
  }

  .group-row {
    display: flex;
    align-items: center;
  }

  .group {
    flex: 1;
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 8px 10px;
    border: 1px solid transparent;
    border-radius: var(--radius-sm);
    background: transparent;
    color: var(--text);
    font: inherit;
    text-align: left;
    cursor: pointer;
  }

  .group:hover {
    border-color: var(--border-strong);
    background: var(--panel-raised);
  }

  .swatch {
    width: 12px;
    height: 12px;
    border-radius: 4px;
    background: var(--color);
    box-shadow: 0 0 8px var(--color);
  }

  .swatch.all {
    background: conic-gradient(red, yellow, lime, cyan, blue, magenta, red);
  }

  .count {
    margin-left: auto;
    font-size: 11px;
    color: var(--text-faint);
  }

  .edit {
    opacity: 0.5;
  }

  .hint {
    margin: 4px 0 0;
    font-size: 11px;
  }

  .center {
    display: flex;
    flex-direction: column;
    gap: 12px;
    min-width: 0;
  }

  .toolbar {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .toolbar .dim {
    flex: 1;
    font-size: 12px;
  }

  .fixtures {
    display: flex;
    flex-direction: column;
    padding: 6px;
    min-width: 0;
  }

  .fixture-head,
  .fixture {
    display: grid;
    grid-template-columns: 30px minmax(0, 1fr) auto 48px;
    gap: 10px;
    align-items: center;
  }

  .fixture-head {
    padding: 4px 10px 6px;
    font-size: 11px;
    letter-spacing: 0.06em;
    text-transform: uppercase;
  }

  .level-head {
    text-align: right;
  }

  .fixture {
    padding: 7px 10px;
    border: 1px solid transparent;
    border-radius: var(--radius-sm);
    background: transparent;
    color: var(--text);
    font: inherit;
    text-align: left;
    cursor: pointer;
  }

  .fixture + .fixture {
    margin-top: 1px;
  }

  .fixture:hover {
    background: var(--panel-raised);
  }

  .fixture.selected {
    border-color: var(--accent);
    background: var(--accent-soft);
  }

  .lamp {
    display: grid;
    place-items: center;
    width: 28px;
    height: 28px;
    border-radius: 50%;
    color: color-mix(in srgb, var(--glow) calc(var(--intensity) * 100%), var(--text-faint));
    background: color-mix(in srgb, var(--glow) calc(var(--intensity) * 45%), rgba(255, 255, 255, 0.04));
    box-shadow: 0 0 calc(18px * var(--intensity)) var(--glow);
  }

  .fixture-text {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
  }

  /* long names wrap instead of being cut off */
  .fixture-name {
    font-weight: 600;
    overflow-wrap: anywhere;
  }

  .fixture.programmed .fixture-name::after {
    content: ' ●';
    color: var(--accent);
    font-size: 9px;
    vertical-align: middle;
  }

  .fixture-type {
    font-size: 11px;
    color: var(--text-faint);
    overflow-wrap: anywhere;
  }

  .fixture-heads {
    display: flex;
    gap: 2px;
    max-width: 220px;
    margin-top: 2px;
  }

  .fixture-heads span {
    flex: 1;
    height: 4px;
    border-radius: 2px;
    background: color-mix(in srgb, var(--glow) calc(var(--intensity) * 100%), #1b2030);
  }

  .fixture-address {
    font-size: 12px;
    color: var(--text-dim);
  }

  .fixture-level {
    font-size: 12px;
    color: var(--text-dim);
    text-align: right;
  }

  .none {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .none p {
    margin: 0;
  }


  form {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .color-field {
    flex: 0 0 90px;
  }

  .color-field input {
    padding: 4px;
  }

  .check {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
  }

  @media (max-width: 1200px) {
    .programmer {
      grid-template-columns: minmax(240px, 2fr) minmax(320px, 3fr);
    }

    .groups {
      grid-column: 1 / -1;
      position: static;
      flex-direction: row;
      flex-wrap: wrap;
      align-items: center;
      max-height: none;
    }

    .hint {
      display: none;
    }
  }

  @media (max-width: 800px) {
    .programmer {
      grid-template-columns: minmax(0, 1fr);
    }

    .panel {
      position: static;
      max-height: none;
    }
  }
</style>
