package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.domain.composition.Chase;
import de.vinzens.lumen.domain.composition.Color;
import de.vinzens.lumen.domain.composition.ColorCycle;
import de.vinzens.lumen.domain.composition.CompositionSpec;
import de.vinzens.lumen.domain.composition.Flicker;
import de.vinzens.lumen.domain.composition.StepSequence;
import de.vinzens.lumen.domain.composition.StepSequence.Step;
import de.vinzens.lumen.domain.composition.Target;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.project.ArtNetAddress;
import de.vinzens.lumen.domain.project.ArtNetTarget;
import de.vinzens.lumen.domain.project.FixtureSpec;
import de.vinzens.lumen.domain.project.GroupSpec;
import de.vinzens.lumen.domain.project.ProjectSpec;
import de.vinzens.lumen.domain.project.UniverseSpec;
import de.vinzens.lumen.domain.scene.SceneNumber;
import de.vinzens.lumen.domain.scene.SceneSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SqliteCompositionRepositoryTest {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	private static final Attribute RED = Attribute.of(ChannelFunction.RED);

	@TempDir
	Path dir;

	private Path dbFile;
	private SqliteRepositories repos;
	private long project;
	private long scene;
	private long a;
	private long b;
	private long group;

	@BeforeEach
	void setUp() {
		dbFile = dir.resolve("lumen.db");
		repos = new SqliteRepositories(SqliteDatabase.open(dbFile));
		project = repos.projects().create(new ProjectSpec("Show", Optional.empty(), Duration.ZERO, Duration.ZERO),
				Instant.EPOCH).id();
		var universe = repos.universes().create(project, new UniverseSpec(1, Optional.empty(),
				new ArtNetAddress(0, 0, 0), new ArtNetTarget.Broadcast())).id();
		a = repos.fixtures().create(project, fixture("A", universe, 1)).id();
		b = repos.fixtures().create(project, fixture("B", universe, 2)).id();
		group = repos.groups().create(project, new GroupSpec("Ramp", Optional.empty(), 0, List.of(a, b))).id();
		scene = repos.scenes().create(project, new SceneSpec(SceneNumber.of(1), "Fair", Optional.empty(),
				Optional.empty()), Map.of()).id();
	}

	@Test
	void everyPatternSurvivesReopening() {
		var chase = CompositionSpec.of("Ramp chase", new Chase(0.4, 2, Chase.Direction.PING_PONG, 0.3, 0.1, 0.9),
				new Target.Group(group));
		var cycle = new CompositionSpec("Booths", new ColorCycle(List.of(new Color(1, 0, 0), new Color(0, 0.5, 1)),
				2, 1, 0.5, 0.8), new Target.Fixtures(List.of(b, a)), 0.7, 1.5, true);
		var steps = CompositionSpec.of("Steps", new StepSequence(List.of(
				new Step(1, 0.5, Map.of(a, new FixtureValues(Map.of(DIMMER, 1.0, RED, 0.25)))),
				new Step(2, 0, Map.of(b, new FixtureValues(Map.of(DIMMER, 0.5))))), StepSequence.PlayMode.PING_PONG),
				new Target.Steps());

		var savedChase = repos.compositions().create(project, Optional.empty(), Optional.empty(), chase);
		var savedCycle = repos.compositions().create(project, Optional.of(scene), Optional.of(savedChase.id()), cycle);
		var savedSteps = repos.compositions().create(project, Optional.empty(), Optional.empty(), steps);

		var reopened = new SqliteRepositories(SqliteDatabase.open(dbFile)).compositions();
		assertThat(reopened.findById(savedChase.id()).orElseThrow().spec()).isEqualTo(chase);
		var copy = reopened.findById(savedCycle.id()).orElseThrow();
		assertThat(copy.spec()).isEqualTo(cycle);
		assertThat(copy.sceneId()).contains(scene);
		assertThat(copy.sourceId()).contains(savedChase.id());
		assertThat(reopened.findById(savedSteps.id()).orElseThrow().spec()).isEqualTo(steps);
		assertThat(reopened.findLibrary(project)).extracting(c -> c.spec().name()).containsExactly("Ramp chase", "Steps");
		assertThat(reopened.findByScenes(project)).containsOnlyKeys(scene);
	}

	@Test
	void copiesKeepTheirOrderAndFollowDeletions() {
		var library = repos.compositions().create(project, Optional.empty(), Optional.empty(), flicker("Candle"));
		var first = repos.compositions().create(project, Optional.of(scene), Optional.of(library.id()),
				CompositionSpec.of("Candle", flicker("x").pattern(), new Target.Fixtures(List.of(a, b))));
		var second = repos.compositions().create(project, Optional.of(scene), Optional.empty(),
				CompositionSpec.of("Ramp", flicker("x").pattern(), new Target.Group(group)));
		assertThat(repos.compositions().findByScene(scene)).extracting(c -> c.id()).containsExactly(first.id(), second.id());

		repos.compositions().reorder(scene, List.of(second.id(), first.id()));
		assertThat(repos.compositions().findByScene(scene)).extracting(c -> c.id()).containsExactly(second.id(), first.id());

		repos.compositions().delete(library.id());
		repos.fixtures().delete(a);
		repos.groups().delete(group);
		var copies = repos.compositions().findByScene(scene);
		assertThat(copies.get(1).sourceId()).as("library origin deleted").isEmpty();
		assertThat(copies.get(1).spec().target()).isEqualTo(new Target.Fixtures(List.of(b)));
		assertThat(copies.get(0).spec().target()).as("group deleted: empty").isEqualTo(new Target.Fixtures(List.of()));

		repos.scenes().delete(scene);
		assertThat(repos.compositions().findByScenes(project)).isEmpty();
	}

	@Test
	void updateReplacesTargetAndSteps() {
		var created = repos.compositions().create(project, Optional.empty(), Optional.empty(),
				CompositionSpec.of("Booths", flicker("x").pattern(), new Target.Fixtures(List.of(a, b))));
		var changed = CompositionSpec.of("Booths", flicker("x").pattern(), new Target.Fixtures(List.of(b)));

		assertThat(repos.compositions().update(created.id(), changed).spec()).isEqualTo(changed);
	}

	private static CompositionSpec flicker(String name) {
		return CompositionSpec.of(name, new Flicker(Flicker.Character.CANDLE, 0.4, 1, 3), new Target.Fixtures(List.of()));
	}

	private static FixtureSpec fixture(String name, long universe, int address) {
		return new FixtureSpec(name, Optional.empty(), "generic-dimmer", "1ch", universe, address, Optional.empty(), 0);
	}
}
