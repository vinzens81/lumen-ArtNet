<script lang="ts">
  import AddressGrid from '../components/AddressGrid.svelte';
  import FixtureIcon from '../components/FixtureIcon.svelte';
  import OflImportModal from '../components/OflImportModal.svelte';
  import { api } from '../lib/api';
  import { fixtureHue, footprint, locateValues, nextFreeAddress, plannedFixtures } from '../lib/patch';
  import { navigate } from '../lib/router.svelte';
  import { live } from '../lib/live.svelte';
  import { app } from '../lib/state.svelte';
  import { notify, showError } from '../lib/toasts.svelte';
  import type { Fixture, FixtureType, PatchIssue, Universe } from '../lib/types';

  type Panel = 'list' | 'add' | 'edit';

  let universes = $state<Universe[]>([]);
  let fixtures = $state<Fixture[]>([]);
  let issues = $state<PatchIssue[]>([]);
  let universeId = $state<number | null>(null);
  let panel = $state<Panel>('list');
  let selectedId = $state<number | null>(null);
  let search = $state('');
  let expandedType = $state<string | null>(null);
  let located = $state<number[]>([]);
  let confirmDelete = $state(false);

  /** true while several fixtures are being created one after another */
  let adding = $state(false);
  let form = $state({ typeId: '', mode: '', name: '', shortName: '', universeId: 0, address: 1, count: 1 });
  let addressTouched = $state(false);

  const project = $derived(app.active);
  let oflOpen = $state(false);
  // effects depend on the id only: reloading the project list yields new objects for the same project
  const projectId = $derived(app.active?.id ?? null);
  const universeFixtures = $derived(
    [...fixtures.filter((f) => f.universeId === universeId)].sort((a, b) => a.address - b.address || a.id - b.id),
  );
  const selected = $derived(fixtures.find((f) => f.id === selectedId) ?? null);
  const formType = $derived(app.fixtureType(form.typeId));
  const formMode = $derived(formType?.modes.find((m) => m.name === form.mode));
  const formChannels = $derived(formMode?.dmxChannelCount ?? 1);
  const preview = $derived(
    panel === 'add' && form.universeId === universeId
      ? { address: form.address, count: formChannels * Math.max(1, form.count) }
      : panel === 'edit' && form.universeId === universeId
        ? { address: form.address, count: formChannels }
        : null,
  );
  const issuesByFixture = $derived(new Set(issues.flatMap((i) => i.fixtureIds)));

  const groupedLibrary = $derived.by(() => {
    const term = search.trim().toLowerCase();
    const matches = app.library.filter((t) =>
      [t.manufacturer, t.model, t.id, t.category].some((v) => v.toLowerCase().includes(term)),
    );
    const groups = new Map<string, FixtureType[]>();
    for (const type of matches) {
      groups.set(type.manufacturer, [...(groups.get(type.manufacturer) ?? []), type]);
    }
    return [...groups.entries()];
  });

  $effect(() => {
    if (projectId !== null) loadAll(projectId, true);
  });

  // changes from other clients
  $effect(() => {
    if (live.patchRevision > 0 && projectId !== null) loadAll(projectId);
  });

  async function loadAll(projectId: number, resetSelection = false) {
    try {
      const [u, f, i] = await Promise.all([
        api.universes.list(projectId),
        api.fixtures.list(projectId),
        api.patchIssues(projectId),
      ]);
      universes = u;
      fixtures = f;
      issues = i;
      if (resetSelection || !u.some((x) => x.id === universeId)) {
        universeId = u[0]?.id ?? null;
        panel = 'list';
        selectedId = null;
      }
    } catch (e) {
      showError(e);
    }
  }

  // --- adding ------------------------------------------------------------------------------------------------

  function startAdd(type: FixtureType, modeName: string) {
    const targetUniverse = universeId ?? universes[0]?.id ?? 0;
    form = {
      typeId: type.id,
      mode: modeName,
      name: type.model,
      shortName: '',
      universeId: targetUniverse,
      address: 1,
      count: 1,
    };
    addressTouched = false;
    selectedId = null;
    panel = 'add';
    suggestAddress();
  }

  function suggestAddress() {
    const others = fixtures.filter((f) => f.universeId === form.universeId);
    const ignore = panel === 'edit' ? (selectedId ?? undefined) : undefined;
    const count = formChannels * (panel === 'add' ? Math.max(1, form.count) : 1);
    form.address = nextFreeAddress(others, count, 1, ignore) ?? 1;
  }

  $effect(() => {
    // keep the suggestion up to date while the user has not typed an address
    void form.mode;
    void form.count;
    void form.universeId;
    if (panel === 'add' && !addressTouched && !adding) suggestAddress();
  });

  async function add() {
    if (!project || !formMode) return;
    // everything is fixed before the first request: each created fixture reloads the patch (live update), and the
    // address suggestion must not move the remaining fixtures
    const projectId = project.id;
    const targetUniverse = form.universeId;
    const planned = plannedFixtures(
      {
        name: form.name,
        shortName: form.shortName || null,
        fixtureTypeId: form.typeId,
        mode: form.mode,
        universeId: targetUniverse,
        position: null,
        sortOrder: 0,
      },
      form.address,
      formChannels,
      form.count,
    );
    adding = true;
    try {
      for (const fixture of planned) {
        await api.fixtures.create(projectId, fixture);
      }
      notify(planned.length === 1 ? `Patched "${planned[0].name}"` : `Patched ${planned.length} fixtures`);
      panel = 'list';
      universeId = targetUniverse;
    } catch (e) {
      showError(e);
    } finally {
      adding = false;
      await loadAll(projectId);
    }
  }

  // --- editing -----------------------------------------------------------------------------------------------

  function select(id: number) {
    const fixture = fixtures.find((f) => f.id === id);
    if (!fixture) return;
    selectedId = id;
    universeId = fixture.universeId;
    form = {
      typeId: fixture.fixtureTypeId,
      mode: fixture.mode,
      name: fixture.name,
      shortName: fixture.shortName ?? '',
      universeId: fixture.universeId,
      address: fixture.address,
      count: 1,
    };
    confirmDelete = false;
    panel = 'edit';
  }

  function onSelectAddress(address: number) {
    if (panel === 'add' || panel === 'edit') {
      form.address = address;
      form.universeId = universeId ?? form.universeId;
      addressTouched = true;
    }
  }

  async function save() {
    if (!project || !selected) return;
    try {
      await api.fixtures.update(project.id, selected.id, {
        name: form.name,
        shortName: form.shortName || null,
        fixtureTypeId: form.typeId,
        mode: form.mode,
        universeId: form.universeId,
        address: form.address,
        position: selected.position,
        sortOrder: selected.sortOrder,
      });
      notify(`Saved "${form.name}"`);
      universeId = form.universeId;
      await loadAll(project.id);
    } catch (e) {
      showError(e);
    }
  }

  async function remove() {
    if (!project || !selected) return;
    if (!confirmDelete) {
      confirmDelete = true;
      return;
    }
    try {
      await api.fixtures.remove(project.id, selected.id);
      located = located.filter((id) => id !== selected.id);
      notify(`Removed "${selected.name}"`);
      panel = 'list';
      selectedId = null;
      await loadAll(project.id);
    } catch (e) {
      showError(e);
    }
  }

  // --- locate ------------------------------------------------------------------------------------------------

  async function toggleLocate(fixture: Fixture) {
    const mode = app.fixtureType(fixture.fixtureTypeId)?.modes.find((m) => m.name === fixture.mode);
    if (!mode) return;
    try {
      if (located.includes(fixture.id)) {
        await api.programmer.clearFixture(fixture.id);
        located = located.filter((id) => id !== fixture.id);
      } else {
        await api.programmer.set(fixture.id, locateValues(mode));
        located = [...located, fixture.id];
      }
    } catch (e) {
      showError(e);
    }
  }

  function typeLabel(fixture: Fixture): string {
    const type = app.fixtureType(fixture.fixtureTypeId);
    return type ? `${type.model} · ${fixture.mode}` : `${fixture.fixtureTypeId} (missing)`;
  }

  function range(fixture: Fixture): string {
    return fixture.lastAddress && fixture.lastAddress !== fixture.address
      ? `${fixture.address}–${fixture.lastAddress}`
      : `${fixture.address}`;
  }
</script>

{#if !project}
  <div class="glass empty">
    <h2>No active project</h2>
    <button class="btn primary" type="button" onclick={() => navigate('/setup/projects')}>Go to projects</button>
  </div>
{:else if universes.length === 0}
  <div class="glass empty">
    <h2>No universes yet</h2>
    <p>Fixtures are patched into universes. Create one first.</p>
    <button class="btn primary" type="button" onclick={() => navigate('/setup/universes')}>Set up universes</button>
  </div>
{:else}
  <div class="patch">
    <!-- Library ----------------------------------------------------------------------------------------- -->
    <aside class="library glass">
      <h3>Fixture library</h3>
      <input class="search" type="search" placeholder="Search fixtures…" bind:value={search} aria-label="Search fixtures" />
      <div class="types">
        {#each groupedLibrary as [manufacturer, types] (manufacturer)}
          <div class="manufacturer">{manufacturer}</div>
          {#each types as type (type.id)}
            <div class="type" class:open={expandedType === type.id}>
              <button type="button" class="type-head" onclick={() => (expandedType = expandedType === type.id ? null : type.id)}>
                <FixtureIcon category={type.category} />
                <span class="model">{type.model}</span>
                <span class="dim small">{type.modes.length} {type.modes.length === 1 ? 'mode' : 'modes'}</span>
              </button>
              {#if expandedType === type.id}
                <div class="modes">
                  {#each type.modes as mode (mode.name)}
                    <button type="button" class="btn small" onclick={() => startAdd(type, mode.name)}>
                      + {mode.name} <span class="dim mono">{mode.dmxChannelCount} ch</span>
                    </button>
                  {/each}
                </div>
              {/if}
            </div>
          {/each}
        {:else}
          <p class="dim">No fixture types match "{search}".</p>
        {/each}
      </div>
      <button type="button" class="btn small ghost import" onclick={() => (oflOpen = true)}
        title="Add a fixture from the Open Fixture Library">⇣ Import from Open Fixture Library</button>
      {#if app.libraryIssues.length > 0}
        <p class="lib-issues"><span class="badge warning">{app.libraryIssues.length}</span> library files have problems</p>
      {/if}
    </aside>

    <!-- Address grid ------------------------------------------------------------------------------------ -->
    <section class="center">
      <div class="glass grid-panel">
        <div class="tabs" role="tablist">
          {#each universes as universe (universe.id)}
            <button type="button" role="tab" aria-selected={universe.id === universeId}
              class:active={universe.id === universeId} onclick={() => (universeId = universe.id)}>
              <span class="mono">{universe.number}</span> {universe.name ?? 'Universe'}
              <span class="count">{fixtures.filter((f) => f.universeId === universe.id).length}</span>
            </button>
          {/each}
        </div>
        <AddressGrid fixtures={universeFixtures} {selectedId} {preview} onSelectFixture={select} {onSelectAddress} />
        <p class="legend dim">
          {#if panel === 'add' || panel === 'edit'}Click a free channel to place the fixture there.{:else}Click a fixture to edit it, or pick a fixture type from the library to add one.{/if}
        </p>
      </div>

      {#if issues.length > 0}
        <div class="glass issues">
          <h3>Patch issues</h3>
          {#each issues as issue, i (i)}
            <button type="button" class="issue" class:error={issue.severity === 'error'}
              onclick={() => issue.fixtureIds[0] !== undefined && select(issue.fixtureIds[0])}>
              <span class="badge {issue.severity}">{issue.severity}</span>{issue.message}
            </button>
          {/each}
        </div>
      {/if}
    </section>

    <!-- Side panel -------------------------------------------------------------------------------------- -->
    <aside class="side glass">
      {#if panel === 'list'}
        <h3>Fixtures in this universe</h3>
        {#if universeFixtures.length === 0}
          <p class="dim">Nothing patched here yet.</p>
        {/if}
        <ul class="fixtures">
          {#each universeFixtures as fixture (fixture.id)}
            <li>
              <button type="button" class="fixture" onclick={() => select(fixture.id)} style="--hue: {fixtureHue(fixture.id)}">
                <span class="swatch" class:lit={located.includes(fixture.id)}></span>
                <span class="fx-name">{fixture.name}</span>
                <span class="mono dim">{range(fixture)}</span>
                {#if issuesByFixture.has(fixture.id)}<span class="badge error">!</span>{/if}
              </button>
            </li>
          {/each}
        </ul>
      {:else}
        <form onsubmit={(e) => { e.preventDefault(); if (panel === 'add') add(); else save(); }}>
          <div class="form-head">
            {#if formType}<FixtureIcon category={formType.category} size={28} />{/if}
            <div>
              <h2>{panel === 'add' ? 'Add fixture' : form.name}</h2>
              <span class="dim small">{formType ? `${formType.manufacturer} ${formType.model}` : form.typeId}</span>
            </div>
          </div>

          <label class="field">Name<input bind:value={form.name} required /></label>
          <div class="row">
            <label class="field">Short name<input bind:value={form.shortName} placeholder="FL" maxlength="6" disabled={panel === 'add' && form.count > 1} /></label>
            <label class="field">Mode
              <select bind:value={form.mode}>
                {#each formType?.modes ?? [] as mode (mode.name)}
                  <option value={mode.name}>{mode.name} ({mode.dmxChannelCount} ch)</option>
                {/each}
              </select>
            </label>
          </div>
          <div class="row">
            <label class="field">Universe
              <select bind:value={form.universeId}>
                {#each universes as universe (universe.id)}
                  <option value={universe.id}>{universe.number} {universe.name ?? ''}</option>
                {/each}
              </select>
            </label>
            <label class="field">Address
              <input type="number" min="1" max="512" bind:value={form.address} oninput={() => (addressTouched = true)} required />
            </label>
            {#if panel === 'add'}
              <label class="field">Count<input type="number" min="1" max="64" bind:value={form.count} /></label>
            {/if}
          </div>
          <p class="field">
            <span class="hint">
              Occupies <span class="mono">{form.address}–{form.address + formChannels * (panel === 'add' ? Math.max(1, form.count) : 1) - 1}</span>
              <button type="button" class="link" onclick={() => { addressTouched = false; suggestAddress(); }}>next free</button>
            </span>
          </p>

          <div class="form-actions">
            {#if panel === 'add'}
              <button class="btn primary" type="submit" disabled={!formMode || adding}>Patch {form.count > 1 ? `${form.count} fixtures` : 'fixture'}</button>
            {:else}
              <button class="btn primary" type="submit">Save</button>
              {#if selected}
                <button class="btn" type="button" class:locating={located.includes(selected.id)} onclick={() => selected && toggleLocate(selected)}>
                  {located.includes(selected.id) ? 'Stop locate' : 'Locate'}
                </button>
              {/if}
            {/if}
            <button class="btn ghost" type="button" onclick={() => { panel = 'list'; selectedId = null; }}>Cancel</button>
          </div>

          {#if panel === 'edit' && selected}
            <div class="details">
              <span class="dim">{typeLabel(selected)} · {footprint(selected)} channels</span>
              <button class="btn small danger" type="button" onclick={remove}>{confirmDelete ? 'Really remove?' : 'Remove fixture'}</button>
            </div>
          {/if}
        </form>
      {/if}
    </aside>
  </div>
{/if}

<OflImportModal bind:open={oflOpen} />

<style>
  .patch {
    display: grid;
    grid-template-columns: 270px minmax(0, 1fr) 330px;
    gap: 12px;
    align-items: start;
  }

  .library .import {
    align-self: flex-start;
  }

  .library,
  .side {
    min-width: 0;
    position: sticky;
    top: 84px;
    display: flex;
    flex-direction: column;
    gap: 12px;
    padding: 16px;
    max-height: calc(100vh - 100px);
    overflow: auto;
  }

  .search {
    min-height: 36px;
    padding: 0 12px;
    border-radius: var(--radius-sm);
    border: 1px solid var(--border-strong);
    background: rgba(0, 0, 0, 0.25);
    color: var(--text);
    font: inherit;
  }

  .types {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .manufacturer {
    margin-top: 8px;
    font-size: 11px;
    font-weight: 600;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--text-faint);
  }

  .type {
    border-radius: var(--radius-sm);
    border: 1px solid transparent;
  }

  .type.open {
    border-color: var(--border);
    background: var(--panel-raised);
  }

  .type-head {
    display: flex;
    align-items: center;
    gap: 10px;
    width: 100%;
    padding: 8px;
    border: 0;
    border-radius: var(--radius-sm);
    background: transparent;
    color: var(--text);
    font: inherit;
    text-align: left;
    cursor: pointer;
  }

  .type-head:hover {
    background: var(--panel-raised);
    color: var(--accent);
  }

  .model {
    flex: 1;
  }

  .small {
    font-size: 11px;
  }

  .modes {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    padding: 0 8px 10px 38px;
  }

  .lib-issues {
    margin: 0;
    font-size: 12px;
    color: var(--text-dim);
  }

  .center {
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .grid-panel {
    display: flex;
    flex-direction: column;
    gap: 14px;
    padding: 16px;
  }

  .tabs {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .tabs button {
    display: flex;
    align-items: center;
    gap: 8px;
    min-height: 32px;
    padding: 0 12px;
    border-radius: 999px;
    border: 1px solid var(--border-strong);
    background: transparent;
    color: var(--text-dim);
    font: inherit;
    cursor: pointer;
  }

  .tabs button.active {
    border-color: var(--accent);
    color: var(--accent);
    background: var(--accent-soft);
  }

  .count {
    min-width: 20px;
    padding: 0 6px;
    border-radius: 999px;
    background: rgba(255, 255, 255, 0.08);
    font-size: 11px;
  }

  .legend {
    margin: 0;
    font-size: 12px;
  }

  .issues {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 16px;
  }

  .issue {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 8px 10px;
    border-radius: var(--radius-sm);
    border: 1px solid var(--border);
    background: transparent;
    color: var(--text);
    font: inherit;
    text-align: left;
    cursor: pointer;
  }

  .issue.error {
    border-color: rgba(255, 77, 109, 0.35);
    background: var(--danger-soft);
  }

  .fixtures {
    display: flex;
    flex-direction: column;
    gap: 4px;
    margin: 0;
    padding: 0;
    list-style: none;
  }

  .fixture {
    display: flex;
    align-items: center;
    gap: 10px;
    width: 100%;
    padding: 8px 10px;
    border-radius: var(--radius-sm);
    border: 1px solid transparent;
    background: transparent;
    color: var(--text);
    font: inherit;
    text-align: left;
    cursor: pointer;
  }

  .fixture:hover {
    border-color: var(--border-strong);
    background: var(--panel-raised);
  }

  .swatch {
    width: 12px;
    height: 12px;
    border-radius: 4px;
    background: hsl(var(--hue) 85% 58%);
    box-shadow: 0 0 8px hsl(var(--hue) 85% 58% / 0.6);
  }

  .swatch.lit {
    background: #fff;
    box-shadow: 0 0 6px #fff, 0 0 16px #fff, 0 0 30px var(--accent-glow);
  }

  .fx-name {
    flex: 1;
  }

  form {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .form-head {
    display: flex;
    align-items: center;
    gap: 12px;
    color: var(--accent);
  }

  .form-head h2 {
    color: var(--text);
  }

  .form-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .locating {
    border-color: #fff;
    box-shadow: 0 0 16px rgba(255, 255, 255, 0.6);
  }

  .details {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
    padding-top: 12px;
    border-top: 1px solid var(--border);
    font-size: 12px;
  }

  .link {
    margin-left: 8px;
    padding: 0;
    border: 0;
    background: none;
    color: var(--accent);
    font: inherit;
    cursor: pointer;
    text-decoration: underline;
  }

  @media (max-width: 1200px) {
    .patch {
      grid-template-columns: 240px minmax(0, 1fr);
    }

    .side {
      grid-column: 1 / -1;
      position: static;
      max-height: none;
    }
  }

  @media (max-width: 800px) {
    .patch {
      grid-template-columns: minmax(0, 1fr);
    }

    .library {
      position: static;
      max-height: 320px;
    }
  }
</style>
