package de.vinzens.lumen.application.library.port.out;

import de.vinzens.lumen.domain.library.FixtureType;

import java.util.List;

/**
 * Fixture type files of the library: converting profiles of other libraries and saving new profiles.
 */
public interface FixtureTypeFiles {

	/**
	 * Converts a fixture of the Open Fixture Library ("OFL JSON" download).
	 *
	 * @param manufacturer the manufacturer name; OFL fixture files do not contain it
	 */
	Converted convertOfl(String json, String manufacturer);

	/**
	 * Writes a profile into the library.
	 *
	 * @param source  file relative to the library, e.g. {@code cameo/cameo-flat-par.yml}
	 * @param comment written at the top of the file
	 */
	void save(FixtureType type, String source, String comment);

	/** Whether a file exists in the library. */
	boolean exists(String source);

	/** @param warnings what got lost or simplified in the conversion */
	record Converted(FixtureType fixtureType, List<String> warnings) {

		public Converted {
			warnings = List.copyOf(warnings);
		}
	}
}
