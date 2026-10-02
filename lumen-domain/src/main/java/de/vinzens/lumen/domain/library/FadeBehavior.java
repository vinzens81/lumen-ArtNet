package de.vinzens.lumen.domain.library;

/**
 * How a channel behaves during a scene change.
 */
public enum FadeBehavior {
	/** Intensity: HTP, scaled by the scene level. */
	INTENSITY,
	/** LTP, smoothly faded from the start value to the target value. */
	SMOOTH,
	/** LTP, jumps to the target value on GO. */
	SNAP
}
