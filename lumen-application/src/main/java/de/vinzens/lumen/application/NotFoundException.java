package de.vinzens.lumen.application;

/**
 * Angefragtes Objekt existiert nicht (oder gehört nicht zum angegebenen Projekt).
 */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String message) {
		super(message);
	}
}
