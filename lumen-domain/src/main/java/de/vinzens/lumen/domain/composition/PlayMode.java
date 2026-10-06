package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.DomainValidationException;

import java.util.List;
import java.util.Locale;

/** How a composition runs through its steps. */
public enum PlayMode {
	/** from the first to the last step, then again */
	LOOP,
	/** there and back */
	PING_PONG,
	/** once through, then the last step stays (e.g. a sunrise) */
	ONCE;

	/** Lower-case id used in the API and the database, e.g. {@code ping_pong}. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static PlayMode fromId(String id) {
		for (var mode : values()) {
			if (mode.id().equals(id)) {
				return mode;
			}
		}
		throw new DomainValidationException(List.of("playMode must be one of loop, ping_pong, once, was '" + id + "'"));
	}

	StepSequence.PlayMode sequenceMode() {
		return this == PING_PONG ? StepSequence.PlayMode.PING_PONG : StepSequence.PlayMode.LOOP;
	}
}
