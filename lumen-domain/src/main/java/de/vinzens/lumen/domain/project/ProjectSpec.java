package de.vinzens.lumen.domain.project;

import de.vinzens.lumen.domain.Validation;
import de.vinzens.lumen.domain.scene.FadeCurve;
import de.vinzens.lumen.domain.scene.MoveInDark;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Editable properties of a project.
 *
 * @param venue   venue, optional
 * @param fadeIn  global fade-in time of all scenes
 * @param fadeOut global fade-out time of all scenes
 * @param fadeCurve shape of all fades, unless a scene has its own
 * @param dimOut  time to dim out heads that move in the dark, unless a scene has its own
 * @param travel  time heads get to move in the dark, unless a scene has its own
 */
public record ProjectSpec(String name, Optional<String> venue, Duration fadeIn, Duration fadeOut,
		FadeCurve fadeCurve, Duration dimOut, Duration travel) {

	public static final Duration DEFAULT_FADE = Duration.ofSeconds(3);
	public static final Duration MAX_FADE = Duration.ofMinutes(10);

	public ProjectSpec {
		Objects.requireNonNull(venue, "venue");
		Objects.requireNonNull(fadeCurve, "fadeCurve");
		Objects.requireNonNull(dimOut, "dimOut");
		Objects.requireNonNull(travel, "travel");
		new Validation()
				.requireText(name, "name")
				.require(fadeIn != null && !fadeIn.isNegative() && fadeIn.compareTo(MAX_FADE) <= 0,
						"fadeIn must be between 0 and " + MAX_FADE.toMinutes() + " minutes")
				.require(fadeOut != null && !fadeOut.isNegative() && fadeOut.compareTo(MAX_FADE) <= 0,
						"fadeOut must be between 0 and " + MAX_FADE.toMinutes() + " minutes")
				.require(MoveInDark.inRange(dimOut), "dimOut must be between 0 and 60 s")
				.require(MoveInDark.inRange(travel), "travel must be between 0 and 60 s")
				.throwIfInvalid();
		name = name.strip();
		venue = venue.map(String::strip).filter(v -> !v.isEmpty());
	}

	/** A project with the default times for moving in the dark. */
	public ProjectSpec(String name, Optional<String> venue, Duration fadeIn, Duration fadeOut, FadeCurve fadeCurve) {
		this(name, venue, fadeIn, fadeOut, fadeCurve, MoveInDark.DEFAULT_DIM_OUT, MoveInDark.DEFAULT_TRAVEL);
	}

	/** A project with linear fades. */
	public ProjectSpec(String name, Optional<String> venue, Duration fadeIn, Duration fadeOut) {
		this(name, venue, fadeIn, fadeOut, FadeCurve.LINEAR);
	}
}
