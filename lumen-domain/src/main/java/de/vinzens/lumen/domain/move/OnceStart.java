package de.vinzens.lumen.domain.move;

import de.vinzens.lumen.domain.DomainValidationException;

import java.util.List;
import java.util.Locale;

/** How the fixtures of a move that runs once start when they have an offset. */
public enum OnceStart {
	/** all make the same journey from its start, each one later than the one before (a "snake") */
	SEQUENTIAL,
	/** all start at once, each at its own point further along the way, and stop at its end */
	TOGETHER;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static OnceStart fromId(String id) {
		for (var start : values()) {
			if (start.id().equals(id)) {
				return start;
			}
		}
		throw new DomainValidationException(List.of("onceStart must be sequential or together, was '" + id + "'"));
	}
}
