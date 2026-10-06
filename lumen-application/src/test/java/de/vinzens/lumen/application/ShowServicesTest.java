package de.vinzens.lumen.application;

import de.vinzens.lumen.application.composition.CompositionService;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.library.port.in.QueryFixtureLibraryUseCase;
import de.vinzens.lumen.application.look.LookService;
import de.vinzens.lumen.application.look.port.in.ManageLooksUseCase.RecordOptions;
import de.vinzens.lumen.application.project.port.out.FixtureRepository;
import de.vinzens.lumen.application.project.port.out.GroupRepository;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.application.scene.SceneService;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.composition.Chase;
import de.vinzens.lumen.domain.composition.CompositionSpec;
import de.vinzens.lumen.domain.composition.CompositionStep;
import de.vinzens.lumen.domain.composition.GeneratorSettings;
import de.vinzens.lumen.domain.composition.KeyMode;
import de.vinzens.lumen.domain.composition.Target;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelDefinition;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.library.FixtureCategory;
import de.vinzens.lumen.domain.library.FixtureType;
import de.vinzens.lumen.domain.library.LibraryIssue;
import de.vinzens.lumen.domain.library.Mode;
import de.vinzens.lumen.domain.library.PhysicalProperties;
import de.vinzens.lumen.domain.look.Look;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.project.FixtureSpec;
import de.vinzens.lumen.domain.project.Group;
import de.vinzens.lumen.domain.project.GroupSpec;
import de.vinzens.lumen.domain.project.PatchedFixture;
import de.vinzens.lumen.domain.project.Project;
import de.vinzens.lumen.domain.project.ProjectSpec;
import de.vinzens.lumen.domain.scene.Scene;
import de.vinzens.lumen.domain.scene.SceneComposition;
import de.vinzens.lumen.domain.scene.SceneKey;
import de.vinzens.lumen.domain.scene.SceneNumber;
import de.vinzens.lumen.domain.scene.SceneTiming;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Looks, compositions and scenes working together, on an active project with three dimmers in group 7. */
class ShowServicesTest {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	private static final Mode ONE_CHANNEL = new Mode("1ch", List.of(new ChannelDefinition(ChannelFunction.DIMMER,
			Optional.empty(), 8, 0, false, List.of(), Optional.empty())));
	private static final FixtureType DIMMER_TYPE = new FixtureType("dimmer", "Generic", "Dimmer",
			FixtureCategory.DIMMER, PhysicalProperties.NONE, List.of(ONE_CHANNEL));

	private final TestShow show = new TestShow();
	private final FakeProgrammer programmer = new FakeProgrammer();
	private final StateEvents events = new StateEvents();
	private final Clock clock = Clock.fixed(Instant.parse("2026-10-06T20:00:00Z"), ZoneOffset.UTC);
	private final LookService looks = new LookService(projects(), show.looks, show.compositions, programmer, events,
			clock);
	private final CompositionService compositions = new CompositionService(projects(), show.compositions, show.looks,
			show.scenes, fixtures(), groups(), library(), programmer, events);
	private final SceneService scenes = new SceneService(projects(), show.scenes, show.compositions, show.looks, looks,
			show.resolver(), programmer, events);

	@Test
	void recordingASceneMakesALookAndACompositionWithOneStep() {
		programmer.look = lit(1, 0.7);

		var scene = create("1", true);

		assertThat(scene.compositions()).singleElement().satisfies(use -> {
			var composition = show.compositions.findById(use.compositionId()).orElseThrow();
			assertThat(composition.spec().name()).isEqualTo("Scene");
			var look = show.looks.findById(composition.steps().getFirst().lookId()).orElseThrow();
			assertThat(look.spec().owner()).contains(composition.id());
			assertThat(look.look()).isEqualTo(programmer.look);
		});
	}

	@Test
	void recordingIntoASceneUpdatesTheLookOfItsFirstCompositionAndKeepsTheOldOne() {
		programmer.look = lit(1, 0.7);
		var scene = create("1", true);
		var lookId = show.compositions.findById(scene.compositions().getFirst().compositionId()).orElseThrow()
				.steps().getFirst().lookId();

		programmer.look = lit(2, 1);
		scenes.record(1, scene.id(), RecordOptions.REPLACE);

		assertThat(show.looks.findById(lookId).orElseThrow().look()).isEqualTo(lit(2, 1));
		assertThat(looks.revisions(1, lookId)).singleElement()
				.satisfies(revision -> assertThat(revision.look()).isEqualTo(lit(1, 0.7)));
	}

	@Test
	void aNewSceneTakesTheKeysOfTheSceneBeforeIt() {
		var storm = compositions.create(1, CompositionSpec.of("Storm"), List.of());
		var first = create("1", false);
		scenes.setKeys(1, first.id(), List.of(new SceneKey(3, storm.id(), KeyMode.FLASH, Duration.ZERO)));

		var second = create("2", false);

		assertThat(second.keys()).extracting(SceneKey::slot).containsExactly(3);
		assertThatThrownBy(() -> scenes.setKeys(1, second.id(), List.of(new SceneKey(1, 999, KeyMode.FLASH,
				Duration.ZERO)))).isInstanceOf(DomainValidationException.class);
	}

	@Test
	void aCompositionInAScenesCannotBeDeletedButItsCopyCan() {
		var chase = compositions.create(1, CompositionSpec.of("Chase"), List.of());
		var scene = create("1", false);
		scenes.setCompositions(1, scene.id(), List.of(SceneComposition.of(chase.id())));

		assertThatThrownBy(() -> compositions.delete(1, chase.id())).isInstanceOf(ConflictException.class)
				.hasMessageContaining("scene 1");
		var copy = compositions.duplicate(1, chase.id());
		compositions.delete(1, copy.id());
		assertThat(show.compositions.findById(copy.id())).isEmpty();
	}

	@Test
	void generatingReplacesTheStepsAndDropsOwnLooksThatAreNoLongerPlayed() {
		var chase = compositions.create(1, CompositionSpec.of("Chase"), List.of());
		programmer.look = lit(1, 1);
		chase = compositions.recordStep(1, chase.id());
		var recorded = chase.steps().getFirst().lookId();

		chase = compositions.generate(1, chase.id(), new GeneratorSettings(new Chase(0.5, 1, Chase.Direction.FORWARD,
				0, 0, 1), new Target.Group(7), false));

		assertThat(chase.steps()).hasSize(3);
		assertThat(chase.spec().holdSeconds()).isEqualTo(0.5);
		assertThat(chase.spec().generator()).isPresent();
		assertThat(show.looks.findById(recorded)).as("the recorded step is gone").isEmpty();
		var firstStep = show.looks.findById(chase.steps().getFirst().lookId()).orElseThrow().look().values();
		assertThat(firstStep.get(1L).get(DIMMER)).contains(1.0);
		assertThat(firstStep.get(2L).get(DIMMER)).contains(0.0);
	}

	@Test
	void stepsMayPlaySharedLooksButNotTheOwnLooksOfOtherCompositions() {
		var shared = looks.create(1, "Warm", false);
		var a = compositions.create(1, CompositionSpec.of("A"), List.of(CompositionStep.of(shared.id())));
		programmer.look = lit(1, 1);
		a = compositions.recordStep(1, a.id());
		var own = a.steps().get(1).lookId();

		var b = compositions.create(1, CompositionSpec.of("B"), List.of());
		assertThatThrownBy(() -> compositions.update(1, b.id(), b.spec(), List.of(CompositionStep.of(own))))
				.isInstanceOf(DomainValidationException.class).hasMessageContaining("another composition");

		looks.update(1, own, "Bright", true);
		compositions.update(1, b.id(), b.spec(), List.of(CompositionStep.of(own)));
		assertThatThrownBy(() -> looks.delete(1, own)).isInstanceOf(ConflictException.class)
				.hasMessageContaining("\"A\"").hasMessageContaining("\"B\"");
	}

	@Test
	void loadingASceneTakesEveryCompositionAtItsFirstStep() {
		programmer.look = lit(1, 0.4);
		var scene = create("1", true);
		var chase = compositions.create(1, CompositionSpec.of("Chase"), List.of());
		compositions.generate(1, chase.id(), new GeneratorSettings(new Chase(1, 1, Chase.Direction.FORWARD, 0.5, 0,
				1), new Target.Fixtures(List.of(2L, 3L)), false));
		scenes.setCompositions(1, scene.id(), List.of(scene.compositions().getFirst(),
				SceneComposition.of(chase.id())));
		programmer.clear();

		scenes.loadIntoProgrammer(1, scene.id());

		assertThat(programmer.look.values().get(1L).get(DIMMER)).contains(0.4);
		assertThat(programmer.look.values().get(2L).get(DIMMER)).contains(1.0);
		assertThat(programmer.look.values().get(3L).get(DIMMER)).contains(0.0);
	}

	private Scene create(String number, boolean record) {
		return scenes.create(1, Optional.of(SceneNumber.parse(number)), "Scene", Optional.empty(), Optional.empty(),
				SceneTiming.DEFAULT, "", record);
	}

	private static Look lit(long fixtureId, double level) {
		return Look.of(Map.of(fixtureId, new FixtureValues(Map.of(DIMMER, level))));
	}

	// --- in-memory project -------------------------------------------------------------------------------------

	private static ProjectRepository projects() {
		var project = new Project(1, new ProjectSpec("Show", Optional.empty(), Duration.ZERO, Duration.ZERO), true,
				Instant.EPOCH, Instant.EPOCH);
		return new ProjectRepository() {
			public List<Project> findAll() {
				return List.of(project);
			}

			public Optional<Project> findById(long id) {
				return Optional.of(project).filter(p -> p.id() == id);
			}

			public Project create(ProjectSpec spec, Instant now) {
				throw new UnsupportedOperationException();
			}

			public Project update(long id, ProjectSpec spec, Instant now) {
				throw new UnsupportedOperationException();
			}

			public void delete(long id) {
				throw new UnsupportedOperationException();
			}

			public void activate(long id) {
				throw new UnsupportedOperationException();
			}
		};
	}

	private static FixtureRepository fixtures() {
		var all = List.of(dimmer(1), dimmer(2), dimmer(3));
		return new FixtureRepository() {
			public List<PatchedFixture> findByProject(long projectId) {
				return all;
			}

			public Optional<PatchedFixture> findById(long id) {
				return all.stream().filter(f -> f.id() == id).findFirst();
			}

			public PatchedFixture create(long projectId, FixtureSpec spec) {
				throw new UnsupportedOperationException();
			}

			public PatchedFixture update(long id, FixtureSpec spec) {
				throw new UnsupportedOperationException();
			}

			public void delete(long id) {
				throw new UnsupportedOperationException();
			}

			public int countByUniverse(long universeId) {
				return all.size();
			}
		};
	}

	private static PatchedFixture dimmer(long id) {
		return new PatchedFixture(id, 1, new FixtureSpec("Dimmer " + id, Optional.empty(), "dimmer", "1ch", 1,
				(int) id, Optional.empty(), 0));
	}

	private static GroupRepository groups() {
		var group = new Group(7, 1, new GroupSpec("Row", Optional.empty(), 0, List.of(1L, 2L, 3L)));
		return new GroupRepository() {
			public List<Group> findByProject(long projectId) {
				return List.of(group);
			}

			public Optional<Group> findById(long id) {
				return Optional.of(group).filter(g -> g.id() == id);
			}

			public Group create(long projectId, GroupSpec spec) {
				throw new UnsupportedOperationException();
			}

			public Group update(long id, GroupSpec spec) {
				throw new UnsupportedOperationException();
			}

			public void delete(long id) {
				throw new UnsupportedOperationException();
			}
		};
	}

	private static QueryFixtureLibraryUseCase library() {
		return new QueryFixtureLibraryUseCase() {
			public List<FixtureType> fixtureTypes() {
				return List.of(DIMMER_TYPE);
			}

			public Optional<FixtureType> fixtureType(String id) {
				return Optional.of(DIMMER_TYPE).filter(type -> type.id().equals(id));
			}

			public List<LibraryIssue> issues() {
				return List.of();
			}
		};
	}
}
