package de.vinzens.lumen.application.output.port.in;

import de.vinzens.lumen.domain.output.MasterState;

/**
 * Grand master and blackout.
 */
public interface ControlMasterUseCase {

	MasterState master();

	MasterState setMasterLevel(double level);

	MasterState setBlackout(boolean active);
}
