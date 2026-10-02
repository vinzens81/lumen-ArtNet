package de.vinzens.lumen.domain.composition;

public enum IntensityMode {
	/** The pattern sets the intensity; with a base intensity the higher one wins (HTP). */
	ABSOLUTE,
	/** The pattern scales the base intensity, e.g. a flickering candle around the programmed level. */
	RELATIVE
}
