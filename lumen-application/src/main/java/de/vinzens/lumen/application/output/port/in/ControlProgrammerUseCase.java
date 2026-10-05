package de.vinzens.lumen.application.output.port.in;

import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.output.PresetRefs;
import de.vinzens.lumen.domain.preset.Preset;
import de.vinzens.lumen.domain.scene.Look;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Live control of fixtures of the active project.
 */
public interface ControlProgrammerUseCase {

	/** Programmer values per fixture id. */
	Map<Long, FixtureValues> programmerValues();

	/** Presets the programmer values come from. */
	PresetRefs programmerPresets();

	/** Values and presets of the programmer, without fixtures that have no values. */
	default Look programmerLook() {
		var values = new HashMap<Long, FixtureValues>();
		programmerValues().forEach((fixtureId, fixtureValues) -> {
			if (!fixtureValues.isEmpty()) {
				values.put(fixtureId, fixtureValues);
			}
		});
		return new Look(values, programmerPresets());
	}

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

	/**
	 * Replaces the whole programmer, e.g. with the values of a scene. Fixtures that are not patched and
	 * attributes a fixture does not have (any more) are skipped.
	 */
	default void loadValues(Map<Long, FixtureValues> values) {
		loadLook(Look.of(values));
	}

	/** Like {@link #loadValues}, keeping the preset references, e.g. of a scene. */
	void loadLook(Look look);

	/**
	 * Sets the values of a preset on fixtures of the active project and remembers that they come from it. Fixtures
	 * the preset has no values for are skipped.
	 */
	void applyPreset(Collection<Long> fixtureIds, Preset preset);

	/** Fixtures in the programmer that refer to the preset take its new values. */
	void presetChanged(Preset preset);

	/** Fixtures in the programmer keep their values but no longer refer to the preset. */
	void presetDeleted(long presetId);

	void clearFixture(long fixtureId);

	void clearFixtures(Collection<Long> fixtureIds);

	void clear();
}
