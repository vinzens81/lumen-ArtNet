package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.Validation;

import java.util.HashSet;
import java.util.List;

/**
 * Which fixtures a generator runs an effect on, in which order.
 */
public sealed interface Target {

	/** Members of a group, in group order at the time the steps are generated. */
	record Group(long groupId) implements Target {
	}

	/** A fixed list of fixtures. */
	record Fixtures(List<Long> fixtureIds) implements Target {

		public Fixtures {
			fixtureIds = List.copyOf(fixtureIds);
			new Validation()
					.require(new HashSet<>(fixtureIds).size() == fixtureIds.size(), "a fixture is in the target twice")
					.throwIfInvalid();
		}
	}
}
