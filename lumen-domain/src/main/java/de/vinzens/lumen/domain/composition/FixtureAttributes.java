package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.Mode;

import java.util.Map;

/**
 * Which attributes a fixture has: compositions only set what a fixture can do.
 */
@FunctionalInterface
public interface FixtureAttributes {

	/** Every fixture has every attribute (for tests). */
	FixtureAttributes ALL = (fixtureId, attribute) -> true;

	boolean has(long fixtureId, Attribute attribute);

	static FixtureAttributes of(Map<Long, Mode> modes) {
		return (fixtureId, attribute) -> {
			var mode = modes.get(fixtureId);
			return mode != null && mode.attributes().contains(attribute);
		};
	}
}
