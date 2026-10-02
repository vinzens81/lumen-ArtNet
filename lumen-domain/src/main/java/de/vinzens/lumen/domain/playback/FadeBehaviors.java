package de.vinzens.lumen.domain.playback;

import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.FadeBehavior;
import de.vinzens.lumen.domain.library.Mode;

import java.util.Map;

/**
 * Tells how an attribute of a fixture behaves in a crossfade, honoring {@code fade} overrides in the profile.
 */
@FunctionalInterface
public interface FadeBehaviors {

	/** Uses the default of each channel function, ignoring profile overrides. */
	FadeBehaviors DEFAULTS = (fixtureId, attribute) -> attribute.function().defaultFade();

	FadeBehavior of(long fixtureId, Attribute attribute);

	/** Behaviors from the modes of the patched fixtures. */
	static FadeBehaviors of(Map<Long, Mode> modes) {
		return (fixtureId, attribute) -> {
			var mode = modes.get(fixtureId);
			if (mode != null) {
				for (var channel : mode.channels()) {
					if (channel.attribute().equals(attribute)) {
						return channel.effectiveFadeBehavior();
					}
				}
			}
			return attribute.function().defaultFade();
		};
	}
}
