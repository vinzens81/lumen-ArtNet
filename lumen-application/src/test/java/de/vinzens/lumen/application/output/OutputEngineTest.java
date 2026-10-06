package de.vinzens.lumen.application.output;

import de.vinzens.lumen.application.ConflictException;
import de.vinzens.lumen.application.NotFoundException;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.library.port.in.QueryFixtureLibraryUseCase;
import de.vinzens.lumen.application.project.port.out.FixtureRepository;
import de.vinzens.lumen.application.project.port.out.GroupRepository;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.application.project.port.out.UniverseRepository;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelDefinition;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.library.FixtureCategory;
import de.vinzens.lumen.domain.library.FixtureType;
import de.vinzens.lumen.domain.library.LibraryIssue;
import de.vinzens.lumen.domain.library.Mode;
import de.vinzens.lumen.domain.library.PhysicalProperties;
import de.vinzens.lumen.domain.output.DmxFrame;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.output.LayerOutput;
import de.vinzens.lumen.domain.output.OperatingMode;
import de.vinzens.lumen.domain.project.ArtNetAddress;
import de.vinzens.lumen.domain.project.ArtNetTarget;
import de.vinzens.lumen.domain.project.FixtureSpec;
import de.vinzens.lumen.domain.project.Group;
import de.vinzens.lumen.domain.project.GroupSpec;
import de.vinzens.lumen.domain.project.PatchedFixture;
import de.vinzens.lumen.domain.project.Project;
import de.vinzens.lumen.domain.project.ProjectSpec;
import de.vinzens.lumen.domain.project.Universe;
import de.vinzens.lumen.domain.project.UniverseSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutputEngineTest {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	private static final Attribute RED = Attribute.of(ChannelFunction.RED);

	private static final Attribute PAN = Attribute.of(ChannelFunction.PAN);

	private static final FixtureType PAR = new FixtureType("par", "Generic", "Par", FixtureCategory.PAR,
			PhysicalProperties.NONE, List.of(new Mode("2ch", List.of(
					channel(ChannelFunction.DIMMER), channel(ChannelFunction.RED)))));
	private static final FixtureType HEAD = new FixtureType("head", "Generic", "Head", FixtureCategory.MOVING_HEAD,
			PhysicalProperties.NONE, List.of(new Mode("2ch", List.of(
					channel(ChannelFunction.PAN), channel(ChannelFunction.DIMMER)))));

	/** a dimmer pack: four channels as heads, no dimmer for the whole fixture */
	private static final FixtureType PACK = new FixtureType("pack", "Generic", "Dimmer pack", FixtureCategory.DIMMER,
			PhysicalProperties.NONE, List.of(new Mode("4ch", List.of(headDimmer(1), headDimmer(2), headDimmer(3),
					headDimmer(4)))));

	private final List<Project> projects = new ArrayList<>();
	private final List<Universe> universes = new ArrayList<>();
	private final List<PatchedFixture> fixtures = new ArrayList<>();
	private final List<Group> groups = new ArrayList<>();
	private final List<Sent> sent = new ArrayList<>();
	private final List<StateTopic> published = new ArrayList<>();
	private OutputEngine engine;

	@BeforeEach
	void setUp() {
		projects.add(project(1, true));
		universes.add(universe(10, 1, 1));
		universes.add(universe(11, 1, 2));
		fixtures.add(new PatchedFixture(100, 1, new FixtureSpec("PAR", Optional.empty(), "par", "2ch", 10, 5,
				Optional.empty(), 0)));
		fixtures.add(new PatchedFixture(101, 1, new FixtureSpec("Head", Optional.empty(), "head", "2ch", 10, 20,
				Optional.empty(), 0)));
		fixtures.add(new PatchedFixture(102, 1, new FixtureSpec("Pack", Optional.empty(), "pack", "4ch", 11, 1,
				Optional.empty(), 0)));
		groups.add(new Group(7, 1, new GroupSpec("All", Optional.empty(), 0, List.of(100L, 101L))));
		var events = new StateEvents();
		events.subscribe(published::add);
		engine = new OutputEngine(projectRepository(), universeRepository(), fixtureRepository(), groupRepository(),
				library(), (universe, frame) -> sent.add(new Sent(universe.spec().number(), frame)), events,
				Clock.systemUTC());
		engine.patchChanged();
		published.clear();
	}

	@Test
	void tickSendsEveryUniverseOfTheActiveProject() {
		engine.setValues(100, new FixtureValues(Map.of(DIMMER, 1.0, RED, 0.5)));

		engine.tick();

		assertThat(sent).extracting(Sent::number).containsExactly(1, 2);
		assertThat(sent.getFirst().frame().get(5)).isEqualTo(255);
		assertThat(sent.getFirst().frame().get(6)).isEqualTo(128);
		assertThat(sent.get(1).frame()).isEqualTo(DmxFrame.BLACK);
	}

	@Test
	void remembersWhatTheLastTickSentForLiveViews() {
		assertThat(engine.outputValues()).isEmpty();
		assertThat(engine.lastFrame(1)).isEmpty();
		engine.setValues(100, new FixtureValues(Map.of(DIMMER, 1.0)));
		assertThat(engine.outputValues()).as("not sent yet").isEmpty();

		engine.tick();

		assertThat(engine.outputValues().get(100L).get(DIMMER)).contains(1.0);
		assertThat(engine.lastFrame(1)).hasValueSatisfying(frame -> assertThat(frame.get(5)).isEqualTo(255));
		assertThat(engine.lastFrame(9)).isEmpty();
	}

	@Test
	void selectionOnlyReceivesSupportedAttributes() {
		var result = engine.setValues(List.of(100L, 101L), new FixtureValues(Map.of(DIMMER, 1.0, RED, 1.0, PAN, 0.5)));

		assertThat(result.get(100L).values()).containsOnlyKeys(DIMMER, RED);
		assertThat(result.get(101L).values()).containsOnlyKeys(DIMMER, PAN);
		assertThat(published).containsExactly(StateTopic.PROGRAMMER);
	}

	@Test
	void aDimmerPackKeepsEveryChannelInsteadOfAMasterItDoesNotHave() {
		engine.setValues(102, new FixtureValues(Map.of(DIMMER.withHead(2), 0.3)));
		var values = engine.setValues(List.of(102L), new FixtureValues(Map.of(DIMMER, 1.0))).get(102L);

		assertThat(values.values()).as("no hidden master, every channel set").containsOnly(
				Map.entry(DIMMER.withHead(1), 1.0), Map.entry(DIMMER.withHead(2), 1.0),
				Map.entry(DIMMER.withHead(3), 1.0), Map.entry(DIMMER.withHead(4), 1.0));
	}

	@Test
	void attributeNoFixtureHasIsRejected() {
		assertThatThrownBy(() -> engine.setValues(List.of(100L), new FixtureValues(Map.of(PAN, 0.5))))
				.isInstanceOf(DomainValidationException.class)
				.hasMessageContaining("[pan]");
	}

	@Test
	void groupValuesGoToAllMembers() {
		engine.setGroupValues(7, new FixtureValues(Map.of(DIMMER, 0.5)));

		assertThat(engine.programmerValues()).containsOnlyKeys(100L, 101L);
		assertThatThrownBy(() -> engine.setGroupValues(8, FixtureValues.EMPTY)).isInstanceOf(NotFoundException.class);
	}

	@Test
	void grandMasterAndBlackoutScaleTheOutput() {
		engine.setValues(100, new FixtureValues(Map.of(DIMMER, 1.0, RED, 1.0)));

		engine.setMasterLevel(0.5);
		assertThat(engine.frame(1).get(5)).isEqualTo(128);
		assertThat(engine.frame(1).get(6)).as("color is not scaled").isEqualTo(255);

		engine.setBlackout(true);
		assertThat(engine.frame(1).get(5)).isZero();
		assertThat(engine.master().level()).isEqualTo(0.5);
		assertThat(published).endsWith(StateTopic.OUTPUT, StateTopic.OUTPUT);
	}

	@Test
	void showModeClearsAndLocksTheProgrammer() {
		engine.setValues(100, new FixtureValues(Map.of(DIMMER, 1.0)));

		engine.setMode(OperatingMode.SHOW);

		assertThat(engine.programmerValues()).isEmpty();
		assertThat(published).endsWith(StateTopic.MODE);
		assertThatThrownBy(() -> engine.setValues(100, new FixtureValues(Map.of(DIMMER, 1.0))))
				.isInstanceOf(ConflictException.class);
		engine.setMode(OperatingMode.SETUP);
		engine.setValues(100, new FixtureValues(Map.of(DIMMER, 1.0)));
	}

	@Test
	void showAndPartyModeAreOnlyReachedFromSetup() {
		engine.setMode(OperatingMode.SHOW);
		assertThatThrownBy(() -> engine.setMode(OperatingMode.PARTY)).isInstanceOf(ConflictException.class);
		assertThat(engine.mode()).isEqualTo(OperatingMode.SHOW);

		engine.setMode(OperatingMode.SETUP);
		engine.setMode(OperatingMode.PARTY);
		assertThatThrownBy(() -> engine.setMode(OperatingMode.SHOW)).isInstanceOf(ConflictException.class);
		assertThat(engine.setMode(OperatingMode.PARTY)).as("staying is fine").isEqualTo(OperatingMode.PARTY);
	}

	@Test
	void layerSourcesAreMergedBelowTheProgrammer() {
		engine.addLayerSource((now, modes) -> new LayerOutput(50,
				Map.of(100L, new FixtureValues(Map.of(DIMMER, 0.5, RED, 1.0)))));
		engine.setValues(100, new FixtureValues(Map.of(DIMMER, 0.2, RED, 0.0)));

		var frame = engine.frame(1);
		assertThat(frame.get(5)).as("dimmer HTP: max(0.5, 0.2)").isEqualTo(128);
		assertThat(frame.get(6)).as("red LTP: programmer wins").isZero();
	}

	@Test
	void frameIsRenderedForTheUniverseNumber() {
		engine.setValues(100, new FixtureValues(Map.of(DIMMER, 1.0)));

		assertThat(engine.frame(1).get(5)).isEqualTo(255);
		assertThatThrownBy(() -> engine.frame(3)).isInstanceOf(NotFoundException.class);
	}

	@Test
	void rejectsAttributesTheFixtureDoesNotHave() {
		assertThatThrownBy(() -> engine.setValues(100, new FixtureValues(Map.of(Attribute.of(ChannelFunction.PAN), 1.0))))
				.isInstanceOf(DomainValidationException.class)
				.hasMessageContaining("[pan]");
	}

	@Test
	void unknownFixtureIsNotFound() {
		assertThatThrownBy(() -> engine.setValues(999, new FixtureValues(Map.of(DIMMER, 1.0))))
				.isInstanceOf(NotFoundException.class);
	}

	@Test
	void switchingProjectsClearsTheProgrammer() {
		engine.setValues(100, new FixtureValues(Map.of(DIMMER, 1.0)));
		projects.set(0, project(1, false));
		projects.add(project(2, true));

		engine.patchChanged();
		engine.tick();

		assertThat(engine.programmerValues()).isEmpty();
		assertThat(sent).isEmpty();
	}

	private record Sent(int number, DmxFrame frame) {
	}

	private static Project project(long id, boolean active) {
		var now = Instant.EPOCH;
		return new Project(id, new ProjectSpec("P" + id, Optional.empty(), Duration.ZERO, Duration.ZERO), active, now,
				now);
	}

	private static Universe universe(long id, long projectId, int number) {
		return new Universe(id, projectId, new UniverseSpec(number, Optional.empty(), new ArtNetAddress(0, 0, number - 1),
				new ArtNetTarget.Broadcast()));
	}

	private static ChannelDefinition headDimmer(int head) {
		return new ChannelDefinition(ChannelFunction.DIMMER, Optional.empty(), 8, 0, false, List.of(), Optional.empty(),
				head);
	}

	private static ChannelDefinition channel(ChannelFunction function) {
		return new ChannelDefinition(function, Optional.empty(), 8, 0, false, List.of(), Optional.empty());
	}

	private QueryFixtureLibraryUseCase library() {
		return new QueryFixtureLibraryUseCase() {
			@Override
			public List<FixtureType> fixtureTypes() {
				return List.of(PAR);
			}

			@Override
			public Optional<FixtureType> fixtureType(String id) {
				return List.of(PAR, HEAD, PACK).stream().filter(type -> type.id().equals(id)).findFirst();
			}

			@Override
			public List<LibraryIssue> issues() {
				return List.of();
			}
		};
	}

	private ProjectRepository projectRepository() {
		return new ProjectRepository() {
			@Override
			public List<Project> findAll() {
				return List.copyOf(projects);
			}

			@Override
			public Optional<Project> findById(long id) {
				return projects.stream().filter(p -> p.id() == id).findFirst();
			}

			@Override
			public Project create(ProjectSpec spec, Instant now) {
				throw new UnsupportedOperationException();
			}

			@Override
			public Project update(long id, ProjectSpec spec, Instant now) {
				throw new UnsupportedOperationException();
			}

			@Override
			public void delete(long id) {
				throw new UnsupportedOperationException();
			}

			@Override
			public void activate(long id) {
				throw new UnsupportedOperationException();
			}
		};
	}

	private UniverseRepository universeRepository() {
		return new UniverseRepository() {
			@Override
			public List<Universe> findByProject(long projectId) {
				return universes.stream().filter(u -> u.projectId() == projectId).toList();
			}

			@Override
			public Optional<Universe> findById(long id) {
				return universes.stream().filter(u -> u.id() == id).findFirst();
			}

			@Override
			public Universe create(long projectId, UniverseSpec spec) {
				throw new UnsupportedOperationException();
			}

			@Override
			public Universe update(long id, UniverseSpec spec) {
				throw new UnsupportedOperationException();
			}

			@Override
			public void delete(long id) {
				throw new UnsupportedOperationException();
			}
		};
	}

	private GroupRepository groupRepository() {
		return new GroupRepository() {
			@Override
			public List<Group> findByProject(long projectId) {
				return groups.stream().filter(g -> g.projectId() == projectId).toList();
			}

			@Override
			public Optional<Group> findById(long id) {
				return groups.stream().filter(g -> g.id() == id).findFirst();
			}

			@Override
			public Group create(long projectId, GroupSpec spec) {
				throw new UnsupportedOperationException();
			}

			@Override
			public Group update(long id, GroupSpec spec) {
				throw new UnsupportedOperationException();
			}

			@Override
			public void delete(long id) {
				throw new UnsupportedOperationException();
			}
		};
	}

	private FixtureRepository fixtureRepository() {
		return new FixtureRepository() {
			@Override
			public List<PatchedFixture> findByProject(long projectId) {
				return fixtures.stream().filter(f -> f.projectId() == projectId).toList();
			}

			@Override
			public Optional<PatchedFixture> findById(long id) {
				return fixtures.stream().filter(f -> f.id() == id).findFirst();
			}

			@Override
			public PatchedFixture create(long projectId, FixtureSpec spec) {
				throw new UnsupportedOperationException();
			}

			@Override
			public PatchedFixture update(long id, FixtureSpec spec) {
				throw new UnsupportedOperationException();
			}

			@Override
			public void delete(long id) {
				throw new UnsupportedOperationException();
			}

			@Override
			public int countByUniverse(long universeId) {
				throw new UnsupportedOperationException();
			}
		};
	}
}
