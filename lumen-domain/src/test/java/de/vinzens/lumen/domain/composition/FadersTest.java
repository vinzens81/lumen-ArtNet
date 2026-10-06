package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.composition.Faders.PlayableFader;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.output.FixtureValues;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class FadersTest {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	private static final Attribute RED = Attribute.of(ChannelFunction.RED);
	private static final Instant NOW = Instant.parse("2026-10-05T20:00:00Z");

	private final Faders faders = new Faders();

	@Test
	void faderLaysItsCompositionOverThePlaybackAtItsLevel() {
		faders.configure(List.of(fader(1, 10, Map.of(5L, new FixtureValues(Map.of(DIMMER, 1.0, RED, 1.0))))));
		var stage = Map.of(5L, new FixtureValues(Map.of(DIMMER, 0.2, RED, 0.0)), 6L,
				new FixtureValues(Map.of(DIMMER, 0.7)));

		assertThat(faders.apply(stage, NOW, FixtureAttributes.ALL)).isEqualTo(stage);

		faders.set(1, 0.5, NOW);
		var mixed = faders.apply(stage, NOW, FixtureAttributes.ALL);
		assertThat(mixed.get(5L).get(DIMMER)).as("HTP").contains(0.5);
		assertThat(mixed.get(5L).get(RED).orElseThrow()).isCloseTo(0.5, within(1e-9));
		assertThat(mixed.get(6L)).isEqualTo(stage.get(6L));
	}

	@Test
	void levelStaysWhileTheFaderKeepsItsComposition() {
		faders.configure(List.of(fader(1, 10, Map.of()), fader(2, 11, Map.of())));
		faders.set(1, 0.8, NOW);
		faders.set(2, 0.3, NOW);

		faders.configure(List.of(fader(1, 10, Map.of()), fader(2, 12, Map.of())));

		assertThat(faders.status()).extracting(Faders.FaderStatus::level).containsExactly(0.8, 0.0);
	}

	@Test
	void levelsMustBeValidAndFadersAssigned() {
		faders.configure(List.of(fader(1, 10, Map.of())));

		assertThatThrownBy(() -> faders.set(1, 1.5, NOW)).hasMessageContaining("between 0 and 1");
		assertThatThrownBy(() -> faders.set(3, 0.5, NOW)).hasMessageContaining("No fader on slot 3");
		assertThat(faders.set(1, 0, NOW)).as("already down").isFalse();
	}

	private static PlayableFader fader(int slot, long compositionId, Map<Long, FixtureValues> values) {
		// fixed values: one composition with one step
		var look = new StepSequence(List.of(new StepSequence.Step(1, 0, values)), StepSequence.PlayMode.LOOP);
		var composition = new ActiveComposition(compositionId, Optional.of(compositionId), look,
				List.copyOf(values.keySet()), 1, 1, false);
		return new PlayableFader(slot, "Fader " + slot, composition);
	}
}
