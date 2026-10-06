package de.vinzens.lumen.domain.look;

import java.util.Objects;

/**
 * The first level of the show: a look of the project with a name – fixed values per fixture, and the presets they
 * come from. Compositions play looks as their steps.
 */
public record NamedLook(long id, long projectId, LookSpec spec, Look look) {

	public NamedLook {
		Objects.requireNonNull(spec, "spec");
		Objects.requireNonNull(look, "look");
	}
}
