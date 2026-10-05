package de.vinzens.lumen.domain.output;

import de.vinzens.lumen.domain.library.AttributeGroup;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Live values set by the operator in setup mode, with the presets they come from. Immutable; every change returns
 * a new programmer.
 */
public record Programmer(Map<Long, FixtureValues> fixtures, PresetRefs presets) {

	public static final Programmer EMPTY = new Programmer(Map.of());

	public Programmer {
		fixtures = Map.copyOf(fixtures);
		Objects.requireNonNull(presets, "presets");
		presets = presets.only(fixtures.keySet());
	}

	public Programmer(Map<Long, FixtureValues> fixtures) {
		this(fixtures, PresetRefs.NONE);
	}

	/**
	 * Sets values for a fixture; other attributes of the fixture keep their programmer values. The fixture no longer
	 * refers to presets of the groups the values belong to.
	 */
	public Programmer with(long fixtureId, FixtureValues values) {
		var updated = new HashMap<>(fixtures);
		updated.merge(fixtureId, values, FixtureValues::with);
		var touched = EnumSet.noneOf(AttributeGroup.class);
		values.values().keySet().forEach(attribute -> touched.add(AttributeGroup.of(attribute.function())));
		return new Programmer(updated, presets.without(fixtureId, touched));
	}

	/** Sets the values of a preset for a fixture and remembers that they come from it. */
	public Programmer withPreset(long fixtureId, FixtureValues values, AttributeGroup group, long presetId) {
		var programmer = with(fixtureId, values);
		return new Programmer(programmer.fixtures, programmer.presets.with(fixtureId, group, presetId));
	}

	public Programmer without(long fixtureId) {
		var updated = new HashMap<>(fixtures);
		updated.remove(fixtureId);
		return new Programmer(updated, presets.withoutFixture(fixtureId));
	}

	public FixtureValues values(long fixtureId) {
		return fixtures.getOrDefault(fixtureId, FixtureValues.EMPTY);
	}

	/** The programmer as an output layer. */
	public LayerOutput asLayer(int priority) {
		return new LayerOutput(priority, fixtures);
	}
}
