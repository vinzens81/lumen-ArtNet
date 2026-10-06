package de.vinzens.lumen.application.output;

import de.vinzens.lumen.application.ConflictException;
import de.vinzens.lumen.application.NotFoundException;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.library.port.in.QueryFixtureLibraryUseCase;
import de.vinzens.lumen.application.output.port.in.ControlMasterUseCase;
import de.vinzens.lumen.application.output.port.in.ControlModeUseCase;
import de.vinzens.lumen.application.output.port.in.ControlProgrammerUseCase;
import de.vinzens.lumen.application.output.port.in.QueryOutputUseCase;
import de.vinzens.lumen.application.output.port.out.DmxOutputPort;
import de.vinzens.lumen.application.project.port.out.FixtureRepository;
import de.vinzens.lumen.application.project.port.out.GroupRepository;
import de.vinzens.lumen.application.project.port.out.PatchChangeListener;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.application.project.port.out.UniverseRepository;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.Mode;
import de.vinzens.lumen.domain.output.DmxFrame;
import de.vinzens.lumen.domain.output.DmxRenderer;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.output.LayerMerger;
import de.vinzens.lumen.domain.output.LayerOutput;
import de.vinzens.lumen.domain.output.MasterState;
import de.vinzens.lumen.domain.output.OperatingMode;
import de.vinzens.lumen.domain.output.PresetRefs;
import de.vinzens.lumen.domain.output.Programmer;
import de.vinzens.lumen.domain.output.RenderFixture;
import de.vinzens.lumen.domain.preset.Preset;
import de.vinzens.lumen.domain.project.Project;
import de.vinzens.lumen.domain.project.Universe;
import de.vinzens.lumen.domain.look.Look;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Computes the DMX output of the active project from all output layers and sends it on every tick.
 * State is kept in immutable snapshots, so REST calls and the render loop never block each other.
 */
public class OutputEngine
		implements ControlProgrammerUseCase, ControlMasterUseCase, ControlModeUseCase, QueryOutputUseCase,
		PatchChangeListener {

	static final int PROGRAMMER_PRIORITY = 100;

	private final ProjectRepository projects;
	private final UniverseRepository universes;
	private final FixtureRepository fixtures;
	private final GroupRepository groups;
	private final QueryFixtureLibraryUseCase library;
	private final DmxOutputPort output;
	private final StateEvents events;
	private final Clock clock;
	private final List<OutputLayerSource> layerSources = new CopyOnWriteArrayList<>();

	private final AtomicReference<ActivePatch> patch = new AtomicReference<>(ActivePatch.NONE);
	private final AtomicReference<Programmer> programmer = new AtomicReference<>(Programmer.EMPTY);
	private final AtomicReference<MasterState> master = new AtomicReference<>(MasterState.FULL);
	private final AtomicReference<OperatingMode> mode = new AtomicReference<>(OperatingMode.SETUP);
	/** What the last tick sent, for live views (stage, DMX monitor). */
	private final AtomicReference<Rendered> lastSent = new AtomicReference<>(Rendered.NOTHING);

	public OutputEngine(ProjectRepository projects, UniverseRepository universes, FixtureRepository fixtures,
			GroupRepository groups, QueryFixtureLibraryUseCase library, DmxOutputPort output, StateEvents events,
			Clock clock) {
		this.projects = Objects.requireNonNull(projects, "projects");
		this.universes = Objects.requireNonNull(universes, "universes");
		this.fixtures = Objects.requireNonNull(fixtures, "fixtures");
		this.groups = Objects.requireNonNull(groups, "groups");
		this.library = Objects.requireNonNull(library, "library");
		this.output = Objects.requireNonNull(output, "output");
		this.events = Objects.requireNonNull(events, "events");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/** Adds a layer below the programmer, e.g. the scene playback. */
	public void addLayerSource(OutputLayerSource source) {
		layerSources.add(source);
	}

	/** Reloads the patch of the active project. Switching projects clears the programmer. */
	@Override
	public void patchChanged() {
		var active = projects.findAll().stream().filter(Project::active).findFirst();
		var loaded = active.map(this::load).orElse(ActivePatch.NONE);
		var previous = patch.getAndSet(loaded);
		if (!Objects.equals(previous.projectId(), loaded.projectId())) {
			programmer.set(Programmer.EMPTY);
			events.publish(StateTopic.PROGRAMMER);
		}
		events.publish(StateTopic.PROJECTS);
		events.publish(StateTopic.PATCH);
		events.publish(StateTopic.SCENES);
	}

	private ActivePatch load(Project project) {
		var projectUniverses = universes.findByProject(project.id());
		var renderFixtures = fixtures.findByProject(project.id()).stream()
				.flatMap(fixture -> library.fixtureType(fixture.spec().fixtureTypeId())
						.flatMap(type -> type.mode(fixture.spec().modeName()))
						.map(mode -> new RenderFixture(fixture.id(), fixture.spec().universeId(), fixture.spec().address(),
								mode))
						.stream())
				.toList();
		return new ActivePatch(project.id(), projectUniverses, renderFixtures);
	}

	/** Number of universes of the active project. */
	public int universeCount() {
		return patch.get().universes().size();
	}

	/** Renders all universes of the active project and sends them. Called by the render loop. */
	public void tick() {
		var current = patch.get();
		var rendered = render(current);
		for (var universe : current.universes()) {
			output.send(universe, rendered.frames().get(universe.id()));
		}
		lastSent.set(rendered);
	}

	private Rendered render(ActivePatch current) {
		var now = clock.instant();
		var modes = new HashMap<Long, Mode>();
		current.fixtures().forEach(f -> modes.put(f.fixtureId(), f.mode()));
		var layers = new ArrayList<LayerOutput>();
		var currentMode = mode.get();
		for (var source : layerSources) {
			if (source.activeIn(currentMode)) {
				layers.add(source.layer(now, modes));
			}
		}
		layers.add(programmer.get().asLayer(PROGRAMMER_PRIORITY));
		var values = LayerMerger.merge(layers);
		var universeIds = current.universes().stream().map(Universe::id).toList();
		var frames = DmxRenderer.render(universeIds, current.fixtures(), values, master.get().effectiveLevel());
		return new Rendered(current, values, frames);
	}

	// --- Output ----------------------------------------------------------------------------------------------

	@Override
	public DmxFrame frame(int universeNumber) {
		var current = patch.get();
		var universe = current.universes().stream()
				.filter(u -> u.spec().number() == universeNumber)
				.findFirst()
				.orElseThrow(() -> new NotFoundException("Universe " + universeNumber
						+ " does not exist in the active project"));
		return render(current).frames().get(universe.id());
	}

	@Override
	public Map<Long, FixtureValues> outputValues() {
		return lastSent.get().values();
	}

	@Override
	public Optional<DmxFrame> lastFrame(int universeNumber) {
		var rendered = lastSent.get();
		return rendered.patch().universes().stream()
				.filter(u -> u.spec().number() == universeNumber)
				.findFirst()
				.map(universe -> rendered.frames().get(universe.id()));
	}

	// --- Programmer ------------------------------------------------------------------------------------------

	@Override
	public Map<Long, FixtureValues> programmerValues() {
		return programmer.get().fixtures();
	}

	@Override
	public PresetRefs programmerPresets() {
		return programmer.get().presets();
	}

	@Override
	public FixtureValues setValues(long fixtureId, FixtureValues values) {
		requireSetupMode();
		var fixture = requireFixture(fixtureId);
		var allowed = fixture.mode().attributes();
		var unknown = values.values().keySet().stream()
				.filter(attribute -> !allowed.contains(attribute))
				.map(Attribute::id)
				.sorted()
				.toList();
		if (!unknown.isEmpty()) {
			throw new DomainValidationException(List.of("Fixture " + fixtureId + " has no attributes " + unknown
					+ " (available: " + allowed.stream().map(Attribute::id).toList() + ")"));
		}
		var result = programmer.updateAndGet(p -> p.with(fixtureId, values)).values(fixtureId);
		events.publish(StateTopic.PROGRAMMER);
		return result;
	}

	@Override
	public Map<Long, FixtureValues> setValues(Collection<Long> fixtureIds, FixtureValues values) {
		requireSetupMode();
		var targets = new LinkedHashSet<>(fixtureIds).stream().map(this::requireFixture).toList();
		var perFixture = new HashMap<Long, FixtureValues>();
		var used = new LinkedHashSet<Attribute>();
		for (var fixture : targets) {
			var allowed = fixture.mode().attributes();
			var supported = new HashMap<Attribute, Double>();
			values.values().forEach((attribute, value) -> {
				if (allowed.contains(attribute)) {
					supported.put(attribute, value);
					used.add(attribute);
				}
			});
			perFixture.put(fixture.fixtureId(), new FixtureValues(supported));
		}
		var unused = values.values().keySet().stream()
				.filter(attribute -> !used.contains(attribute))
				.map(Attribute::id)
				.sorted()
				.toList();
		if (!unused.isEmpty() && !targets.isEmpty()) {
			throw new DomainValidationException(List.of("None of the fixtures has the attributes " + unused));
		}

		var updated = programmer.updateAndGet(p -> {
			var next = p;
			for (var entry : perFixture.entrySet()) {
				next = next.with(entry.getKey(), entry.getValue());
			}
			return next;
		});
		events.publish(StateTopic.PROGRAMMER);
		var result = new HashMap<Long, FixtureValues>();
		perFixture.keySet().forEach(id -> result.put(id, updated.values(id)));
		return result;
	}

	@Override
	public Map<Long, FixtureValues> setGroupValues(long groupId, FixtureValues values) {
		var projectId = patch.get().projectId();
		var group = groups.findById(groupId)
				.filter(g -> Objects.equals(g.projectId(), projectId))
				.orElseThrow(() -> new NotFoundException("Group " + groupId + " does not exist in the active project"));
		var members = group.spec().fixtureIds().stream().filter(id -> patch.get().fixture(id).isPresent()).toList();
		return setValues(members, values);
	}

	@Override
	public void loadLook(Look look) {
		requireSetupMode();
		var current = patch.get();
		var loaded = new HashMap<Long, FixtureValues>();
		look.values().forEach((fixtureId, fixtureValues) -> current.fixture(fixtureId).ifPresent(fixture ->
				loaded.put(fixtureId, supported(fixture, fixtureValues))));
		programmer.set(new Programmer(loaded, look.presets()));
		events.publish(StateTopic.PROGRAMMER);
	}

	@Override
	public void applyPreset(Collection<Long> fixtureIds, Preset preset) {
		requireSetupMode();
		var targets = new LinkedHashSet<>(fixtureIds).stream().map(this::requireFixture).toList();
		programmer.updateAndGet(p -> {
			var next = p;
			for (var fixture : targets) {
				var values = preset.values().get(fixture.fixtureId());
				if (values != null) {
					next = next.withPreset(fixture.fixtureId(), supported(fixture, values), preset.spec().group(),
							preset.id());
				}
			}
			return next;
		});
		events.publish(StateTopic.PROGRAMMER);
	}

	@Override
	public void presetChanged(Preset preset) {
		var before = programmer.getAndUpdate(p -> {
			var look = new Look(p.fixtures(), p.presets()).withPreset(preset);
			return new Programmer(look.values(), look.presets());
		});
		if (!before.presets().fixturesUsing(preset.id()).isEmpty()) {
			events.publish(StateTopic.PROGRAMMER);
		}
	}

	@Override
	public void presetDeleted(long presetId) {
		var before = programmer.getAndUpdate(p -> new Programmer(p.fixtures(), p.presets().withoutPreset(presetId)));
		if (!before.presets().fixturesUsing(presetId).isEmpty()) {
			events.publish(StateTopic.PROGRAMMER);
		}
	}

	/** Only the values of attributes the fixture has. */
	private static FixtureValues supported(RenderFixture fixture, FixtureValues values) {
		var allowed = fixture.mode().attributes();
		var supported = new HashMap<Attribute, Double>();
		values.values().forEach((attribute, value) -> {
			if (allowed.contains(attribute)) {
				supported.put(attribute, value);
			}
		});
		return new FixtureValues(supported);
	}

	@Override
	public void clearFixture(long fixtureId) {
		clearFixtures(List.of(fixtureId));
	}

	@Override
	public void clearFixtures(Collection<Long> fixtureIds) {
		fixtureIds.forEach(this::requireFixture);
		programmer.updateAndGet(p -> {
			var next = p;
			for (var id : fixtureIds) {
				next = next.without(id);
			}
			return next;
		});
		events.publish(StateTopic.PROGRAMMER);
	}

	@Override
	public void clear() {
		programmer.set(Programmer.EMPTY);
		events.publish(StateTopic.PROGRAMMER);
	}

	private void requireSetupMode() {
		if (mode.get() != OperatingMode.SETUP) {
			throw new ConflictException("The programmer is locked in " + mode.get().name().toLowerCase(Locale.ROOT)
					+ " mode");
		}
	}

	// --- Mode ------------------------------------------------------------------------------------------------

	@Override
	public OperatingMode mode() {
		return mode.get();
	}

	@Override
	public OperatingMode setMode(OperatingMode newMode) {
		var previous = mode.getAndUpdate(current -> canSwitch(current, newMode) ? newMode : current);
		if (!canSwitch(previous, newMode)) {
			throw new ConflictException("Switch from " + name(previous) + " mode to " + name(newMode)
					+ " mode through setup mode");
		}
		if (previous != newMode) {
			if (newMode != OperatingMode.SETUP) {
				programmer.set(Programmer.EMPTY);
				events.publish(StateTopic.PROGRAMMER);
			}
			events.publish(StateTopic.MODE);
		}
		return newMode;
	}

	/** Show and party mode are only entered from setup mode, never directly from each other. */
	private static boolean canSwitch(OperatingMode from, OperatingMode to) {
		return from == to || from == OperatingMode.SETUP || to == OperatingMode.SETUP;
	}

	private static String name(OperatingMode mode) {
		return mode.name().toLowerCase(Locale.ROOT);
	}

	// --- Master ----------------------------------------------------------------------------------------------

	@Override
	public MasterState master() {
		return master.get();
	}

	@Override
	public MasterState setMasterLevel(double level) {
		var result = master.updateAndGet(m -> m.withLevel(level));
		events.publish(StateTopic.OUTPUT);
		return result;
	}

	@Override
	public MasterState setBlackout(boolean active) {
		var result = master.updateAndGet(m -> m.withBlackout(active));
		events.publish(StateTopic.OUTPUT);
		return result;
	}

	private RenderFixture requireFixture(long fixtureId) {
		return patch.get().fixture(fixtureId).orElseThrow(() -> new NotFoundException("Fixture " + fixtureId
				+ " is not patched in the active project (or its fixture type is missing in the library)"));
	}

	/** @param values merged attribute values before the grand master */
	private record Rendered(ActivePatch patch, Map<Long, FixtureValues> values, Map<Long, DmxFrame> frames) {

		static final Rendered NOTHING = new Rendered(ActivePatch.NONE, Map.of(), Map.of());
	}

	private record ActivePatch(Long projectId, List<Universe> universes, List<RenderFixture> fixtures,
			Map<Long, RenderFixture> fixturesById) {

		static final ActivePatch NONE = new ActivePatch(null, List.of(), List.of());

		ActivePatch(Long projectId, List<Universe> universes, List<RenderFixture> fixtures) {
			this(projectId, List.copyOf(universes), List.copyOf(fixtures), fixtures.stream()
					.collect(Collectors.toUnmodifiableMap(RenderFixture::fixtureId, Function.identity())));
		}

		Optional<RenderFixture> fixture(long fixtureId) {
			return Optional.ofNullable(fixturesById.get(fixtureId));
		}
	}
}
