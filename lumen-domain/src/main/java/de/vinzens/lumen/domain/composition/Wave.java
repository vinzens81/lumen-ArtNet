package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.Validation;
import de.vinzens.lumen.domain.output.FixtureValues;

import java.util.Objects;

/**
 * Intensity rising and falling periodically. Every shape starts at {@code min}.
 *
 * @param spread 0 = all fixtures in sync, 1 = spread over one period (a wave running along the target)
 */
public record Wave(Shape shape, double periodSeconds, double min, double max, double spread) implements Pattern {

	public enum Shape {
		SINE, TRIANGLE, SAWTOOTH, SQUARE
	}

	public Wave {
		Objects.requireNonNull(shape, "shape");
		new Validation()
				.require(periodSeconds >= 0.05 && periodSeconds <= 3600, "periodSeconds must be between 0.05 and 3600")
				.require(min >= 0 && min <= 1 && max >= 0 && max <= 1, "min and max must be between 0 and 1")
				.require(spread >= 0 && spread <= 1, "spread must be between 0 and 1")
				.throwIfInvalid();
	}

	@Override
	public PatternKind kind() {
		return PatternKind.WAVE;
	}

	@Override
	public FixtureValues valuesAt(double seconds, int index, int count, long fixtureId) {
		// later fixtures lag behind, so the wave runs along the target
		var phase = Pattern.cycle(Math.max(0, seconds) / periodSeconds - Pattern.spreadOffset(spread, index, count));
		var level = switch (shape) {
			case SINE -> 0.5 - 0.5 * Math.cos(2 * Math.PI * phase);
			case TRIANGLE -> phase < 0.5 ? 2 * phase : 2 - 2 * phase;
			case SAWTOOTH -> phase;
			case SQUARE -> phase < 0.5 ? 0 : 1;
		};
		return Values.intensity(min + (max - min) * level);
	}
}
