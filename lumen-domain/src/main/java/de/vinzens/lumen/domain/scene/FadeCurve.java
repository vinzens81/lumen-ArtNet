package de.vinzens.lumen.domain.scene;

import de.vinzens.lumen.domain.DomainValidationException;

import java.util.Arrays;
import java.util.List;

/**
 * Shape of a fade over time. A linear fade in DMX values looks fast at the start of a fade-in and slow at its end,
 * because the eye is more sensitive to changes in the dark; the other curves soften that.
 */
public enum FadeCurve {

	/** Constant speed in DMX values. */
	LINEAR("linear"),
	/** Starts and ends gently, fastest in the middle (smoothstep); also for colors and positions. */
	S_CURVE("s-curve"),
	/** Intensity follows the square of the time: softer in the dark, which looks even to the eye. */
	SQUARE("square");

	private final String id;

	FadeCurve(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public static FadeCurve parse(String id) {
		return Arrays.stream(values())
				.filter(curve -> curve.id.equals(id))
				.findFirst()
				.orElseThrow(() -> new DomainValidationException(List.of("fade curve '" + id + "' is unknown, use "
						+ Arrays.stream(values()).map(FadeCurve::id).toList())));
	}

	/**
	 * Intensity level for a linear level, e.g. {@code 1 - progress} of a fade-out.
	 *
	 * @param linear 0–1
	 */
	public double level(double linear) {
		return switch (this) {
			case LINEAR -> linear;
			case S_CURVE -> smoothstep(linear);
			case SQUARE -> linear * linear;
		};
	}

	/** How far colors, positions and other smooth attributes have moved to their new value. */
	public double progress(double linear) {
		return this == S_CURVE ? smoothstep(linear) : linear;
	}

	private static double smoothstep(double x) {
		return x * x * (3 - 2 * x);
	}
}
