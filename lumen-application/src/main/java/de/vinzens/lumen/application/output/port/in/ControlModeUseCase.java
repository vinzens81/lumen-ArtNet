package de.vinzens.lumen.application.output.port.in;

import de.vinzens.lumen.domain.output.OperatingMode;

public interface ControlModeUseCase {

	OperatingMode mode();

	/** Switching to show mode clears and locks the programmer. */
	OperatingMode setMode(OperatingMode mode);
}
