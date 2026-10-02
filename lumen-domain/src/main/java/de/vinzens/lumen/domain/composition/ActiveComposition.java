package de.vinzens.lumen.domain.composition;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A composition ready to play: its target resolved to an ordered list of fixtures.
 *
 * @param id       the copy in the scene, to keep its phase when the scene is edited during the show
 * @param sourceId origin in the library; copies of the same origin continue seamlessly from scene to scene
 * @param once     stands still at the end of its first run
 */
public record ActiveComposition(long id, Optional<Long> sourceId, Pattern pattern, List<Long> fixtureIds,
		double level, double speedFactor, boolean restart, boolean once) {

	public ActiveComposition {
		Objects.requireNonNull(sourceId, "sourceId");
		Objects.requireNonNull(pattern, "pattern");
		fixtureIds = List.copyOf(fixtureIds);
	}

	/** Resolves a composition whose target is the given ordered fixtures (ignored for step sequences). */
	public static ActiveComposition of(Composition composition, List<Long> targetFixtures) {
		var spec = composition.spec();
		var fixtures = spec.pattern().ownFixtures().orElse(targetFixtures);
		return new ActiveComposition(composition.id(), composition.sourceId(), spec.pattern(), fixtures, spec.level(),
				spec.speedFactor(), spec.restart(), spec.once());
	}

	/** A repeating composition (for tests and previews). */
	public ActiveComposition(long id, Optional<Long> sourceId, Pattern pattern, List<Long> fixtureIds, double level,
			double speedFactor, boolean restart) {
		this(id, sourceId, pattern, fixtureIds, level, speedFactor, restart, false);
	}

	/** Pattern time for the given time since the start: scaled by the speed, held at the end when running once. */
	public double patternSeconds(double seconds) {
		var scaled = seconds * speedFactor;
		if (once) {
			var end = pattern.onceSeconds(fixtureIds.size());
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
