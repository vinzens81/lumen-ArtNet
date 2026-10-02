package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.Validation;

import java.util.HashSet;
import java.util.List;

/**
 * Which fixtures a composition runs on, in which order.
 */
public sealed interface Target {

	/** Members of a group, in group order; follows later changes of the group. */
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

	/** The fixtures recorded in the steps of a step sequence. */
	record Steps() implements Target {
	}
}
