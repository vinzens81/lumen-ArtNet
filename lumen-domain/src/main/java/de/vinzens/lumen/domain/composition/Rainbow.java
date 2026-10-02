package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.Validation;
import de.vinzens.lumen.domain.output.FixtureValues;

import java.util.Optional;

/**
 * Runs through all hues.
 *
 * @param cycleSeconds time for one round through the color circle
 * @param spread       0 = all fixtures show the same hue, 1 = the fixtures show the whole color circle
 * @param intensity    used for fixtures without a base intensity
 */
public record Rainbow(double cycleSeconds, double saturation, double spread, double intensity) implements Pattern {

	public Rainbow {
		new Validation()
				.require(cycleSeconds >= 0.1 && cycleSeconds <= 3600, "cycleSeconds must be between 0.1 and 3600")
				.require(saturation >= 0 && saturation <= 1, "saturation must be between 0 and 1")
				.require(spread >= 0 && spread <= 1, "spread must be between 0 and 1")
				.require(intensity >= 0 && intensity <= 1, "intensity must be between 0 and 1")
				.throwIfInvalid();
	}

	@Override
	public PatternKind kind() {
		return PatternKind.RAINBOW;
	}

	@Override
	public FixtureValues valuesAt(double seconds, int index, int count, long fixtureId) {
		var hue = Pattern.cycle(Math.max(0, seconds) / cycleSeconds + Pattern.spreadOffset(spread, index, count));
		return Values.color(Color.hsv(hue, saturation, 1));
	}

	@Override
	public Optional<Double> fallbackIntensity() {
		return Optional.of(intensity);
	}
}
