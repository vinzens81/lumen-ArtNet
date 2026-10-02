package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.output.FixtureValues;

import java.util.Map;

/** Attribute values patterns produce. */
final class Values {

	static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	static final Attribute RED = Attribute.of(ChannelFunction.RED);
	static final Attribute GREEN = Attribute.of(ChannelFunction.GREEN);
	static final Attribute BLUE = Attribute.of(ChannelFunction.BLUE);

	private Values() {
	}

	static FixtureValues intensity(double value) {
		return new FixtureValues(Map.of(DIMMER, clamp(value)));
	}

	static FixtureValues color(Color color) {
		return new FixtureValues(Map.of(RED, clamp(color.red()), GREEN, clamp(color.green()),
				BLUE, clamp(color.blue())));
	}

	static double clamp(double value) {
		return Math.min(1, Math.max(0, value));
	}

	/** Pseudo random number 0 ≤ result < 1, always the same for the same inputs (SplitMix64). */
	static double random(long seed, long a, long b) {
		var z = seed * 0x9E3779B97F4A7C15L + a * 0xBF58476D1CE4E5B9L + b * 0x94D049BB133111EBL;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		z = z ^ (z >>> 31);
		return (z >>> 11) * 0x1.0p-53;
	}
}
