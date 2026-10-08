package de.vinzens.lumen.domain.scene;

import de.vinzens.lumen.domain.Validation;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * "Move in dark" for the GO into a scene: heads that point somewhere else in the new scene dim out, move while dark
 * and dim in at their new place, instead of sweeping across the stage while lit.
 *
 * @param enabled whether the heads move in the dark at the GO into this scene
 * @param dimOut  time to dim out the heads that move, empty = the project's
 * @param travel  time the heads get to move while dark, empty = the project's
 */
public record MoveInDark(boolean enabled, Optional<Duration> dimOut, Optional<Duration> travel) {

	public static final MoveInDark OFF = new MoveInDark(false, Optional.empty(), Optional.empty());
	public static final Duration DEFAULT_DIM_OUT = Duration.ofMillis(500);
	public static final Duration DEFAULT_TRAVEL = Duration.ofMillis(1500);
	public static final Duration MAX = Duration.ofMinutes(1);

	public MoveInDark {
		Objects.requireNonNull(dimOut, "dimOut");
		Objects.requireNonNull(travel, "travel");
		new Validation()
				.require(dimOut.map(MoveInDark::inRange).orElse(true), "dimOut must be between 0 and 60 s")
				.require(travel.map(MoveInDark::inRange).orElse(true), "travel must be between 0 and 60 s")
				.throwIfInvalid();
	}

	/** Moving in the dark with the project's times. */
	public static MoveInDark on() {
		return new MoveInDark(true, Optional.empty(), Optional.empty());
	}

	public Duration dimOutOr(Duration projectDimOut) {
		return dimOut.orElse(projectDimOut);
	}

	public Duration travelOr(Duration projectTravel) {
		return travel.orElse(projectTravel);
	}

	public static boolean inRange(Duration duration) {
		return !duration.isNegative() && duration.compareTo(MAX) <= 0;
	}
}
