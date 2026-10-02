package de.vinzens.lumen.domain.library;

/**
 * Wie sich ein Kanal beim Szenenwechsel verhält.
 */
public enum FadeBehavior {
	/** Intensität: HTP, wird mit dem Szenen-Level skaliert. */
	INTENSITY,
	/** LTP, stufenlos vom Startwert zum Zielwert überblendet. */
	SMOOTH,
	/** LTP, springt beim GO auf den Zielwert. */
	SNAP
}
