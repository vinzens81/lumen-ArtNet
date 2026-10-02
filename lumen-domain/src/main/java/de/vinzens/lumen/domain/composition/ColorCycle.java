package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.Validation;
import de.vinzens.lumen.domain.output.FixtureValues;

import java.util.List;
import java.util.Optional;

/**
 * Steps through colors: each color is held, then fades into the next one.
 *
 * @param spread    0 = all fixtures show the same color, 1 = the fixtures are spread over one full cycle
 * @param intensity used for fixtures without a base intensity
 */
public record ColorCycle(List<Color> colors, double holdSeconds, double fadeSeconds, double spread, double intensity)
		implements Pattern {

	public ColorCycle {
		colors = List.copyOf(colors);
		new Validation()
				.require(colors.size() >= 2, "a color cycle needs at least 2 colors")
				.require(holdSeconds >= 0 && fadeSeconds >= 0 && holdSeconds + fadeSeconds >= 0.05,
						"hold and fade must not be negative and together at least 0.05 s")
				.require(spread >= 0 && spread <= 1, "spread must be between 0 and 1")
				.require(intensity >= 0 && intensity <= 1, "intensity must be between 0 and 1")
				.throwIfInvalid();
	}

	@Override
	public PatternKind kind() {
		return PatternKind.COLOR_CYCLE;
	}

	@Override
	public FixtureValues valuesAt(double seconds, int index, int count, long fixtureId) {
		var period = holdSeconds + fadeSeconds;
		var cycle = period * colors.size();
		var t = Math.max(0, seconds) + Pattern.spreadOffset(spread, index, count) * cycle;
		var position = t / period;
		var current = (int) Math.floorMod((long) Math.floor(position), colors.size());
		var within = (position - Math.floor(position)) * period;
		var color = colors.get(current);
		if (within > holdSeconds && fadeSeconds > 0) {
			color = color.blend(colors.get((current + 1) % colors.size()), (within - holdSeconds) / fadeSeconds);
		}
		return Values.color(color);
	}

	@Override
	public Optional<Double> fallbackIntensity() {
		return Optional.of(intensity);
	}
}
