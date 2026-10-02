package de.vinzens.lumen.domain.project;

import java.util.Objects;

/**
 * A DMX universe (512 channels) of a project and its Art-Net target.
 */
public record Universe(long id, long projectId, UniverseSpec spec) {

	public Universe {
		Objects.requireNonNull(spec, "spec");
	}

	/** Display name, e.g. "Universe 1 – Stage". */
	public String displayName() {
		return "Universe " + spec.number() + spec.name().map(n -> " – " + n).orElse("");
	}
}
