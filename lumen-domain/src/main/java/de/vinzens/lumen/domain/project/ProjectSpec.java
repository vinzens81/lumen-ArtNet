package de.vinzens.lumen.domain.project;

import de.vinzens.lumen.domain.Validation;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Editable properties of a project.
 *
 * @param venue   venue, optional
 * @param fadeIn  global fade-in time of all scenes
 * @param fadeOut global fade-out time of all scenes
 */
public record ProjectSpec(String name, Optional<String> venue, Duration fadeIn, Duration fadeOut) {

	public static final Duration DEFAULT_FADE = Duration.ofSeconds(3);
	public static final Duration MAX_FADE = Duration.ofMinutes(10);

	public ProjectSpec {
		Objects.requireNonNull(venue, "venue");
		new Validation()
				.requireText(name, "name")
				.require(fadeIn != null && !fadeIn.isNegative() && fadeIn.compareTo(MAX_FADE) <= 0,
						"fadeIn must be between 0 and " + MAX_FADE.toMinutes() + " minutes")
				.require(fadeOut != null && !fadeOut.isNegative() && fadeOut.compareTo(MAX_FADE) <= 0,
						"fadeOut must be between 0 and " + MAX_FADE.toMinutes() + " minutes")
				.throwIfInvalid();
		name = name.strip();
		venue = venue.map(String::strip).filter(v -> !v.isEmpty());
	}
}
