package de.vinzens.lumen.application.output.port.in;

import de.vinzens.lumen.domain.output.DmxFrame;

/**
 * Read access to the current DMX output of the active project.
 */
public interface QueryOutputUseCase {

	/** Current frame of the universe with the given number in the active project. */
	DmxFrame frame(int universeNumber);
}
