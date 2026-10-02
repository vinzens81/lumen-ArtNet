package de.vinzens.lumen.domain.project;

import java.util.Objects;

/**
 * Named set of fixtures, e.g. "Front light".
 */
public record Group(long id, long projectId, GroupSpec spec) {

	public Group {
		Objects.requireNonNull(spec, "spec");
	}
}
