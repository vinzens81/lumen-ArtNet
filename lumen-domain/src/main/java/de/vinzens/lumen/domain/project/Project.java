package de.vinzens.lumen.domain.project;

import java.time.Instant;
import java.util.Objects;

/**
 * Ein Projekt, z.B. ein Theaterstück an einer Spielstätte. Genau ein Projekt kann aktiv sein.
 */
public record Project(long id, ProjectSpec spec, boolean active, Instant createdAt, Instant updatedAt) {

	public Project {
		Objects.requireNonNull(spec, "spec");
		Objects.requireNonNull(createdAt, "createdAt");
		Objects.requireNonNull(updatedAt, "updatedAt");
	}
}
