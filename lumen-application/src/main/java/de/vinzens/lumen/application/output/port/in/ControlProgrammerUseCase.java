package de.vinzens.lumen.application.output.port.in;

import de.vinzens.lumen.domain.output.FixtureValues;

import java.util.Map;

/**
 * Live control of fixtures of the active project.
 */
public interface ControlProgrammerUseCase {

	/** Programmer values per fixture id. */
	Map<Long, FixtureValues> programmerValues();

	/**
	 * Sets attribute values of a fixture of the active project; other attributes keep their values.
	 *
	 * @return the resulting programmer values of the fixture
	 */
	FixtureValues setValues(long fixtureId, FixtureValues values);

	void clearFixture(long fixtureId);

	void clear();
}
