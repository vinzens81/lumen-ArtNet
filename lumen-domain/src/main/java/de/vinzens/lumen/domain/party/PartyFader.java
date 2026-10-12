package de.vinzens.lumen.domain.party;

import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.Validation;
import de.vinzens.lumen.domain.composition.Color;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.output.FixtureValues;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * A gradient or pad on a party field: touching a point of it sets the intensity, the color or the position (pan and
 * tilt) of its fixtures at once.
 *
 * @param kind       what the field sets
 * @param fixtureIds the fixtures it sets, in order
 */
public record PartyFader(Kind kind, List<Long> fixtureIds) {

	/** The colors run from red over orange, yellow, green, cyan and blue to violet – not round to red again. */
	public static final double HUE_RANGE = 5.0 / 6;

	static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	static final Attribute RED = Attribute.of(ChannelFunction.RED);
	static final Attribute GREEN = Attribute.of(ChannelFunction.GREEN);
	static final Attribute BLUE = Attribute.of(ChannelFunction.BLUE);
	static final Attribute PAN = Attribute.of(ChannelFunction.PAN);
	static final Attribute TILT = Attribute.of(ChannelFunction.TILT);

	public PartyFader {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(fixtureIds, "fixtureIds");
		fixtureIds = List.copyOf(fixtureIds);
		var ids = new HashSet<Long>();
		new Validation()
				.require(!fixtureIds.isEmpty(), "a gradient needs at least one fixture")
				.require(fixtureIds.stream().allMatch(ids::add), "a fixture is in the gradient twice")
				.throwIfInvalid();
	}

	/** The same gradient for other fixtures. */
	public PartyFader withFixtureIds(List<Long> newFixtureIds) {
		return new PartyFader(kind, newFixtureIds);
	}

	/**
	 * The values at a point: dimmer 0–100 % or a fully saturated color (red/green/blue) from red at the left to violet
	 * at the right – both only along x –, or pan (x, from the left) and tilt (y, from the bottom) over their whole
	 * range.
	 */
	public FixtureValues valuesAt(PartyPoint point) {
		return switch (kind) {
			case DIMMER -> new FixtureValues(Map.of(DIMMER, point.x()));
			case COLOR -> {
				var color = colorAt(point.x());
				yield new FixtureValues(Map.of(RED, color.red(), GREEN, color.green(), BLUE, color.blue()));
			}
			case PAN_TILT -> new FixtureValues(Map.of(PAN, point.x(), TILT, point.y()));
		};
	}

	/** Where the field starts before it is touched: a gradient at its right end, a pad in the middle. */
	public PartyPoint firstPoint() {
		return kind == Kind.PAN_TILT ? PartyPoint.CENTER : PartyPoint.along(1);
	}

	/** The color at a point of a color gradient: red at the left, violet at the right. */
	public static Color colorAt(double position) {
		return Color.hsv(clamp(position) * HUE_RANGE, 1, 1);
	}

	private static double clamp(double position) {
		return Math.min(1, Math.max(0, position));
	}

	/** What a gradient or pad sets. */
	public enum Kind {
		DIMMER,
		COLOR,
		/** a pad: pan along x, tilt along y */
		PAN_TILT;

		/** Lower-case id used in the API and the database, e.g. {@code pan-tilt}. */
		public String id() {
			return name().toLowerCase(Locale.ROOT).replace('_', '-');
		}

		public static Kind parse(String id) {
			for (var kind : values()) {
				if (kind.id().equals(id)) {
					return kind;
				}
			}
			throw new DomainValidationException(List.of("gradient must be dimmer, color or pan-tilt, was '" + id + "'"));
		}
	}
}
