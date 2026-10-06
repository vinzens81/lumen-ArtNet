package de.vinzens.lumen.domain.playback;

import de.vinzens.lumen.domain.composition.ActiveComposition;
import de.vinzens.lumen.domain.composition.StepSequence;
import de.vinzens.lumen.domain.output.FixtureValues;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Fixed values of a scene, as a composition with one step (what a recorded scene is). */
final class StaticLooks {

	private StaticLooks() {
	}

	/** @param id unique per scene, so the look of one scene never continues in another */
	static ActiveComposition of(long id, Map<Long, FixtureValues> values) {
		var sequence = new StepSequence(List.of(new StepSequence.Step(1, 0, values)), StepSequence.PlayMode.LOOP);
		return new ActiveComposition(id, Optional.of(id), sequence, sequence.ownFixtures().orElseThrow(), 1, 1, false);
	}
}
