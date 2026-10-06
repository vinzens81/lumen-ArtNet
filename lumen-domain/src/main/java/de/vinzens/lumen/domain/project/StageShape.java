package de.vinzens.lumen.domain.project;

import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.Validation;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * How a fixture is drawn in the stage view.
 *
 * @param kind     the drawn form
 * @param rotation clockwise in degrees (0–359), 0 = lying across the stage
 * @param length   length of a bar or strip as a fraction of the stage width (0.02–1); ignored for spots
 */
public record StageShape(Kind kind, int rotation, double length) {

	public static final double DEFAULT_LENGTH = 0.15;
	public static final StageShape DEFAULT = new StageShape(Kind.AUTO, 0, DEFAULT_LENGTH);

	public StageShape {
		Objects.requireNonNull(kind, "kind");
		new Validation()
				.requireRange(rotation, 0, 359, "stageShape.rotation")
				.require(length >= 0.02 && length <= 1,
						"stageShape.length must be between 0.02 and 1, was " + length)
				.throwIfInvalid();
	}

	public enum Kind {
		/** spot for single-cell fixtures, bar for bars and multi-pixel fixtures */
		AUTO("auto"),
		/** round lens with a pool of light */
		SPOT("spot"),
		/** LED bar: a rectangle with one segment per head */
		BAR("bar"),
		/** LED strip: a thin line of pixels */
		STRIP("strip");

		private final String id;

		Kind(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}

		public static Optional<Kind> fromId(String id) {
			return Arrays.stream(values()).filter(k -> k.id.equals(id)).findFirst();
		}

		/** Like {@link #fromId}, but with a readable validation error for unknown ids. */
		public static Kind parse(String id) {
			return fromId(id).orElseThrow(() -> new DomainValidationException(List.of(
					"stageShape.kind must be one of auto, spot, bar, strip, was '" + id + "'")));
		}
	}
}
