package de.vinzens.lumen.domain.project;

import java.time.Instant;
import java.util.Objects;

/**
 * A project, e.g. a play at a venue. At most one project is active.
 */
public record Project(long id, ProjectSpec spec, boolean active, Instant createdAt, Instant updatedAt) {

	public Project {
		Objects.requireNonNull(spec, "spec");
		Objects.requireNonNull(createdAt, "createdAt");
		Objects.requireNonNull(updatedAt, "updatedAt");
	}
}
