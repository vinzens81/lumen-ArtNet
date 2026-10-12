package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.composition.FixtureColors.Mixing;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelDefinition;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.library.ChannelRange;
import de.vinzens.lumen.domain.library.Mode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FixtureColorsTest {

	private static final Attribute WHEEL = Attribute.of(ChannelFunction.COLOR_WHEEL);
	private static final Color RED = new Color(1, 0, 0);
	private static final Color ORANGE = new Color(1, 0.3, 0);
	private static final Color BLUE = new Color(0, 0, 1);
	private static final Color VIOLET = new Color(0.55, 0, 1);

	/** Like the example spot: white, four colors and a rotation range. */
	private static final Mode SPOT = new Mode("spot", List.of(
			channel(ChannelFunction.DIMMER),
			new ChannelDefinition(ChannelFunction.COLOR_WHEEL, Optional.empty(), 8, 0, false, List.of(
					range(0, 9, "White", "#ffffff"),
					range(10, 19, "Red", "#ff0000"),
					range(20, 29, "Blue", "#0033ff"),
					range(30, 39, "Green", "#00ff44"),
					range(40, 49, "Blue → cyan", "#0080ff"),
					new ChannelRange(50, 255, "Color wheel rotation", Optional.empty())), Optional.empty())));

	@Test
	void rgbFixturesMixAndTurnTheirOtherEmittersOff() {
		var rgbw = FixtureColors.of(mode(ChannelFunction.DIMMER, ChannelFunction.RED, ChannelFunction.GREEN,
				ChannelFunction.BLUE, ChannelFunction.WHITE));

		assertThat(rgbw.mixing()).isEqualTo(Mixing.RGB);
		var values = rgbw.valuesFor(ORANGE).orElseThrow();
		assertThat(values.get(Attribute.of(ChannelFunction.GREEN))).contains(0.3);
		assertThat(values.get(Attribute.of(ChannelFunction.WHITE))).contains(0.0);
	}

	@Test
	void cmyFixturesSubtract() {
		var cmy = FixtureColors.of(mode(ChannelFunction.CYAN, ChannelFunction.MAGENTA, ChannelFunction.YELLOW));

		assertThat(cmy.mixing()).isEqualTo(Mixing.CMY);
		var red = cmy.valuesFor(RED).orElseThrow();
		assertThat(red.get(Attribute.of(ChannelFunction.CYAN))).contains(0.0);
		assertThat(red.get(Attribute.of(ChannelFunction.MAGENTA))).contains(1.0);
		assertThat(red.get(Attribute.of(ChannelFunction.YELLOW))).contains(1.0);
	}

	@Test
	void aWheelTakesTheClosestColoredSlotOrNone() {
		var spot = FixtureColors.of(SPOT);

		assertThat(spot.mixing()).isEqualTo(Mixing.WHEEL);
		assertThat(spot.wheelSlots()).extracting(FixtureColors.WheelSlot::label)
				.as("transitions and slots without a color do not count")
				.containsExactly("White", "Red", "Blue", "Green");
		assertThat(spot.valuesFor(RED).orElseThrow().get(WHEEL).orElseThrow()).isCloseTo(14.5 / 255, within(1e-9));
		assertThat(spot.slotFor(ORANGE)).map(FixtureColors.WheelSlot::label).contains("Red");
		assertThat(spot.slotFor(BLUE)).map(FixtureColors.WheelSlot::label).contains("Blue");
		assertThat(spot.slotFor(VIOLET)).as("45° from blue is too far").isEmpty();
		assertThat(spot.slotFor(new Color(1, 1, 1))).as("white is no color to look for").isEmpty();
	}

	@Test
	void aFixtureWithoutColorsShowsNone() {
		var dimmer = FixtureColors.of(mode(ChannelFunction.DIMMER));

		assertThat(dimmer.canShowColors()).isFalse();
		assertThat(dimmer.valuesFor(RED)).isEmpty();
		assertThat(FixtureColors.parse("#0033ff")).contains(new Color(0, 0.2, 1));
		assertThat(FixtureColors.parse("blue")).isEmpty();
	}

	private static Mode mode(ChannelFunction... functions) {
		return new Mode("std", Arrays.stream(functions).map(FixtureColorsTest::channel).toList());
	}

	private static ChannelDefinition channel(ChannelFunction function) {
		return new ChannelDefinition(function, Optional.empty(), 8, 0, false, List.of(), Optional.empty());
	}

	private static ChannelRange range(int from, int to, String label, String color) {
		return new ChannelRange(from, to, label, Optional.of(color));
	}
}
