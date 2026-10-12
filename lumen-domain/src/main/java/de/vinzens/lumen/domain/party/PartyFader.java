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
 * A gradient on a party field: touching a point of it sets the intensity or the color of its fixtures at once.
 *
 * @param kind       what the gradient sets
 * @param fixtureIds the fixtures it sets, in order
 */
public record PartyFader(Kind kind, List<Long> fixtureIds) {

	/** The colors run from red over orange, yellow, green, cyan and blue to violet – not round to red again. */
	public static final double HUE_RANGE = 5.0 / 6;

	static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	static final Attribute RED = Attribute.of(ChannelFunction.RED);
	static final Attribute GREEN = Attribute.of(ChannelFunction.GREEN);
	static final Attribute BLUE = Attribute.of(ChannelFunction.BLUE);

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
	 * The values at a point of the gradient: dimmer 0–100 %, or a fully saturated color (red/green/blue) from red at
	 * the left to violet at the right.
	 *
	 * @param position 0 = left end, 1 = right end
	 */
	public FixtureValues valuesAt(double position) {
		var value = clamp(position);
		return switch (kind) {
			case DIMMER -> new FixtureValues(Map.of(DIMMER, value));
			case COLOR -> {
				var color = colorAt(value);
				yield new FixtureValues(Map.of(RED, color.red(), GREEN, color.green(), BLUE, color.blue()));
			}
		};
	}

	/** The color at a point of a color gradient: red at the left, violet at the right. */
	public static Color colorAt(double position) {
		return Color.hsv(clamp(position) * HUE_RANGE, 1, 1);
	}

	private static double clamp(double position) {
		return Math.min(1, Math.max(0, position));
	}

	/** What a gradient sets. */
	public enum Kind {
		DIMMER,
		COLOR;

		/** Lower-case id used in the API and the database. */
		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Kind parse(String id) {
			for (var kind : values()) {
				if (kind.id().equals(id)) {
					return kind;
				}
			}
			throw new DomainValidationException(List.of("gradient must be dimmer or color, was '" + id + "'"));
		}
	}
}
