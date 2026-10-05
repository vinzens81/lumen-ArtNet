package de.vinzens.lumen.application.library.port.in;

import de.vinzens.lumen.domain.library.FixtureType;

import java.util.List;

/**
 * Adds profiles from other fixture libraries to the Lumen library.
 */
public interface ImportFixtureTypeUseCase {

	/**
	 * Converts a fixture of the Open Fixture Library and saves it as a profile in the library.
	 *
	 * @param json         the "OFL JSON" download of the fixture
	 * @param manufacturer manufacturer name; OFL fixture files do not contain it
	 * @param replace      replace a profile with the same id; otherwise that is an error
	 */
	ImportResult importOfl(String json, String manufacturer, boolean replace);

	/**
	 * @param source   the new file, relative to the library
	 * @param warnings what got lost or simplified, and warnings of the library check
	 */
	record ImportResult(FixtureType fixtureType, String source, List<String> warnings) {

		public ImportResult {
			warnings = List.copyOf(warnings);
		}
	}
}
