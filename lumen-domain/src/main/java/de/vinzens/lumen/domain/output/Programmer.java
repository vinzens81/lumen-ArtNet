package de.vinzens.lumen.domain.output;

import java.util.HashMap;
import java.util.Map;

/**
 * Live values set by the operator in setup mode. Immutable; every change returns a new programmer.
 */
public record Programmer(Map<Long, FixtureValues> fixtures) {

	public static final Programmer EMPTY = new Programmer(Map.of());

	public Programmer {
		fixtures = Map.copyOf(fixtures);
	}

	/** Sets values for a fixture; other attributes of the fixture keep their programmer values. */
	public Programmer with(long fixtureId, FixtureValues values) {
		var updated = new HashMap<>(fixtures);
		updated.merge(fixtureId, values, FixtureValues::with);
		return new Programmer(updated);
	}

	public Programmer without(long fixtureId) {
		var updated = new HashMap<>(fixtures);
		updated.remove(fixtureId);
		return new Programmer(updated);
	}

	public FixtureValues values(long fixtureId) {
		return fixtures.getOrDefault(fixtureId, FixtureValues.EMPTY);
	}

	/** The programmer as an output layer. */
	public LayerOutput asLayer(int priority) {
		return new LayerOutput(priority, fixtures);
	}
}
