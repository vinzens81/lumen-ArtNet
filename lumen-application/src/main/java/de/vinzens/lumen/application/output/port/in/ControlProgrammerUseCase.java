package de.vinzens.lumen.application.output.port.in;

import de.vinzens.lumen.domain.output.FixtureValues;

import java.util.Collection;
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

	/**
	 * Sets values for several fixtures at once, e.g. a selection or a group. Each fixture only receives the
	 * attributes it has; an attribute that none of the fixtures has is an error.
	 *
	 * @return the resulting programmer values of the given fixtures
	 */
	Map<Long, FixtureValues> setValues(Collection<Long> fixtureIds, FixtureValues values);

	/** Like {@link #setValues(Collection, FixtureValues)} for all members of a group of the active project. */
	Map<Long, FixtureValues> setGroupValues(long groupId, FixtureValues values);

	void clearFixture(long fixtureId);

	void clearFixtures(Collection<Long> fixtureIds);

	void clear();
}
