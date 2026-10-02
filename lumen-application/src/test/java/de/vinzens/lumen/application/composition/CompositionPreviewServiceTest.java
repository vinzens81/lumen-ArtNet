package de.vinzens.lumen.application.composition;

import de.vinzens.lumen.application.ConflictException;
import de.vinzens.lumen.application.FixedCompositions;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.output.port.in.ControlModeUseCase;
import de.vinzens.lumen.application.project.port.out.GroupRepository;
import de.vinzens.lumen.application.scene.port.out.SceneRepository;
import de.vinzens.lumen.domain.composition.Chase;
import de.vinzens.lumen.domain.composition.Composition;
import de.vinzens.lumen.domain.composition.CompositionSpec;
import de.vinzens.lumen.domain.composition.Flicker;
import de.vinzens.lumen.domain.composition.Pattern;
import de.vinzens.lumen.domain.composition.Target;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelDefinition;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.library.Mode;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.output.OperatingMode;
import de.vinzens.lumen.domain.project.Group;
import de.vinzens.lumen.domain.project.GroupSpec;
import de.vinzens.lumen.domain.scene.Scene;
import de.vinzens.lumen.domain.scene.SceneNumber;
import de.vinzens.lumen.domain.scene.SceneSpec;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompositionPreviewServiceTest {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	private static final Mode DIMMER_MODE = new Mode("1ch", List.of(new ChannelDefinition(ChannelFunction.DIMMER,
			Optional.empty(), 8, 0, false, List.of(), Optional.empty())));

	private Instant now = Instant.parse("2026-10-02T20:00:00Z");
	private OperatingMode currentMode = OperatingMode.SETUP;
	private final StateEvents events = new StateEvents();

	private final Composition chase = composition(1, new Chase(1, 1, Chase.Direction.FORWARD, 0, 0, 1));
	private final Composition flicker = composition(2, new Flicker(Flicker.Character.CANDLE, 0.5, 1, 1));
	private final FixedCompositions stored = new FixedCompositions(Map.of(9L, List.of(chase, flicker)));
	/** scene 9: fixture 3 at 30 %, with the chase and the flicker on fixtures 1 and 2 */
	private final Scene scene = new Scene(9, 1, new SceneSpec(SceneNumber.of(1), "Fair", Optional.empty(),
			Optional.empty()), Map.of(3L, new FixtureValues(Map.of(DIMMER, 0.3))));
	private final CompositionPreviewService preview = new CompositionPreviewService(stored, scenes(),
			new CompositionResolver(stored, groups()), mode(), clock(), events);

	@Test
	void previewRunsOnItsOwnClockWithoutTouchingTheStage() {
		preview.layer(now, Map.of(1L, DIMMER_MODE, 2L, DIMMER_MODE));

		assertThat(preview.previewValues(1).get(1L).get(DIMMER)).contains(1.0);
		now = now.plusSeconds(1);
		assertThat(preview.previewValues(1).get(2L).get(DIMMER)).contains(1.0);
		assertThat(preview.previewValues(2).get(1L).get(DIMMER).orElseThrow())
				.as("flicker over full intensity").isBetween(0.5, 1.0);
		assertThat(preview.layer(now, Map.of(1L, DIMMER_MODE)).fixtures()).isEmpty();
	}

	@Test
	void stagePreviewIsALayerThatStopsInShowMode() {
		preview.showOnStage(1, 1);
		assertThat(preview.compositionOnStage()).contains(1L);
		var layer = preview.layer(now, Map.of(1L, DIMMER_MODE, 2L, DIMMER_MODE));
		assertThat(layer.priority()).isEqualTo(75);
		assertThat(layer.fixtures().get(1L).get(DIMMER)).contains(1.0);

		currentMode = OperatingMode.SHOW;
		events.publish(StateTopic.MODE);
		assertThat(preview.compositionOnStage()).isEmpty();
		assertThatThrownBy(() -> preview.showOnStage(1, 1)).isInstanceOf(ConflictException.class);
	}

	@Test
	void wholeSceneRunsOnStageWithItsValuesAndCompositions() {
		preview.showSceneOnStage(1, 9);
		assertThat(preview.sceneOnStage()).contains(9L);
		assertThat(preview.compositionOnStage()).isEmpty();

		var modes = Map.of(1L, DIMMER_MODE, 2L, DIMMER_MODE, 3L, DIMMER_MODE);
		var look = preview.layer(now, modes).fixtures();
		assertThat(look.get(3L).get(DIMMER)).as("static value of the scene").contains(0.3);
		assertThat(look.get(1L).get(DIMMER).orElseThrow()).as("chase on, then the flicker over it").isBetween(0.5, 1.0);
		now = now.plusSeconds(1);
		assertThat(preview.layer(now, modes).fixtures().get(2L).get(DIMMER).orElseThrow()).isGreaterThan(0.4);

		preview.showOnStage(1, 1);
		assertThat(preview.sceneOnStage()).as("one preview at a time").isEmpty();
		preview.stopOnStage();
		assertThat(preview.layer(now, modes).fixtures()).isEmpty();
	}

	private SceneRepository scenes() {
		return new SceneRepository() {
			public List<Scene> findByProject(long projectId) {
				return List.of(scene);
			}

			public Optional<Scene> findById(long id) {
				return Optional.of(scene).filter(s -> s.id() == id);
			}

			public Scene create(long projectId, SceneSpec spec, Map<Long, FixtureValues> values) {
				throw new UnsupportedOperationException();
			}

			public Scene updateSpec(long id, SceneSpec spec) {
				throw new UnsupportedOperationException();
			}

			public Scene replaceValues(long id, Map<Long, FixtureValues> values) {
				throw new UnsupportedOperationException();
			}

			public void delete(long id) {
				throw new UnsupportedOperationException();
			}
		};
	}

	private static Composition composition(long id, Pattern pattern) {
		return new Composition(id, 1, Optional.of(9L), Optional.empty(), 0,
				CompositionSpec.of("C" + id, pattern, new Target.Fixtures(List.of(1L, 2L))));
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

	private static GroupRepository groups() {
		return new GroupRepository() {
			public List<Group> findByProject(long projectId) {
				return List.of();
			}

			public Optional<Group> findById(long id) {
				return Optional.empty();
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
}
