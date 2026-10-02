package de.vinzens.lumen.application.output;

import de.vinzens.lumen.application.NotFoundException;
import de.vinzens.lumen.application.library.port.in.QueryFixtureLibraryUseCase;
import de.vinzens.lumen.application.output.port.in.ControlProgrammerUseCase;
import de.vinzens.lumen.application.output.port.in.QueryOutputUseCase;
import de.vinzens.lumen.application.output.port.out.DmxOutputPort;
import de.vinzens.lumen.application.project.port.out.FixtureRepository;
import de.vinzens.lumen.application.project.port.out.PatchChangeListener;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.application.project.port.out.UniverseRepository;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.output.DmxFrame;
import de.vinzens.lumen.domain.output.DmxRenderer;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.output.LayerMerger;
import de.vinzens.lumen.domain.output.Programmer;
import de.vinzens.lumen.domain.output.RenderFixture;
import de.vinzens.lumen.domain.project.Project;
import de.vinzens.lumen.domain.project.Universe;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Computes the DMX output of the active project from all output layers and sends it on every tick.
 * State is kept in immutable snapshots, so REST calls and the render loop never block each other.
 */
public class OutputEngine implements ControlProgrammerUseCase, QueryOutputUseCase, PatchChangeListener {

	static final int PROGRAMMER_PRIORITY = 100;

	private final ProjectRepository projects;
	private final UniverseRepository universes;
	private final FixtureRepository fixtures;
	private final QueryFixtureLibraryUseCase library;
	private final DmxOutputPort output;

	private final AtomicReference<ActivePatch> patch = new AtomicReference<>(ActivePatch.NONE);
	private final AtomicReference<Programmer> programmer = new AtomicReference<>(Programmer.EMPTY);

	public OutputEngine(ProjectRepository projects, UniverseRepository universes, FixtureRepository fixtures,
			QueryFixtureLibraryUseCase library, DmxOutputPort output) {
		this.projects = Objects.requireNonNull(projects, "projects");
		this.universes = Objects.requireNonNull(universes, "universes");
		this.fixtures = Objects.requireNonNull(fixtures, "fixtures");
		this.library = Objects.requireNonNull(library, "library");
		this.output = Objects.requireNonNull(output, "output");
	}

	/** Reloads the patch of the active project. Switching projects clears the programmer. */
	@Override
	public void patchChanged() {
		var active = projects.findAll().stream().filter(Project::active).findFirst();
		var loaded = active.map(this::load).orElse(ActivePatch.NONE);
		var previous = patch.getAndSet(loaded);
		if (!Objects.equals(previous.projectId(), loaded.projectId())) {
			programmer.set(Programmer.EMPTY);
		}
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

	/** Renders all universes of the active project and sends them. Called by the render loop. */
	public void tick() {
		var current = patch.get();
		var frames = render(current);
		for (var universe : current.universes()) {
			output.send(universe, frames.get(universe.id()));
		}
	}

	private Map<Long, DmxFrame> render(ActivePatch current) {
		var values = LayerMerger.merge(List.of(programmer.get().asLayer(PROGRAMMER_PRIORITY)));
		var universeIds = current.universes().stream().map(Universe::id).toList();
		return DmxRenderer.render(universeIds, current.fixtures(), values, 1.0);
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
		return render(current).get(universe.id());
	}

	// --- Programmer ------------------------------------------------------------------------------------------

	@Override
	public Map<Long, FixtureValues> programmerValues() {
		return programmer.get().fixtures();
	}

	@Override
	public FixtureValues setValues(long fixtureId, FixtureValues values) {
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
		return programmer.updateAndGet(p -> p.with(fixtureId, values)).values(fixtureId);
	}

	@Override
	public void clearFixture(long fixtureId) {
		requireFixture(fixtureId);
		programmer.updateAndGet(p -> p.without(fixtureId));
	}

	@Override
	public void clear() {
		programmer.set(Programmer.EMPTY);
	}

	private RenderFixture requireFixture(long fixtureId) {
		return patch.get().fixture(fixtureId).orElseThrow(() -> new NotFoundException("Fixture " + fixtureId
				+ " is not patched in the active project (or its fixture type is missing in the library)"));
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
