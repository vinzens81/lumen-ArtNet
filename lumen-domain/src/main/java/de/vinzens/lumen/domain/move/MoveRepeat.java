package de.vinzens.lumen.domain.move;

import de.vinzens.lumen.domain.DomainValidationException;

import java.util.List;
import java.util.Locale;

/** How often a move runs through its figure. */
public enum MoveRepeat {
	/** round after round, without end */
	LOOP,
	/** once, then the fixtures stay at the end of the way */
	ONCE,
	/** there and back */
	PING_PONG;

	/** Lower-case id used in the API and the database, e.g. {@code ping_pong}. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static MoveRepeat fromId(String id) {
		for (var repeat : values()) {
			if (repeat.id().equals(id)) {
				return repeat;
			}
		}
		throw new DomainValidationException(List.of("repeat must be one of loop, once, ping_pong, was '" + id + "'"));
	}
}
