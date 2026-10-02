package de.vinzens.lumen.application.output.port.in;

import de.vinzens.lumen.domain.output.DmxFrame;
import de.vinzens.lumen.domain.output.FixtureValues;

import java.util.Map;
import java.util.Optional;

/**
 * Read access to the current DMX output of the active project.
 */
public interface QueryOutputUseCase {

	/** Current frame of the universe with the given number in the active project. */
	DmxFrame frame(int universeNumber);

	/** Attribute values per fixture as sent in the last frame, before the grand master. */
	Map<Long, FixtureValues> outputValues();

	/** The frame sent last for the universe with the given number; empty before the first frame. */
	Optional<DmxFrame> lastFrame(int universeNumber);
}
