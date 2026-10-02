package de.vinzens.lumen.domain.output;

import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelDefinition;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.library.Mode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DmxRendererTest {

	private static final long UNIVERSE = 1;

	private static final Mode RGBW_8CH = new Mode("8ch", List.of(
			channel(ChannelFunction.DIMMER), channel(ChannelFunction.RED), channel(ChannelFunction.GREEN),
			channel(ChannelFunction.BLUE), channel(ChannelFunction.WHITE), channel(ChannelFunction.STROBE),
			channel(ChannelFunction.PROGRAM), generic("Speed", 42)));

	private static final Mode RGB_NO_DIMMER = new Mode("3ch", List.of(
			channel(ChannelFunction.RED), channel(ChannelFunction.GREEN), channel(ChannelFunction.BLUE)));

	private static final Mode MOVING_HEAD = new Mode("6ch", List.of(
			sixteenBit(ChannelFunction.PAN, 32_768), sixteenBit(ChannelFunction.TILT, 32_768),
			channel(ChannelFunction.DIMMER), inverted(ChannelFunction.FOCUS)));

	@Test
	void writesValuesAtTheirOffsets() {
		var frame = render(fixture(1, 10, RGBW_8CH), Map.of(1L, values(
				ChannelFunction.DIMMER, 1.0, ChannelFunction.RED, 1.0, ChannelFunction.BLUE, 0.5)));

		assertThat(frame.get(10)).isEqualTo(255);
		assertThat(frame.get(11)).isEqualTo(255);
		assertThat(frame.get(12)).isZero();
		assertThat(frame.get(13)).isEqualTo(128);
		assertThat(frame.get(17)).as("generic default").isEqualTo(42);
		assertThat(frame.get(9)).isZero();
		assertThat(frame.get(18)).isZero();
	}

	@Test
	void genericChannelsAreAddressedByName() {
		var values = new FixtureValues(Map.of(Attribute.generic("Speed"), 1.0));

		assertThat(render(fixture(1, 1, RGBW_8CH), Map.of(1L, values)).get(8)).isEqualTo(255);
	}

	@Test
	void virtualDimmerScalesColorsOfFixturesWithoutDimmerChannel() {
		var mode = RGB_NO_DIMMER;
		var dark = render(fixture(1, 1, mode), Map.of(1L, values(ChannelFunction.RED, 1.0)));
		var half = render(fixture(1, 1, mode), Map.of(1L, values(ChannelFunction.RED, 1.0, ChannelFunction.DIMMER, 0.5)));

		assertThat(dark.get(1)).as("without dimmer the fixture stays dark").isZero();
		assertThat(half.get(1)).isEqualTo(128);
		assertThat(mode.attributes()).first().isEqualTo(Attribute.of(ChannelFunction.DIMMER));
	}

	@Test
	void sixteenBitChannelsUseCoarseAndFine() {
		var frame = render(fixture(1, 1, MOVING_HEAD), Map.of(1L, values(ChannelFunction.PAN, 1.0)));

		assertThat(frame.get(1)).isEqualTo(255);
		assertThat(frame.get(2)).isEqualTo(255);
		assertThat(frame.get(3)).as("tilt default 32768 coarse").isEqualTo(128);
		assertThat(frame.get(4)).as("tilt default 32768 fine").isZero();
	}

	@Test
	void invertedChannelsAreMirrored() {
		var frame = render(fixture(1, 1, MOVING_HEAD), Map.of(1L, values(ChannelFunction.FOCUS, 0.2)));

		assertThat(frame.get(6)).isEqualTo(255 - 51);
	}

	@Test
	void grandMasterScalesRealAndVirtualDimmers() {
		var fixtures = List.of(fixture(1, 1, RGBW_8CH), fixture(2, 20, RGB_NO_DIMMER));
		var values = Map.of(
				1L, values(ChannelFunction.DIMMER, 1.0, ChannelFunction.RED, 1.0),
				2L, values(ChannelFunction.DIMMER, 1.0, ChannelFunction.RED, 1.0));

		var frame = DmxRenderer.render(List.of(UNIVERSE), fixtures, values, 0.5).get(UNIVERSE);

		assertThat(frame.get(1)).as("real dimmer").isEqualTo(128);
		assertThat(frame.get(2)).as("color of fixture with dimmer is not scaled").isEqualTo(255);
		assertThat(frame.get(20)).as("color scaled by virtual dimmer").isEqualTo(128);
	}

	@Test
	void everyUniverseGetsAFrameAndFixturesBeyond512AreClipped() {
		var frames = DmxRenderer.render(List.of(1L, 2L), List.of(fixture(1, 510, RGBW_8CH)),
				Map.of(1L, values(ChannelFunction.DIMMER, 1.0)), 1.0);

		assertThat(frames).containsOnlyKeys(1L, 2L);
		assertThat(frames.get(2L)).isEqualTo(DmxFrame.BLACK);
		assertThat(frames.get(1L).get(510)).isEqualTo(255);
	}

	private static DmxFrame render(RenderFixture fixture, Map<Long, FixtureValues> values) {
		return DmxRenderer.render(List.of(UNIVERSE), List.of(fixture), values, 1.0).get(UNIVERSE);
	}

	private static RenderFixture fixture(long id, int address, Mode mode) {
		return new RenderFixture(id, UNIVERSE, address, mode);
	}

	private static FixtureValues values(Object... functionValuePairs) {
		var map = new java.util.HashMap<Attribute, Double>();
		for (int i = 0; i < functionValuePairs.length; i += 2) {
			map.put(Attribute.of((ChannelFunction) functionValuePairs[i]), (Double) functionValuePairs[i + 1]);
		}
		return new FixtureValues(map);
	}

	private static ChannelDefinition channel(ChannelFunction function) {
		return new ChannelDefinition(function, Optional.empty(), 8, 0, false, List.of(), Optional.empty());
	}

	private static ChannelDefinition generic(String name, int defaultValue) {
		return new ChannelDefinition(ChannelFunction.GENERIC, Optional.of(name), 8, defaultValue, false, List.of(),
				Optional.empty());
	}

	private static ChannelDefinition sixteenBit(ChannelFunction function, int defaultValue) {
		return new ChannelDefinition(function, Optional.empty(), 16, defaultValue, false, List.of(), Optional.empty());
	}

	private static ChannelDefinition inverted(ChannelFunction function) {
		return new ChannelDefinition(function, Optional.empty(), 8, 0, true, List.of(), Optional.empty());
	}
}
