package de.vinzens.lumen.application.library.port.in;

public interface ReloadFixtureLibraryUseCase {

	/** Reads the fixture library again and applies it to the patch and the output. */
	void reload();
}
