package de.vinzens.lumen.domain.project;

import java.util.Objects;

/**
 * Ein DMX-Universe (512 Kanäle) eines Projekts und sein Art-Net-Ziel.
 */
public record Universe(long id, long projectId, UniverseSpec spec) {

	public Universe {
		Objects.requireNonNull(spec, "spec");
	}

	/** Anzeigename, z.B. „Universe 1 – Bühne“. */
	public String displayName() {
		return "Universe " + spec.number() + spec.name().map(n -> " – " + n).orElse("");
	}
}
