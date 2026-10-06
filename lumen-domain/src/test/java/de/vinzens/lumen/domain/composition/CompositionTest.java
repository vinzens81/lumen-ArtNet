package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.look.Look;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.scene.Scene;
import de.vinzens.lumen.domain.scene.SceneComposition;
import de.vinzens.lumen.domain.scene.SceneKey;
import de.vinzens.lumen.domain.scene.SceneNumber;
import de.vinzens.lumen.domain.scene.SceneSpec;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompositionTest {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	private static final Attribute RED = Attribute.of(ChannelFunction.RED);
	private static final Map<Long, Look> LOOKS = Map.of(
			1L, Look.of(Map.of(5L, new FixtureValues(Map.of(DIMMER, 1.0)))),
			2L, Look.of(Map.of(6L, new FixtureValues(Map.of(DIMMER, 0.5)))));

	@Test
	void stepsPlayTheirLooksWithDefaultOrOwnTimes() {
		var spec = new CompositionSpec("Two", 1, 2, PlayMode.PING_PONG, IntensityMode.RELATIVE, Optional.empty());
		var composition = new Composition(9, 1, spec, List.of(CompositionStep.of(1),
				new CompositionStep(2, Optional.of(0.0), Optional.of(5.0))));

		var sequence = composition.sequence(LOOKS);

		assertThat(sequence.steps()).extracting(StepSequence.Step::fadeSeconds).containsExactly(1.0, 0.0);
		assertThat(sequence.steps()).extracting(StepSequence.Step::holdSeconds).containsExactly(2.0, 5.0);
		assertThat(sequence.playMode()).isEqualTo(StepSequence.PlayMode.PING_PONG);
		assertThat(sequence.intensityMode()).isEqualTo(IntensityMode.RELATIVE);
		assertThat(sequence.ownFixtures()).contains(List.of(5L, 6L));
	}

	@Test
	void parallelStepsPlayTogetherInOneRowWithTheTimesOfItsFirstStep() {
		var looks = Map.of(
				1L, Look.of(Map.of(5L, new FixtureValues(Map.of(DIMMER, 1.0, RED, 1.0)))),
				2L, Look.of(Map.of(5L, new FixtureValues(Map.of(DIMMER, 0.4, RED, 0.2)),
						6L, new FixtureValues(Map.of(DIMMER, 0.5)))),
				3L, Look.of(Map.of(7L, new FixtureValues(Map.of(DIMMER, 1.0)))));
		var composition = new Composition(9, 1, CompositionSpec.of("Parallel"), List.of(
				new CompositionStep(1, Optional.of(0.5), Optional.of(4.0)), CompositionStep.parallelOf(2),
				CompositionStep.of(3)));

		var sequence = composition.sequence(looks);

		assertThat(composition.rows()).hasSize(2);
		assertThat(sequence.steps()).hasSize(2);
		var first = sequence.steps().getFirst();
		assertThat(first.fadeSeconds()).isEqualTo(0.5);
		assertThat(first.holdSeconds()).isEqualTo(4.0);
		assertThat(first.values()).containsOnlyKeys(5L, 6L);
		assertThat(first.values().get(5L).get(DIMMER)).as("intensity HTP").contains(1.0);
		assertThat(first.values().get(5L).get(RED)).as("colour from the later step").contains(0.2);
		assertThat(sequence.steps().get(1).values()).containsOnlyKeys(7L);
	}

	@Test
	void aParallelStepHasNoTimesOfItsOwnAndCannotComeFirst() {
		assertThatThrownBy(() -> new CompositionStep(1, Optional.of(1.0), Optional.empty(), true))
				.hasMessageContaining("parallel");
		assertThatThrownBy(() -> new Composition(9, 1, CompositionSpec.of("Bad"),
				List.of(CompositionStep.parallelOf(1)))).hasMessageContaining("first step");
	}

	@Test
	void aMissingLookPlaysAsAnEmptyStep() {
		var composition = new Composition(9, 1, CompositionSpec.of("Gone"), List.of(CompositionStep.of(42)));

		assertThat(composition.sequence(LOOKS).steps().getFirst().values()).isEmpty();
	}

	@Test
	void aCompositionRunningOnceStandsStillAtItsLastStep() {
		var spec = new CompositionSpec("Sunrise", 1, 1, PlayMode.ONCE, IntensityMode.ABSOLUTE, Optional.empty());
		var composition = new Composition(9, 1, spec, List.of(CompositionStep.of(1), CompositionStep.of(2)));

		var active = ActiveComposition.of(composition, LOOKS, 0.8, 2, true);

		assertThat(active.once()).isTrue();
		assertThat(active.level()).isEqualTo(0.8);
		assertThat(active.sourceId()).as("continues as the same composition").contains(9L);
	}

	@Test
	void everyStepMustLastLongEnough() {
		var spec = new CompositionSpec("Fast", 0, 1, PlayMode.LOOP, IntensityMode.ABSOLUTE, Optional.empty());

		assertThatThrownBy(() -> new Composition(9, 1, spec, List.of(CompositionStep.of(1),
				new CompositionStep(2, Optional.empty(), Optional.of(0.0)))))
				.isInstanceOf(DomainValidationException.class).hasMessageContaining("step 2");
	}

	@Test
	void aSceneHoldsEachCompositionAndEachKeyOnce() {
		var spec = new SceneSpec(SceneNumber.of(1), "One", Optional.empty(), Optional.empty());

		assertThatThrownBy(() -> new Scene(1, 1, spec, List.of(SceneComposition.of(3), SceneComposition.of(3)),
				List.of())).hasMessageContaining("twice");
		assertThatThrownBy(() -> new Scene(1, 1, spec, List.of(), List.of(
				new SceneKey(1, 3, KeyMode.TOGGLE, Duration.ZERO), new SceneKey(1, 4, KeyMode.FLASH, Duration.ZERO))))
				.hasMessageContaining("key is used twice");
		assertThatThrownBy(() -> new SceneKey(9, 3, KeyMode.TOGGLE, Duration.ZERO)).hasMessageContaining("slot");
	}
}
