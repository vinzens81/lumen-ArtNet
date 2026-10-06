package de.vinzens.lumen.application;

import de.vinzens.lumen.application.output.port.in.ControlProgrammerUseCase;
import de.vinzens.lumen.domain.look.Look;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.output.PresetRefs;
import de.vinzens.lumen.domain.preset.Preset;

import java.util.Collection;
import java.util.Map;

/** A programmer that only holds a look, for tests. */
public class FakeProgrammer implements ControlProgrammerUseCase {

	public Look look = Look.EMPTY;

	@Override
	public Map<Long, FixtureValues> programmerValues() {
		return look.values();
	}

	@Override
	public PresetRefs programmerPresets() {
		return look.presets();
	}

	@Override
	public void loadLook(Look newLook) {
		look = newLook;
	}

	@Override
	public FixtureValues setValues(long fixtureId, FixtureValues values) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Map<Long, FixtureValues> setValues(Collection<Long> fixtureIds, FixtureValues values) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Map<Long, FixtureValues> setGroupValues(long groupId, FixtureValues values) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void applyPreset(Collection<Long> fixtureIds, Preset preset) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void presetChanged(Preset preset) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void presetDeleted(long presetId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void clearFixture(long fixtureId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void clearFixtures(Collection<Long> fixtureIds) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void clear() {
		look = Look.EMPTY;
	}
}
