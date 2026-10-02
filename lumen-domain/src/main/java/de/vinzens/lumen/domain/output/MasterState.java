package de.vinzens.lumen.domain.output;

import de.vinzens.lumen.domain.Validation;

/**
 * Grand master and blackout. Both scale all intensities as the very last step before the output.
 *
 * @param level    grand master level (0.0–1.0)
 * @param blackout everything dark while active, the level is kept for when it is released
 */
public record MasterState(double level, boolean blackout) {

	public static final MasterState FULL = new MasterState(1.0, false);

	public MasterState {
		new Validation()
				.require(!Double.isNaN(level) && level >= 0 && level <= 1,
						"level must be between 0 and 1, was " + level)
				.throwIfInvalid();
	}

	/** The factor applied to all intensities. */
	public double effectiveLevel() {
		return blackout ? 0 : level;
	}

	public MasterState withLevel(double newLevel) {
		return new MasterState(newLevel, blackout);
	}

	public MasterState withBlackout(boolean active) {
		return new MasterState(level, active);
	}
}
