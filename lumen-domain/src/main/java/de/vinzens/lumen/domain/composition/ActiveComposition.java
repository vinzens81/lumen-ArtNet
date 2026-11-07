package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.look.Look;
import de.vinzens.lumen.domain.move.Figure;
import de.vinzens.lumen.domain.move.Move;
import de.vinzens.lumen.domain.move.MoveRepeat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A composition ready to play: its target resolved to an ordered list of fixtures.
 *
 * @param id       the composition, to keep its phase when the scene is edited during the show
 * @param sourceId what decides whether it continues seamlessly from scene to scene: the same composition
 * @param once     stands still at the end of its first run
 * @param perHead  every head of a multi-pixel fixture is an element of its own
 * @param fadeIn   its own fade-in time at GO, empty = the one of the transition
 * @param fadeOut  its own fade-out time when it leaves the stage, empty = the one of the transition
 */
public record ActiveComposition(long id, Optional<Long> sourceId, Pattern pattern, List<Long> fixtureIds,
		double level, double speedFactor, boolean restart, boolean once, boolean perHead, Optional<Duration> fadeIn,
		Optional<Duration> fadeOut) {

	/** Moves play with ids from here on, so they never meet a composition or look id. */
	public static final long MOVE_IDS = 1L << 40;

	public ActiveComposition {
		Objects.requireNonNull(sourceId, "sourceId");
		Objects.requireNonNull(pattern, "pattern");
		Objects.requireNonNull(fadeIn, "fadeIn");
		Objects.requireNonNull(fadeOut, "fadeOut");
		fixtureIds = List.copyOf(fixtureIds);
	}

	/** A composition with the fade times of the transition. */
	public ActiveComposition(long id, Optional<Long> sourceId, Pattern pattern, List<Long> fixtureIds, double level,
			double speedFactor, boolean restart, boolean once, boolean perHead) {
		this(id, sourceId, pattern, fixtureIds, level, speedFactor, restart, once, perHead, Optional.empty(),
				Optional.empty());
	}

	/**
	 * A composition ready to play with the given settings of its use (in a scene, on a key, on its own). It continues
	 * from scene to scene as long as it is the same composition.
	 *
	 * @param looks the looks of the project by id
	 */
	public static ActiveComposition of(Composition composition, Map<Long, Look> looks, double level, double speedFactor,
			boolean restart) {
		var sequence = composition.sequence(looks);
		return new ActiveComposition(composition.id(), Optional.of(composition.id()), sequence,
				sequence.ownFixtures().orElse(List.of()), level, speedFactor, restart,
				composition.spec().playMode() == PlayMode.ONCE, false);
	}

	/**
	 * A look used directly in a scene: it stays as it is. Its id is the negative look id, so it never meets a
	 * composition id, and the same look continues from one part of a scene to the next without moving.
	 */
	public static ActiveComposition ofLook(long lookId, Look look, double level) {
		var sequence = new StepSequence(List.of(new StepSequence.Step(1, 0, look.values())),
				StepSequence.PlayMode.LOOP);
		return new ActiveComposition(-lookId, Optional.of(-lookId), sequence, sequence.ownFixtures().orElse(List.of()),
				level, 1, false);
	}

	/**
	 * A move: its fixtures follow the figure. It continues from scene to scene and from one part of a scene to the
	 * next as long as it is the same move.
	 */
	public static ActiveComposition ofMove(Move move, Figure figure) {
		var id = MOVE_IDS + move.id();
		return new ActiveComposition(id, Optional.of(id), MovePattern.of(move.spec(), figure),
				move.spec().fixtureIds(), 1, 1, false, move.spec().repeat() == MoveRepeat.ONCE, false);
	}

	/** A composition on its own at full level and speed (previews, keys). */
	public static ActiveComposition of(Composition composition, Map<Long, Look> looks) {
		return of(composition, looks, 1, 1, false);
	}

	/** A composition on whole fixtures. */
	public ActiveComposition(long id, Optional<Long> sourceId, Pattern pattern, List<Long> fixtureIds, double level,
			double speedFactor, boolean restart, boolean once) {
		this(id, sourceId, pattern, fixtureIds, level, speedFactor, restart, once, false);
	}

	/** A repeating composition (for tests and previews). */
	public ActiveComposition(long id, Optional<Long> sourceId, Pattern pattern, List<Long> fixtureIds, double level,
			double speedFactor, boolean restart) {
		this(id, sourceId, pattern, fixtureIds, level, speedFactor, restart, false, false);
	}

	/** The same composition with its own fade times (empty = the ones of the transition). */
	public ActiveComposition withFades(Optional<Duration> newFadeIn, Optional<Duration> newFadeOut) {
		return new ActiveComposition(id, sourceId, pattern, fixtureIds, level, speedFactor, restart, once, perHead,
				newFadeIn, newFadeOut);
	}

	/** Pattern time for the given time since the start: scaled by the speed, held at the end when running once. */
	public double patternSeconds(double seconds) {
		return patternSeconds(seconds, fixtureIds.size());
	}

	/** Like {@link #patternSeconds(double)} for a run over {@code count} elements (fixtures or heads). */
	public double patternSeconds(double seconds, int count) {
		var scaled = seconds * speedFactor;
		if (once) {
			var end = pattern.onceSeconds(count);
			if (end.isPresent()) {
				return Math.min(scaled, end.get());
			}
		}
		return scaled;
	}

	/** Whether this copy continues the given running composition at GO instead of starting anew. */
	public boolean continues(ActiveComposition running) {
		return !restart && sourceId.isPresent() && sourceId.equals(running.sourceId())
				&& pattern.kind() == running.pattern().kind();
	}
}
