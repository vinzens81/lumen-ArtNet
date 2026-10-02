package de.vinzens.lumen.application;

/**
 * The requested object does not exist (or does not belong to the given project).
 */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String message) {
		super(message);
	}
}
