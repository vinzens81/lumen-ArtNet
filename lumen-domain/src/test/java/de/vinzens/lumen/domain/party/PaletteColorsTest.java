package de.vinzens.lumen.domain.party;

import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelDefinition;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.library.ChannelRange;
import de.vinzens.lumen.domain.library.Mode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.vinzens.lumen.domain.library.ChannelFunction.AMBER;
import static de.vinzens.lumen.domain.library.ChannelFunction.BLUE;
import static de.vinzens.lumen.domain.library.ChannelFunction.COLOR_WHEEL;
import static de.vinzens.lumen.domain.library.ChannelFunction.DIMMER;
import static de.vinzens.lumen.domain.library.ChannelFunction.GREEN;
import static de.vinzens.lumen.domain.library.ChannelFunction.RED;
import static de.vinzens.lumen.domain.library.ChannelFunction.UV;
import static de.vinzens.lumen.domain.library.ChannelFunction.WHITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PaletteColorsTest {

	@Test
	void rgbFixtureGetsTheColor() {
		var values = PaletteColors.of(Palette.MAGENTA, mode(DIMMER, RED, GREEN, BLUE)).values();

		assertThat(values).containsExactlyInAnyOrderEntriesOf(Map.of(attr(RED), 1.0, attr(GREEN), 0.0,
				attr(BLUE), 1.0));
	}

	@Test
	void whiteChannelTakesTheCommonPart() {
		var values = PaletteColors.of(Palette.WARM_WHITE, mode(RED, GREEN, BLUE, WHITE)).values();

		assertThat(values.get(attr(WHITE))).isCloseTo(0.4, within(1e-9));
		assertThat(values.get(attr(RED))).isCloseTo(0.6, within(1e-9));
		assertThat(values.get(attr(BLUE))).isCloseTo(0.0, within(1e-9));
		assertThat(PaletteColors.of(Palette.WHITE, mode(RED, GREEN, BLUE, WHITE)).values())
				.containsEntry(attr(WHITE), 1.0).containsEntry(attr(RED), 0.0);
	}

	@Test
	void dedicatedEmittersAreUsedAloneAndClearedOtherwise() {
		var uvPar = mode(RED, GREEN, BLUE, AMBER, UV);

		assertThat(PaletteColors.of(Palette.UV, uvPar).values()).containsEntry(attr(UV), 1.0)
				.containsEntry(attr(RED), 0.0).containsEntry(attr(BLUE), 0.0);
		assertThat(PaletteColors.of(Palette.AMBER, uvPar).values()).containsEntry(attr(AMBER), 1.0)
				.containsEntry(attr(RED), 0.0);
		assertThat(PaletteColors.of(Palette.RED, uvPar).values()).as("nothing left over from UV")
				.containsEntry(attr(UV), 0.0).containsEntry(attr(AMBER), 0.0).containsEntry(attr(RED), 1.0);
	}

	@Test
	void colorWheelGetsTheClosestSlot() {
		var wheel = new ChannelDefinition(COLOR_WHEEL, Optional.empty(), 8, 0, false, List.of(
				new ChannelRange(0, 9, "Open", Optional.of("#ffffff")),
				new ChannelRange(10, 19, "Red", Optional.of("#ff0000")),
				new ChannelRange(20, 29, "Deep blue", Optional.of("#0000c0")),
				new ChannelRange(30, 255, "Rotation", Optional.empty())), Optional.empty());
		var spot = new Mode("spot", List.of(channel(DIMMER), wheel));

		assertThat(PaletteColors.of(Palette.BLUE, spot).get(attr(COLOR_WHEEL)).orElseThrow())
				.isCloseTo(24.5 / 255, within(1e-9));
		assertThat(PaletteColors.of(Palette.AMBER, spot).get(attr(COLOR_WHEEL)).orElseThrow())
				.as("closest to amber").isCloseTo(14.5 / 255, within(1e-9));
	}

	@Test
	void dimmerOnlyFixtureGetsNoColor() {
		assertThat(PaletteColors.of(Palette.RED, mode(DIMMER)).isEmpty()).isTrue();
	}

	static Mode mode(ChannelFunction... functions) {
		return new Mode("test", Arrays.stream(functions).map(PaletteColorsTest::channel).toList());
	}

	private static ChannelDefinition channel(ChannelFunction function) {
		return new ChannelDefinition(function, Optional.empty(), 8, 0, false, List.of(), Optional.empty());
	}

	private static Attribute attr(ChannelFunction function) {
		return Attribute.of(function);
	}
}
