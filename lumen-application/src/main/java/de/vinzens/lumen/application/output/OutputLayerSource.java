package de.vinzens.lumen.application.output;

import de.vinzens.lumen.domain.library.Mode;
import de.vinzens.lumen.domain.output.LayerOutput;

import de.vinzens.lumen.domain.output.OperatingMode;

import java.time.Instant;
import java.util.Map;

/**
 * Contributes an output layer on every render tick, e.g. the scene playback.
 */
@FunctionalInterface
public interface OutputLayerSource {

	/**
	 * @param modes modes of the patched fixtures of the active project, by fixture id
	 */
	LayerOutput layer(Instant now, Map<Long, Mode> modes);

	/** Whether the layer is part of the output in this operating mode. */
	default boolean activeIn(OperatingMode mode) {
		return true;
	}
}
