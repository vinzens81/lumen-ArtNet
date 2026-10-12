<script lang="ts" module>
  const VIEW_KEY = 'lumen.patchView';

  /** The view last chosen on this device: the channel grid or the list. */
  function rememberedView(): 'channels' | 'list' {
    try {
      return localStorage.getItem(VIEW_KEY) === 'list' ? 'list' : 'channels';
    } catch {
      return 'channels';
    }
  }
</script>

<script lang="ts">
  import AddressGrid from '../components/AddressGrid.svelte';
  import FixtureIcon from '../components/FixtureIcon.svelte';
  import OflImportModal from '../components/OflImportModal.svelte';
  import QuickSetupModal from '../components/QuickSetupModal.svelte';
  import QuickSetupOptions from '../components/QuickSetupOptions.svelte';
  import { api } from '../lib/api';
  import {
    commonType, fixtureHue, footprint, locateValues, nextFreeAddress, plannedFixtures, rangeToggled, toggled,
  } from '../lib/patch';
  import { navigate } from '../lib/router.svelte';
  import { live } from '../lib/live.svelte';
  import { canMove } from '../lib/moves';
  import {
    abilitiesOf, fitting, makesSomething, quickSetupRequest, rememberChoice, rememberedChoice, resultSummary,
  } from '../lib/quickSetup';
  import { app } from '../lib/state.svelte';
  import { notify, showError } from '../lib/toasts.svelte';
  import type { Fixture, FixtureType, PatchIssue, Universe } from '../lib/types';

  type Panel = 'list' | 'add' | 'edit' | 'several';
  type View = 'channels' | 'list';

  let universes = $state<Universe[]>([]);
  let fixtures = $state<Fixture[]>([]);
  let issues = $state<PatchIssue[]>([]);
  let universeId = $state<number | null>(null);
  let panel = $state<Panel>('list');
  let selectedId = $state<number | null>(null);
  /** the fixtures picked: the one being edited, or several to set up together */
  let selection = $state<number[]>([]);
  /** the fixture whose checkbox was clicked last, for shift clicks */
  let anchor: number | null = null;
  let view = $state<View>(rememberedView());
  let search = $state('');
  let expandedType = $state<string | null>(null);
  let located = $state<number[]>([]);
  let confirmDelete = $state(false);

  /** true while several fixtures are being created one after another */
  let adding = $state(false);
  let form = $state({ typeId: '', mode: '', name: '', shortName: '', universeId: 0, address: 1, count: 1,
    limited: false, panMin: 0, panMax: 255, tiltMin: 0, tiltMax: 255 });
  let addressTouched = $state(false);
  // quick setup while patching: off unless chosen on this device before
  const remembered = rememberedChoice();
  let setUp = $state(remembered.enabled);
  let setUpOptions = $state(remembered.options);
  let quickSetupOpen = $state(false);
  let quickSetupFixtures = $state<number[]>([]);
  let severalName = $state('');
  /** names of the party pages, to choose where quick setup puts its rows */
  let partyPages = $state<string[]>([]);
  let severalRunning = $state(false);

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
  const formAbilities = $derived(abilitiesOf([formMode]));
  /** every fixture in patch order, for quick setup of existing fixtures */
  const patchOrder = $derived(
    [...fixtures].sort((a, b) => (universeNumber.get(a.universeId) ?? 0) - (universeNumber.get(b.universeId) ?? 0)
      || a.address - b.address || a.id - b.id),
  );
  /** what the checkboxes and the grid show as picked */
  const ticked = $derived(panel === 'edit' || panel === 'several' ? selection : []);
  const tickedHere = $derived(universeFixtures.filter((f) => ticked.includes(f.id)).length);
  const selectedFixtures = $derived(patchOrder.filter((f) => selection.includes(f.id)));
  const selectionType = $derived.by(() => {
    const typeId = commonType(selectedFixtures);
    return typeId === null ? null : (app.fixtureType(typeId) ?? null);
  });
  const selectionModes = $derived([...new Set(selectedFixtures.map((f) => f.mode))]);
  const selectionAbilities = $derived(abilitiesOf(selectedFixtures.map(modeOf)));
  const selectionOptions = $derived(fitting(setUpOptions, selectionAbilities, partyPages.length));
  const universeNumber = $derived(new Map(universes.map((u) => [u.id, u.number])));
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

  $effect(() => {
    void live.partyMatrixRevision;
    if (projectId !== null) loadPartyPages(projectId);
  });

  async function loadPartyPages(projectId: number) {
    try {
      partyPages = (await api.partyMatrix.get(projectId)).pages.map((p) => p.name);
    } catch (e) {
      showError(e);
    }
  }

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
        closePanel();
      } else if (panel === 'several') {
        // fixtures removed meanwhile (here or by another client) leave the selection
        const left = selection.filter((id) => f.some((x) => x.id === id));
        if (left.length !== selection.length) setSelection(left);
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
      limited: false, panMin: 0, panMax: 255, tiltMin: 0, tiltMax: 255,
    };
    addressTouched = false;
    selectedId = null;
    selection = [];
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
    const options = fitting(setUpOptions, formAbilities, partyPages.length);
    const quickSetup = setUp && makesSomething(options);
    const name = form.name;
    rememberChoice(setUp, setUpOptions);
    adding = true;
    try {
      const created = [];
      for (const fixture of planned) {
        created.push(await api.fixtures.create(projectId, fixture));
      }
      notify(planned.length === 1 ? `Patched "${planned[0].name}"` : `Patched ${planned.length} fixtures`);
      panel = 'list';
      universeId = targetUniverse;
      if (quickSetup) {
        const result = await api.quickSetup(projectId, quickSetupRequest(created.map((f) => f.id), name, options));
        notify(resultSummary(result));
        result.notes.forEach((note) => notify(note));
      }
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
    selection = [id];
    universeId = fixture.universeId;
    form = {
      typeId: fixture.fixtureTypeId,
      mode: fixture.mode,
      name: fixture.name,
      shortName: fixture.shortName ?? '',
      universeId: fixture.universeId,
      address: fixture.address,
      count: 1,
      limited: fixture.limits !== null,
      panMin: fixture.limits?.panMin ?? 0,
      panMax: fixture.limits?.panMax ?? 255,
      tiltMin: fixture.limits?.tiltMin ?? 0,
      tiltMax: fixture.limits?.tiltMax ?? 255,
    };
    confirmDelete = false;
    panel = 'edit';
  }

  /** A click on a fixture; with shift (or cmd/ctrl) it joins the selection or leaves it. */
  function pick(id: number, additive: boolean) {
    if (additive) setSelection(toggled(ticked, id));
    else select(id);
  }

  /** A click on a checkbox of the list; with shift every fixture from the one clicked before follows. */
  function tick(event: MouseEvent, id: number) {
    const order = universeFixtures.map((f) => f.id);
    setSelection(event.shiftKey ? rangeToggled(ticked, order, anchor, id) : toggled(ticked, id));
    anchor = id;
  }

  /** The checkbox above the list: all fixtures of this universe, or none of them. */
  function tickAll() {
    const here = universeFixtures.map((f) => f.id);
    const all = here.every((id) => ticked.includes(id));
    setSelection(all ? ticked.filter((id) => !here.includes(id)) : [...ticked, ...here.filter((id) => !ticked.includes(id))]);
  }

  /** One fixture opens its form, several the panel to set them up together. */
  function setSelection(ids: number[]) {
    if (ids.length === 0) {
      closePanel();
    } else if (ids.length === 1) {
      select(ids[0]);
    } else {
      if (panel !== 'several') severalName = '';
      selection = ids;
      selectedId = null;
      confirmDelete = false;
      panel = 'several';
    }
  }

  function closePanel() {
    panel = 'list';
    selectedId = null;
    selection = [];
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
        stageShape: selected.stageShape,
        limits: form.limited ? { panMin: form.panMin, panMax: form.panMax, tiltMin: form.tiltMin,
          tiltMax: form.tiltMax } : null,
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
    // the live reload after the delete can drop the fixture from the list before the request returns
    const fixture = selected;
    try {
      await api.fixtures.remove(project.id, fixture.id);
      located = located.filter((id) => id !== fixture.id);
      notify(`Removed "${fixture.name}"`);
      closePanel();
      await loadAll(project.id);
    } catch (e) {
      showError(e);
    }
  }

  // --- several fixtures --------------------------------------------------------------------------------------

  async function setUpSeveral() {
    if (projectId === null) return;
    severalRunning = true;
    try {
      const ids = selectedFixtures.map((f) => f.id);
      const result = await api.quickSetup(projectId, quickSetupRequest(ids, severalName, selectionOptions));
      notify(resultSummary(result));
      result.notes.forEach((note) => notify(note));
      rememberChoice(setUp, setUpOptions);
    } catch (e) {
      showError(e);
    } finally {
      severalRunning = false;
    }
  }

  const allLocated = $derived(selectedFixtures.length > 0 && selectedFixtures.every((f) => located.includes(f.id)));

  async function locateSeveral() {
    const lit = allLocated;
    for (const fixture of selectedFixtures) {
      if (located.includes(fixture.id) === lit) await toggleLocate(fixture);
    }
  }

  function setView(next: View) {
    view = next;
    try {
      localStorage.setItem(VIEW_KEY, next);
    } catch {
      // only a convenience
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

  function modeOf(fixture: Fixture) {
    return app.fixtureType(fixture.fixtureTypeId)?.modes.find((m) => m.name === fixture.mode);
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

  /** The address with the universe in front when there are several, e.g. "2 · 17–20". */
  function where(fixture: Fixture): string {
    return universes.length > 1 ? `${universeNumber.get(fixture.universeId) ?? '?'} · ${range(fixture)}` : range(fixture);
  }

  const additive = (e: MouseEvent) => e.shiftKey || e.metaKey || e.ctrlKey;
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
        <div class="grid-head">
          <div class="tabs" role="tablist">
            {#each universes as universe (universe.id)}
              <button type="button" role="tab" aria-selected={universe.id === universeId}
                class:active={universe.id === universeId} onclick={() => (universeId = universe.id)}>
                <span class="mono">{universe.number}</span> {universe.name ?? 'Universe'}
                <span class="count">{fixtures.filter((f) => f.universeId === universe.id).length}</span>
              </button>
            {/each}
          </div>
          <div class="views" role="group" aria-label="View">
            <button type="button" class:active={view === 'channels'} aria-pressed={view === 'channels'}
              onclick={() => setView('channels')}>Channels</button>
            <button type="button" class:active={view === 'list'} aria-pressed={view === 'list'}
              onclick={() => setView('list')}>List</button>
          </div>
        </div>
        {#if view === 'channels'}
          <AddressGrid fixtures={universeFixtures} selectedIds={ticked} {preview} onSelectFixture={pick} {onSelectAddress} />
          <p class="legend dim">
            {#if panel === 'add' || panel === 'edit'}Click a free channel to place the fixture there. {/if}Click a
            fixture to edit it, shift-click to select several.
          </p>
        {:else}
          <table class="fixture-table">
            <thead>
              <tr>
                <th class="tick">
                  <input type="checkbox" aria-label="Select all fixtures in this universe"
                    disabled={universeFixtures.length === 0}
                    checked={universeFixtures.length > 0 && tickedHere === universeFixtures.length}
                    {@attach (box: HTMLInputElement) => { box.indeterminate = tickedHere > 0 && tickedHere < universeFixtures.length; }}
                    onclick={tickAll} />
                </th>
                <th>Name</th>
                <th>Type · mode</th>
                <th class="num">Address</th>
              </tr>
            </thead>
            <tbody>
              {#each universeFixtures as fixture (fixture.id)}
                <tr class:picked={ticked.includes(fixture.id)} style="--hue: {fixtureHue(fixture.id)}">
                  <td class="tick">
                    <input type="checkbox" aria-label="Select {fixture.name}" checked={ticked.includes(fixture.id)}
                      onclick={(e) => tick(e, fixture.id)} />
                  </td>
                  <td>
                    <button type="button" class="row-name" onclick={(e) => pick(fixture.id, additive(e))}>
                      <span class="swatch" class:lit={located.includes(fixture.id)}></span>
                      {fixture.name}
                      {#if fixture.shortName}<span class="dim mono">{fixture.shortName}</span>{/if}
                      {#if issuesByFixture.has(fixture.id)}<span class="badge error">!</span>{/if}
                    </button>
                  </td>
                  <td class="dim">{typeLabel(fixture)}</td>
                  <td class="num mono">{range(fixture)}</td>
                </tr>
              {:else}
                <tr><td colspan="4" class="dim">Nothing patched here yet.</td></tr>
              {/each}
            </tbody>
          </table>
          <p class="legend dim">
            Click a name to edit the fixture. Tick several to set them up together – shift-click ticks a whole range.
          </p>
        {/if}
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
        <div class="list-head">
          <h3>Fixtures in this universe</h3>
          <button type="button" class="btn small" disabled={fixtures.length === 0}
            title="Looks, moves and party fields for fixtures that are already patched"
            onclick={() => { quickSetupFixtures = []; quickSetupOpen = true; }}>
            Quick setup…</button>
        </div>
        {#if view === 'list'}
          <p class="dim hint-text">
            Tick fixtures of one type in the list to set them up together: looks, moves and party fields that switch
            all of them at once.
          </p>
        {:else if universeFixtures.length === 0}
          <p class="dim">Nothing patched here yet.</p>
        {:else}
          <ul class="fixtures">
            {#each universeFixtures as fixture (fixture.id)}
              <li>
                <button type="button" class="fixture" onclick={(e) => pick(fixture.id, additive(e))}
                  style="--hue: {fixtureHue(fixture.id)}">
                  <span class="swatch" class:lit={located.includes(fixture.id)}></span>
                  <span class="fx-name">{fixture.name}</span>
                  <span class="mono dim">{range(fixture)}</span>
                  {#if issuesByFixture.has(fixture.id)}<span class="badge error">!</span>{/if}
                </button>
              </li>
            {/each}
          </ul>
        {/if}
      {:else if panel === 'several'}
        <div class="several">
          <div class="list-head">
            <h3>{selectedFixtures.length} fixtures selected</h3>
            <button type="button" class="btn small ghost" onclick={closePanel}>Clear</button>
          </div>
          <ul class="chosen">
            {#each selectedFixtures as fixture (fixture.id)}
              <li style="--hue: {fixtureHue(fixture.id)}">
                <span class="swatch" class:lit={located.includes(fixture.id)}></span>
                <span class="fx-name">{fixture.name}</span>
                <span class="mono dim">{where(fixture)}</span>
                <button type="button" class="unpick" title="Take out of the selection"
                  aria-label="Take {fixture.name} out of the selection"
                  onclick={() => setSelection(selection.filter((id) => id !== fixture.id))}>✕</button>
              </li>
            {/each}
          </ul>

          {#if selectionType}
            <div class="form-head">
              <FixtureIcon category={selectionType.category} size={28} />
              <div>
                <h2>{selectionType.model}</h2>
                <span class="dim small">{selectionType.manufacturer} · {selectionModes.join(', ')}</span>
              </div>
            </div>
            <fieldset class="limits">
              <legend>Quick setup</legend>
              <label class="field">Name<input bind:value={severalName} placeholder="From the fixture names"
                maxlength="60" /></label>
              <QuickSetupOptions bind:options={setUpOptions} abilities={selectionAbilities} pages={partyPages} />
              <p class="hint dim">
                For all {selectedFixtures.length} fixtures together: each look sets all of them, and one party button
                switches them all.
              </p>
              <div>
                <button class="btn primary" type="button" onclick={setUpSeveral}
                  disabled={severalRunning || !makesSomething(selectionOptions)}>
                  Set up {selectedFixtures.length} fixtures</button>
              </div>
            </fieldset>
          {:else}
            <p class="hint-text">
              These are different fixture types, so there is no quick setup here. The quick setup dialog can still put
              them together.
            </p>
          {/if}

          <div class="form-actions">
            <button class="btn" type="button" class:locating={allLocated} onclick={locateSeveral}>
              {allLocated ? 'Stop locate' : 'Locate all'}</button>
            {#if !selectionType}
              <button class="btn" type="button"
                onclick={() => { quickSetupFixtures = [...selection]; quickSetupOpen = true; }}>Quick setup…</button>
            {/if}
          </div>
        </div>
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

          {#if panel === 'add'}
            <fieldset class="limits">
              <legend>
                <label class="check"><input type="checkbox" bind:checked={setUp} /> Quick setup</label>
              </legend>
              {#if setUp}
                <QuickSetupOptions bind:options={setUpOptions} abilities={formAbilities} pages={partyPages} />
                <p class="hint dim">
                  For all {form.count > 1 ? `${form.count} fixtures together` : 'of them'}: looks, a composition for
                  each, and the party fields that switch them.
                </p>
              {:else}
                <p class="hint dim">Ready-made looks, moves and party fields for the new fixtures – or set them up by hand.</p>
              {/if}
            </fieldset>
          {/if}

          {#if panel === 'edit' && canMove(formMode)}
            <fieldset class="limits">
              <legend>
                <label class="check"><input type="checkbox" bind:checked={form.limited} /> Limit pan and tilt</label>
              </legend>
              <div class="row">
                <label class="field">Pan min<input type="number" min="0" max="255" bind:value={form.panMin}
                  disabled={!form.limited} /></label>
                <label class="field">Pan max<input type="number" min="0" max="255" bind:value={form.panMax}
                  disabled={!form.limited} /></label>
              </div>
              <div class="row">
                <label class="field">Tilt min<input type="number" min="0" max="255" bind:value={form.tiltMin}
                  disabled={!form.limited} /></label>
                <label class="field">Tilt max<input type="number" min="0" max="255" bind:value={form.tiltMax}
                  disabled={!form.limited} /></label>
              </div>
              <p class="hint dim">
                DMX values. Scenes, party and keys stay within them – e.g. never into the audience. The programmer can
                reach every point while setting up.
              </p>
            </fieldset>
          {/if}

          <div class="form-actions">
            {#if panel === 'add'}
              <button class="btn primary" type="submit" disabled={!formMode || adding}>Patch {form.count > 1 ? `${form.count} fixtures` : 'fixture'}</button>
            {:else}
              <button class="btn primary" type="submit">Save</button>
              {#if selected}
                <button class="btn" type="button" class:locating={located.includes(selected.id)} onclick={() => selected && toggleLocate(selected)}>
                  {located.includes(selected.id) ? 'Stop locate' : 'Locate'}
                </button>
                <button class="btn" type="button"
                  onclick={() => { quickSetupFixtures = selected ? [selected.id] : []; quickSetupOpen = true; }}>
                  Quick setup…</button>
              {/if}
            {/if}
            <button class="btn ghost" type="button" onclick={closePanel}>Cancel</button>
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
{#if projectId !== null}
  <QuickSetupModal bind:open={quickSetupOpen} {projectId} fixtures={patchOrder} preselected={quickSetupFixtures}
    pages={partyPages} />
{/if}

<style>
  .limits {
    display: flex;
    flex-direction: column;
    gap: 8px;
    margin: 0;
    padding: 8px 12px 12px;
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
  }

  .grid-head {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
  }

  .views {
    display: flex;
    border: 1px solid var(--border-strong);
    border-radius: 999px;
    overflow: hidden;
  }

  .views button {
    min-height: 30px;
    padding: 0 14px;
    border: 0;
    background: transparent;
    color: var(--text-dim);
    font: inherit;
    font-size: 13px;
    cursor: pointer;
  }

  .views button.active {
    background: var(--accent-soft);
    color: var(--accent);
  }

  .fixture-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 13px;
  }

  .fixture-table th {
    padding: 6px 8px;
    border-bottom: 1px solid var(--border-strong);
    color: var(--text-faint);
    font-size: 11px;
    font-weight: 600;
    letter-spacing: 0.08em;
    text-align: left;
    text-transform: uppercase;
  }

  .fixture-table td {
    padding: 2px 8px;
    border-bottom: 1px solid var(--border);
  }

  .fixture-table .tick {
    width: 28px;
  }

  .fixture-table .num {
    text-align: right;
    white-space: nowrap;
  }

  .fixture-table tr.picked td {
    background: var(--accent-soft);
  }

  .row-name {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
    padding: 6px 0;
    border: 0;
    background: transparent;
    color: var(--text);
    font: inherit;
    text-align: left;
    cursor: pointer;
  }

  .row-name:hover {
    color: var(--accent);
  }

  .hint-text {
    margin: 0;
    font-size: 13px;
  }

  .several {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .chosen {
    display: flex;
    flex-direction: column;
    gap: 2px;
    max-height: 220px;
    margin: 0;
    padding: 0;
    overflow-y: auto;
    list-style: none;
  }

  .chosen li {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 4px 4px 4px 10px;
    font-size: 13px;
  }

  .unpick {
    padding: 2px 6px;
    border: 0;
    background: transparent;
    color: var(--text-faint);
    font: inherit;
    cursor: pointer;
  }

  .unpick:hover {
    color: var(--danger);
  }

  .list-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
  }

  .limits .hint {
    margin: 0;
    font-size: 12px;
  }

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
