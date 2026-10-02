package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.composition.ExtraKeys.PlayableKey;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.output.FixtureValues;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ExtraKeysTest {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	/** full intensity on fixture 1 */
	private static final Wave FULL = new Wave(Wave.Shape.SQUARE, 3600, 1, 1, 0);

	private final ExtraKeys keys = new ExtraKeys();
	private Instant now = Instant.parse("2026-10-02T20:00:00Z");

	@BeforeEach
	void setUp() {
		keys.configure(List.of(
				key(1, KeyMode.TOGGLE, Duration.ofSeconds(2), 11),
				key(2, KeyMode.FLASH, Duration.ZERO, 12)), now);
		keys.setAvailable(Set.of(1, 2), now);
	}

	@Test
	void toggleFadesInAndOut() {
		assertThat(keys.press(1, now)).isTrue();
		advance(1000);
		assertThat(dimmer()).isCloseTo(0.5, within(1e-9));
		advance(1000);
		assertThat(dimmer()).isEqualTo(1.0);
		assertThat(keys.tick(now)).as("fade-in finished").isTrue();

		keys.press(1, now);
		advance(500);
		assertThat(dimmer()).isCloseTo(0.75, within(1e-9));
		keys.press(1, now); // back on while fading out: continues from 75 %
		advance(500);
		assertThat(dimmer()).isEqualTo(1.0);
	}

	@Test
	void flashIsOnWhileHeld() {
		keys.press(2, now);
		assertThat(dimmer()).isEqualTo(1.0);
		keys.press(2, now); // repeated key events keep it on
		assertThat(keys.level(2, now)).isEqualTo(1.0);
		keys.release(2, now);
		assertThat(dimmer()).isZero();
		assertThat(keys.tick(now)).isTrue();
		assertThat(keys.activeSlots()).isEmpty();
	}

	@Test
	void unavailableKeysCannotBePressedAndFadeOutWhenTheSceneChanges() {
		keys.press(1, now);
		advance(2000);
		keys.setAvailable(Set.of(2), now); // GO to a scene without key 1
		advance(1000);
		assertThat(dimmer()).isCloseTo(0.5, within(1e-9));
		assertThat(keys.press(1, now)).isFalse();
		advance(1000);
		assertThat(dimmer()).isZero();
	}

	@Test
	void endOfShowSwitchesEverythingOff() {
		keys.press(1, now);
		keys.press(2, now);
		keys.allOff(now);
		advance(2000);
		assertThat(dimmer()).isZero();
		assertThat(keys.status(now)).extracting(ExtraKeys.KeyStatus::on).containsExactly(false, false);
	}

	@Test
	void keysAreLaidOverTheLookWithTheCompositionRules() {
		var flicker = new ActiveComposition(13, Optional.empty(), new Flicker(Flicker.Character.CANDLE, 1, 1, 5),
				List.of(1L), 1, 1, false);
		keys.configure(List.of(new PlayableKey(3, KeyMode.TOGGLE, Duration.ZERO, "Candle", flicker)), now);
		keys.setAvailable(Set.of(3), now);
		keys.press(3, now);
		advance(700);

		var base = Map.of(1L, new FixtureValues(Map.of(DIMMER, 0.6)));
		var look = keys.apply(base, now, FixtureAttributes.ALL);

		assertThat(look.get(1L).get(DIMMER).orElseThrow()).as("flickers around the scene's 60 %").isBetween(0.0, 0.6);
		assertThat(keys.apply(Map.of(), now, FixtureAttributes.ALL).get(1L).get(DIMMER)).as("no base: dark").contains(0.0);
	}

	@Test
	void reconfiguringKeepsRunningKeysWithTheSameComposition() {
		keys.press(1, now);
		advance(2000);
		keys.configure(List.of(key(1, KeyMode.TOGGLE, Duration.ofSeconds(2), 11)), now);
		assertThat(dimmer()).isEqualTo(1.0);
		keys.configure(List.of(key(1, KeyMode.TOGGLE, Duration.ofSeconds(2), 99)), now);
		assertThat(dimmer()).as("another composition on the key").isZero();
	}

	private PlayableKey key(int slot, KeyMode mode, Duration fade, long compositionId) {
		return new PlayableKey(slot, mode, fade, "Key " + slot,
				new ActiveComposition(compositionId, Optional.empty(), FULL, List.of(1L), 1, 1, false));
	}

	private double dimmer() {
		return keys.apply(Map.of(), now, FixtureAttributes.ALL).getOrDefault(1L, FixtureValues.EMPTY).get(DIMMER)
				.orElse(0.0);
	}

	private void advance(long ms) {
		now = now.plusMillis(ms);
	}
}
