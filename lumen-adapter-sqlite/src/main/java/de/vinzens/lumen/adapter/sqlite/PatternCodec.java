package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.domain.composition.Chase;
import de.vinzens.lumen.domain.composition.ColorCycle;
import de.vinzens.lumen.domain.composition.Flicker;
import de.vinzens.lumen.domain.composition.Pattern;
import de.vinzens.lumen.domain.composition.PatternKind;
import de.vinzens.lumen.domain.composition.Rainbow;
import de.vinzens.lumen.domain.composition.StepSequence;
import de.vinzens.lumen.domain.composition.Wave;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/** Effects (chase, rainbow, …) as JSON: the record components of the pattern. */
final class PatternCodec {

	private static final Map<PatternKind, Class<? extends Pattern>> TYPES = Map.of(
			PatternKind.CHASE, Chase.class,
			PatternKind.COLOR_CYCLE, ColorCycle.class,
			PatternKind.RAINBOW, Rainbow.class,
			PatternKind.WAVE, Wave.class,
			PatternKind.FLICKER, Flicker.class);

	private final JsonMapper json = JsonMapper.builder().build();

	String write(Pattern effect) {
		if (effect instanceof StepSequence) {
			throw new IllegalArgumentException("a step sequence is stored as steps");
		}
		return json.writeValueAsString(effect);
	}

	Pattern read(PatternKind kind, String params) {
		var type = TYPES.get(kind);
		if (type == null) {
			throw new IllegalArgumentException("not an effect: " + kind.id());
		}
		return json.readValue(params, type);
	}

	/** The play mode stored in the parameters of an old step sequence. */
	StepSequence.PlayMode legacyPlayMode(String params) {
		return StepSequence.PlayMode.valueOf(json.readTree(params).get("playMode").asString());
	}
}
