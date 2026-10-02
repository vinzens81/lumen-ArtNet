package de.vinzens.lumen.domain.project;

import de.vinzens.lumen.domain.Validation;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Bearbeitbare Eigenschaften eines Projekts.
 *
 * @param venue   Spielstätte, optional
 * @param fadeIn  globale Fade-In-Zeit aller Szenen
 * @param fadeOut globale Fade-Out-Zeit aller Szenen
 */
public record ProjectSpec(String name, Optional<String> venue, Duration fadeIn, Duration fadeOut) {

	public static final Duration DEFAULT_FADE = Duration.ofSeconds(3);
	public static final Duration MAX_FADE = Duration.ofMinutes(10);

	public ProjectSpec {
		Objects.requireNonNull(venue, "venue");
		new Validation()
				.requireText(name, "name")
				.require(fadeIn != null && !fadeIn.isNegative() && fadeIn.compareTo(MAX_FADE) <= 0,
						"fadeIn muss zwischen 0 und " + MAX_FADE.toMinutes() + " Minuten liegen")
				.require(fadeOut != null && !fadeOut.isNegative() && fadeOut.compareTo(MAX_FADE) <= 0,
						"fadeOut muss zwischen 0 und " + MAX_FADE.toMinutes() + " Minuten liegen")
				.throwIfInvalid();
		name = name.strip();
		venue = venue.map(String::strip).filter(v -> !v.isEmpty());
	}
}
