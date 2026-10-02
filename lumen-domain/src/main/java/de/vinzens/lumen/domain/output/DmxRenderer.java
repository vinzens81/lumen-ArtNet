package de.vinzens.lumen.domain.output;

import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelDefinition;
import de.vinzens.lumen.domain.library.ChannelFunction;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Maps normalized fixture values to DMX frames: channel offsets, 8/16 bit, defaults, inversion, the virtual
 * dimmer for fixtures without a dimmer channel and the grand master.
 */
public final class DmxRenderer {

	/** Channels scaled by the virtual dimmer of fixtures without a dimmer channel. */
	private static final Set<ChannelFunction> ADDITIVE_COLORS = EnumSet.of(
			ChannelFunction.RED, ChannelFunction.GREEN, ChannelFunction.BLUE, ChannelFunction.WHITE,
			ChannelFunction.WARM_WHITE, ChannelFunction.COLD_WHITE, ChannelFunction.AMBER, ChannelFunction.UV,
			ChannelFunction.LIME);

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);

	private DmxRenderer() {
	}

	/**
	 * Renders one frame per universe. Every given universe gets a frame, even without fixtures. Fixtures are
	 * written in list order, so later fixtures overwrite earlier ones where they overlap.
	 *
	 * @param master grand master (0.0–1.0), scales all intensities
	 */
	public static Map<Long, DmxFrame> render(Collection<Long> universeIds, Collection<RenderFixture> fixtures,
			Map<Long, FixtureValues> values, double master) {
		var builders = new HashMap<Long, DmxFrame.Builder>();
		universeIds.forEach(id -> builders.put(id, DmxFrame.builder()));

		for (var fixture : fixtures) {
			var builder = builders.get(fixture.universeId());
			if (builder != null) {
				renderFixture(builder, fixture, values.getOrDefault(fixture.fixtureId(), FixtureValues.EMPTY), master);
			}
		}

		var frames = new HashMap<Long, DmxFrame>();
		builders.forEach((id, builder) -> frames.put(id, builder.build()));
		return frames;
	}

	private static void renderFixture(DmxFrame.Builder builder, RenderFixture fixture, FixtureValues values,
			double master) {
		var mode = fixture.mode();
		var dimmer = values.get(DIMMER).orElse(0.0) * master;
		var virtualDimmer = !mode.hasDimmer();

		var channel = fixture.address();
		for (var definition : mode.channels()) {
			var raw = values.get(definition.attribute())
					.map(value -> (int) Math.round(value * definition.maxValue()))
					.orElse(definition.defaultValue());

			if (definition.function() == ChannelFunction.DIMMER) {
				raw = (int) Math.round(raw * master);
			} else if (virtualDimmer && ADDITIVE_COLORS.contains(definition.function())) {
				raw = (int) Math.round(raw * dimmer);
			}
			if (definition.invert()) {
				raw = definition.maxValue() - raw;
			}
			write(builder, channel, definition, raw);
			channel += definition.dmxChannelCount();
		}
	}

	private static void write(DmxFrame.Builder builder, int channel, ChannelDefinition definition, int raw) {
		if (definition.resolution() == ChannelDefinition.RESOLUTION_16_BIT) {
			builder.set(channel, raw >> 8);
			builder.set(channel + 1, raw & 0xFF);
		} else {
			builder.set(channel, raw);
		}
	}
}
