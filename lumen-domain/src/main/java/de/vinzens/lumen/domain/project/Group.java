package de.vinzens.lumen.domain.project;

import java.util.Objects;

/**
 * Benannte Menge von Geräten, z.B. „Frontlicht“.
 */
public record Group(long id, long projectId, GroupSpec spec) {

	public Group {
		Objects.requireNonNull(spec, "spec");
	}
}
