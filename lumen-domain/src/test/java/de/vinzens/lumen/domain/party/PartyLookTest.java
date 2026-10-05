package de.vinzens.lumen.domain.party;

import de.vinzens.lumen.domain.composition.KeyMode;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.Mode;
import de.vinzens.lumen.domain.output.FixtureValues;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static de.vinzens.lumen.domain.library.ChannelFunction.BLUE;
import static de.vinzens.lumen.domain.library.ChannelFunction.DIMMER;
import static de.vinzens.lumen.domain.library.ChannelFunction.GOBO;
import static de.vinzens.lumen.domain.library.ChannelFunction.GREEN;
import static de.vinzens.lumen.domain.library.ChannelFunction.RED;
import static org.assertj.core.api.Assertions.assertThat;

class PartyLookTest {

	private static final Attribute DIM = Attribute.of(DIMMER);
	private static final Attribute R = Attribute.of(RED);
	private static final Attribute G = Attribute.of(GOBO);
	private static final Mode PAR = PaletteColorsTest.mode(DIMMER, RED, GREEN, BLUE);
	private static final Mode SPOT = PaletteColorsTest.mode(DIMMER, GOBO);
	private static final Map<Long, Mode> MODES = Map.of(1L, PAR, 2L, PAR, 3L, SPOT);

	private final PartyLook look = new PartyLook();

	@Test
	void paletteLightsDarkFixturesAndKeepsTheIntensityOfLitOnes() {
		look.setIntensity(List.of(2L), 0.4);

		look.setPalette(List.of(1L, 2L), Palette.RED);

		var output = look.output(MODES);
		assertThat(output.get(1L).get(DIM)).contains(1.0);
		assertThat(output.get(2L).get(DIM)).contains(0.4);
		assertThat(output.get(2L).get(R)).contains(1.0);
		assertThat(output).doesNotContainKey(3L);
	}

	@Test
	void buttonValuesWinAndTheLastPressedGoesFirst() {
		look.useButtons(List.of(button(7, KeyMode.TOGGLE, 0.2, 0.0), button(8, KeyMode.TOGGLE, 0.6, 0.5)));
		look.setIntensity(List.of(3L), 0.8);

		look.press(7);
		look.press(8);
		var both = look.output(MODES).get(3L);
		assertThat(both.get(G)).contains(0.6);
		assertThat(both.get(DIM)).as("intensity HTP").contains(0.8);

		look.press(7);
		assertThat(look.isOn(7)).isFalse();
		assertThat(look.output(MODES).get(3L).get(G)).contains(0.6);
	}

	@Test
	void flashIsOnWhileHeld() {
		look.useButtons(List.of(button(9, KeyMode.FLASH, 0.3, 1.0)));

		look.press(9);
		assertThat(look.output(MODES).get(3L).get(DIM)).contains(1.0);
		look.press(9);
		assertThat(look.isOn(9)).as("a second press does not switch a flash off").isTrue();

		look.release(9);
		assertThat(look.output(MODES)).doesNotContainKey(3L);
	}

	@Test
	void deletedButtonsGoOffAndClearResetsEverything() {
		look.useButtons(List.of(button(7, KeyMode.TOGGLE, 0.2, 0.0)));
		look.press(7);
		look.setPalette(List.of(1L), Palette.BLUE);

		look.useButtons(List.of());
		assertThat(look.activeButtons()).isEmpty();

		look.clear();
		assertThat(look.output(MODES)).isEmpty();
	}

	private static PartyButton button(long id, KeyMode mode, double gobo, double dimmer) {
		return new PartyButton(id, 1, (int) id, "Button " + id, mode,
				Map.of(3L, new FixtureValues(Map.of(G, gobo, DIM, dimmer))));
	}
}
