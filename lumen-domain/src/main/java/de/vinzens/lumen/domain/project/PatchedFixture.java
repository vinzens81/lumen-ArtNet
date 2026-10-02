package de.vinzens.lumen.domain.project;

import java.util.Objects;

/**
 * A concrete fixture in the project: fixture type from the library plus universe, address and position.
 */
public record PatchedFixture(long id, long projectId, FixtureSpec spec) {

	public PatchedFixture {
		Objects.requireNonNull(spec, "spec");
	}
}
