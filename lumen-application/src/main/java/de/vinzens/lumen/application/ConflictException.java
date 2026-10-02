package de.vinzens.lumen.application;

/**
 * Aktion widerspricht dem aktuellen Zustand, z.B. ein Universe wird noch verwendet.
 */
public class ConflictException extends RuntimeException {

	public ConflictException(String message) {
		super(message);
	}
}
