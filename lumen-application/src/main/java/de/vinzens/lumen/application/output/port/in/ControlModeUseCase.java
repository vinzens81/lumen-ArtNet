package de.vinzens.lumen.application.output.port.in;

import de.vinzens.lumen.domain.output.OperatingMode;

public interface ControlModeUseCase {

	OperatingMode mode();

	/**
	 * Switching to show or party mode clears and locks the programmer. Both are only reachable from setup mode;
	 * switching directly between them is refused.
	 */
	OperatingMode setMode(OperatingMode mode);
}
