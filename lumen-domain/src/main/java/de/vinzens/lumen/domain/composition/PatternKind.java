package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.DomainValidationException;

import java.util.List;
import java.util.Locale;

public enum PatternKind {
	CHASE, COLOR_CYCLE, RAINBOW, WAVE, FLICKER, STEPS;

	/** Lower-case id used in the API and the database, e.g. {@code color_cycle}. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static PatternKind fromId(String id) {
		for (var kind : values()) {
			if (kind.id().equals(id)) {
				return kind;
			}
		}
		throw new DomainValidationException(List.of(
				"kind must be one of chase, color_cycle, rainbow, wave, flicker, steps, was '" + id + "'"));
	}
}
