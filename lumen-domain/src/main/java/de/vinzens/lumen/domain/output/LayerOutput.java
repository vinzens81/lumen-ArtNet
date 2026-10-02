package de.vinzens.lumen.domain.output;

import java.util.Map;

/**
 * Values contributed by one output layer (programmer, playback, later effects) for one render tick.
 *
 * @param priority higher priority wins for non-intensity attributes (LTP)
 */
public record LayerOutput(int priority, Map<Long, FixtureValues> fixtures) {

	public LayerOutput {
		fixtures = Map.copyOf(fixtures);
	}
}
