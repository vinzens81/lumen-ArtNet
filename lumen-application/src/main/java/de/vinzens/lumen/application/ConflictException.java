package de.vinzens.lumen.application;

/**
 * The action conflicts with the current state, e.g. a universe is still in use.
 */
public class ConflictException extends RuntimeException {

	public ConflictException(String message) {
		super(message);
	}
}
