package de.vinzens.lumen.application.composition;

import de.vinzens.lumen.application.ConflictException;
import de.vinzens.lumen.application.TestShow;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.output.port.in.ControlModeUseCase;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.domain.composition.Chase;
import de.vinzens.lumen.domain.composition.Composition;
import de.vinzens.lumen.domain.composition.FixtureAttributes;
import de.vinzens.lumen.domain.composition.Flicker;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelDefinition;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.library.Mode;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.output.OperatingMode;
import de.vinzens.lumen.domain.project.Project;
import de.vinzens.lumen.domain.project.ProjectSpec;
import de.vinzens.lumen.domain.scene.Scene;
import de.vinzens.lumen.domain.scene.SceneComposition;
import de.vinzens.lumen.domain.scene.SceneNumber;
import de.vinzens.lumen.domain.scene.SceneSpec;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class CompositionPreviewServiceTest {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	private static final Mode DIMMER_MODE = new Mode("1ch", List.of(new ChannelDefinition(ChannelFunction.DIMMER,
			Optional.empty(), 8, 0, false, List.of(), Optional.empty())));

	private Instant now = Instant.parse("2026-10-02T20:00:00Z");
	private OperatingMode currentMode = OperatingMode.SETUP;
	private final StateEvents events = new StateEvents();

	private final TestShow show = new TestShow();
	/** composition 1: a chase on fixtures 1 and 2 */
	private final Composition chase = show.generated(1, "Chase", new Chase(1, 1, Chase.Direction.FORWARD, 0, 0, 1),
			List.of(1L, 2L), FixtureAttributes.ALL);
	/** composition 2: a flicker on fixtures 1 and 2 */
	private final Composition flicker = show.generated(1, "Flicker", new Flicker(Flicker.Character.CANDLE, 0.5, 1, 1),
			List.of(1L, 2L), FixtureAttributes.ALL);
	/** scene 0.5: fixture 4 at full */
	private final Scene before = show.scenes.create(1, new SceneSpec(SceneNumber.parse("0.5"), "Before",
			Optional.empty(), Optional.empty()), List.of(SceneComposition.of(show.fixed(1, "Before",
			Map.of(4L, new FixtureValues(Map.of(DIMMER, 1.0)))).id())), List.of());
	/** scene 1 after it: fixture 3 at 30 %, with the chase and the flicker on fixtures 1 and 2 */
	private final Scene scene = show.scenes.create(1, new SceneSpec(SceneNumber.of(1), "Fair", Optional.empty(),
			Optional.empty()), List.of(SceneComposition.of(show.fixed(1, "Fair",
			Map.of(3L, new FixtureValues(Map.of(DIMMER, 0.3)))).id()), SceneComposition.of(chase.id()),
			SceneComposition.of(flicker.id())), List.of());
	/** project fade times: in 2 s, out 4 s */
	private final CompositionPreviewService preview = new CompositionPreviewService(show.compositions, show.moves,
			show.scenes,
			projects(), show.resolver(), mode(), clock(), events);

	@Test
	void previewRunsOnItsOwnClockWithoutTouchingTheStage() {
		preview.layer(now, Map.of(1L, DIMMER_MODE, 2L, DIMMER_MODE));

		assertThat(preview.previewValues(chase.id()).get(1L).get(DIMMER)).contains(1.0);
		now = now.plusSeconds(1);
		assertThat(preview.previewValues(chase.id()).get(2L).get(DIMMER)).contains(1.0);
		assertThat(preview.previewValues(flicker.id()).get(1L).get(DIMMER).orElseThrow())
				.as("flicker over full intensity").isBetween(0.5, 1.0);
		assertThat(preview.layer(now, Map.of(1L, DIMMER_MODE)).fixtures()).isEmpty();
	}

	@Test
	void stagePreviewIsALayerThatStopsInShowMode() {
		preview.showOnStage(1, chase.id());
		assertThat(preview.compositionOnStage()).contains(chase.id());
		var layer = preview.layer(now, Map.of(1L, DIMMER_MODE, 2L, DIMMER_MODE));
		assertThat(layer.priority()).isEqualTo(75);
		assertThat(layer.fixtures().get(1L).get(DIMMER)).contains(1.0);

		currentMode = OperatingMode.SHOW;
		events.publish(StateTopic.MODE);
		assertThat(preview.compositionOnStage()).isEmpty();
		assertThatThrownBy(() -> preview.showOnStage(1, chase.id())).isInstanceOf(ConflictException.class);
	}

	@Test
	void wholeSceneRunsOnStageWithItsCompositions() {
		preview.showSceneOnStage(1, scene.id());
		assertThat(preview.sceneOnStage()).contains(scene.id());
		assertThat(preview.compositionOnStage()).isEmpty();

		var modes = Map.of(1L, DIMMER_MODE, 2L, DIMMER_MODE, 3L, DIMMER_MODE);
		var look = preview.layer(now, modes).fixtures();
		assertThat(look.get(3L).get(DIMMER)).as("fixed values of the scene").contains(0.3);
		assertThat(look.get(1L).get(DIMMER).orElseThrow()).as("chase on, then the flicker over it").isBetween(0.5, 1.0);
		now = now.plusSeconds(1);
		assertThat(preview.layer(now, modes).fixtures().get(2L).get(DIMMER).orElseThrow()).isGreaterThan(0.4);

		preview.showOnStage(1, chase.id());
		assertThat(preview.sceneOnStage()).as("one preview at a time").isEmpty();
		preview.stopOnStage();
		assertThat(preview.layer(now, modes).fixtures()).isEmpty();
	}

	@Test
	void rehearsedFadeComesFromThePreviousSceneWithTheTimesOfTheShow() {
		var modes = Map.of(1L, DIMMER_MODE, 2L, DIMMER_MODE, 3L, DIMMER_MODE, 4L, DIMMER_MODE);
		preview.layer(now, modes);

		preview.rehearseFade(1, scene.id());

		assertThat(preview.sceneOnStage()).contains(scene.id());
		assertThat(dimmer(modes, 4)).as("starts with the previous scene").isEqualTo(1.0);
		now = now.plusSeconds(1);
		assertThat(dimmer(modes, 4)).isCloseTo(0.75, within(1e-9));
		assertThat(dimmer(modes, 3)).isCloseTo(0.15, within(1e-9));
		assertThat(preview.fadeOnStage()).get().satisfies(fade -> {
			assertThat(fade.fromSceneId()).contains(before.id());
			assertThat(fade.elapsed()).isEqualTo(Duration.ofSeconds(1));
		});

		now = now.plusSeconds(3);
		assertThat(preview.fadeOnStage()).as("ended, the scene stays").isEmpty();
		assertThat(preview.sceneOnStage()).contains(scene.id());
		assertThat(dimmer(modes, 3)).isEqualTo(0.3);
		assertThat(dimmer(modes, 4)).isZero();
	}

	@Test
	void rehearsedFadeIntoTheFirstSceneComesFromTheDarkStage() {
		var modes = Map.of(4L, DIMMER_MODE);
		preview.layer(now, modes);

		preview.rehearseFade(1, before.id());
		now = now.plusSeconds(1);

		assertThat(dimmer(modes, 4)).isCloseTo(0.5, within(1e-9));
		assertThat(preview.fadeOnStage()).get().satisfies(fade -> assertThat(fade.fromSceneId()).isEmpty());
	}

	@Test
	void rehearsedFadeNeedsSetupMode() {
		currentMode = OperatingMode.SHOW;

		assertThatThrownBy(() -> preview.rehearseFade(1, scene.id())).isInstanceOf(ConflictException.class);
	}

	private double dimmer(Map<Long, Mode> modes, long fixtureId) {
		return preview.layer(now, modes).fixtures().getOrDefault(fixtureId, FixtureValues.EMPTY).get(DIMMER)
				.orElse(0.0);
	}

	private ProjectRepository projects() {
		var project = new Project(1, new ProjectSpec("Show", Optional.empty(), Duration.ofSeconds(2),
				Duration.ofSeconds(4)), true, Instant.EPOCH, Instant.EPOCH);
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

	private ControlModeUseCase mode() {
		return new ControlModeUseCase() {
			@Override
			public OperatingMode mode() {
				return currentMode;
			}

			@Override
			public OperatingMode setMode(OperatingMode mode) {
				currentMode = mode;
				return mode;
			}
		};
	}

	private Clock clock() {
		return new Clock() {
			@Override
			public ZoneId getZone() {
				return ZoneOffset.UTC;
			}

			@Override
			public Clock withZone(ZoneId zone) {
				return this;
			}

			@Override
			public Instant instant() {
				return now;
			}
		};
	}

}
