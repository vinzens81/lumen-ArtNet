package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelDefinition;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.library.Mode;
import de.vinzens.lumen.domain.output.FixtureValues;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * How a fixture shows a color: by mixing red, green and blue, by subtracting cyan, magenta and yellow, or with a slot
 * of its color wheel. Turns a wanted color into the values the fixture needs.
 */
public final class FixtureColors {

	/** A slot further away in hue than this does not count as the color (red and orange are 18° apart). */
	static final double MAX_HUE_DISTANCE = 30;
	/** Below this saturation a slot is white or open, not a color. */
	static final double MIN_SATURATION = 0.4;

	private static final Attribute RED = Attribute.of(ChannelFunction.RED);
	private static final Attribute GREEN = Attribute.of(ChannelFunction.GREEN);
	private static final Attribute BLUE = Attribute.of(ChannelFunction.BLUE);
	private static final Attribute CYAN = Attribute.of(ChannelFunction.CYAN);
	private static final Attribute MAGENTA = Attribute.of(ChannelFunction.MAGENTA);
	private static final Attribute YELLOW = Attribute.of(ChannelFunction.YELLOW);
	private static final Attribute WHEEL = Attribute.of(ChannelFunction.COLOR_WHEEL);
	/** Other emitters a pure RGB color turns off. */
	private static final List<Attribute> OTHER_EMITTERS = List.of(ChannelFunction.WHITE, ChannelFunction.WARM_WHITE,
			ChannelFunction.COLD_WHITE, ChannelFunction.AMBER, ChannelFunction.UV, ChannelFunction.LIME)
			.stream().map(Attribute::of).toList();

	/** How the fixture makes colors; the first that applies wins. */
	public enum Mixing {
		RGB,
		CMY,
		WHEEL,
		NONE
	}

	/**
	 * A slot of a color wheel with a color of its own.
	 *
	 * @param value the normalized channel value in the middle of the slot
	 */
	public record WheelSlot(String label, Color color, double value) {

		public WheelSlot {
			Objects.requireNonNull(label, "label");
			Objects.requireNonNull(color, "color");
		}
	}

	private final Mixing mixing;
	private final List<Attribute> others;
	private final List<WheelSlot> slots;

	private FixtureColors(Mixing mixing, List<Attribute> others, List<WheelSlot> slots) {
		this.mixing = mixing;
		this.others = List.copyOf(others);
		this.slots = List.copyOf(slots);
	}

	public static FixtureColors of(Mode mode) {
		var attributes = mode.attributes();
		var others = OTHER_EMITTERS.stream().filter(attributes::contains).toList();
		if (attributes.containsAll(List.of(RED, GREEN, BLUE))) {
			return new FixtureColors(Mixing.RGB, others, List.of());
		}
		if (attributes.containsAll(List.of(CYAN, MAGENTA, YELLOW))) {
			return new FixtureColors(Mixing.CMY, List.of(), List.of());
		}
		var slots = mode.channels().stream()
				.filter(channel -> channel.function() == ChannelFunction.COLOR_WHEEL && channel.head() == 0)
				.findFirst()
				.map(FixtureColors::slotsOf)
				.orElse(List.of());
		return new FixtureColors(slots.isEmpty() ? Mixing.NONE : Mixing.WHEEL, List.of(), slots);
	}

	/**
	 * The slots of a wheel that show one color: they have a color, and are no transition between two ("Blue → cyan").
	 */
	private static List<WheelSlot> slotsOf(ChannelDefinition wheel) {
		var result = new ArrayList<WheelSlot>();
		for (var range : wheel.ranges()) {
			if (range.color().isEmpty() || range.label().contains("→")) {
				continue;
			}
			parse(range.color().get()).ifPresent(color -> result.add(new WheelSlot(range.label().strip(), color,
					(range.from() + range.to()) / 2.0 / 255)));
		}
		return result;
	}

	public Mixing mixing() {
		return mixing;
	}

	public boolean canShowColors() {
		return mixing != Mixing.NONE;
	}

	/** The colored slots of the wheel, empty for other fixtures. */
	public List<WheelSlot> wheelSlots() {
		return slots;
	}

	/**
	 * The values that show the color: red/green/blue with other emitters off, cyan/magenta/yellow, or the wheel slot
	 * closest in hue – none if no slot is close enough.
	 */
	public Optional<FixtureValues> valuesFor(Color color) {
		return switch (mixing) {
			case RGB -> {
				var values = new HashMap<Attribute, Double>();
				values.put(RED, color.red());
				values.put(GREEN, color.green());
				values.put(BLUE, color.blue());
				others.forEach(attribute -> values.put(attribute, 0.0));
				yield Optional.of(new FixtureValues(values));
			}
			case CMY -> Optional.of(cmy(color));
			case WHEEL -> slotFor(color).map(slot -> new FixtureValues(Map.of(WHEEL, slot.value())));
			case NONE -> Optional.empty();
		};
	}

	/** Cyan, magenta and yellow filters for an RGB color. */
	public static FixtureValues cmy(Color color) {
		return new FixtureValues(Map.of(CYAN, 1 - color.red(), MAGENTA, 1 - color.green(),
				YELLOW, 1 - color.blue()));
	}

	/**
	 * The colored slot closest in hue, if it is within {@link #MAX_HUE_DISTANCE}. White and grey have no hue, so they
	 * match no slot.
	 */
	public Optional<WheelSlot> slotFor(Color color) {
		if (saturation(color) < MIN_SATURATION) {
			return Optional.empty();
		}
		var hue = hue(color);
		return slots.stream()
				.filter(slot -> saturation(slot.color()) >= MIN_SATURATION)
				.filter(slot -> hueDistance(hue, hue(slot.color())) <= MAX_HUE_DISTANCE)
				.min(Comparator.comparingDouble(slot -> hueDistance(hue, hue(slot.color()))));
	}

	/** Hue in degrees, 0–360. */
	static double hue(Color color) {
		var max = Math.max(color.red(), Math.max(color.green(), color.blue()));
		var min = Math.min(color.red(), Math.min(color.green(), color.blue()));
		var delta = max - min;
		if (delta == 0) {
			return 0;
		}
		double hue;
		if (max == color.red()) {
			hue = ((color.green() - color.blue()) / delta) % 6;
		} else if (max == color.green()) {
			hue = (color.blue() - color.red()) / delta + 2;
		} else {
			hue = (color.red() - color.green()) / delta + 4;
		}
		return (hue * 60 + 360) % 360;
	}

	static double saturation(Color color) {
		var max = Math.max(color.red(), Math.max(color.green(), color.blue()));
		var min = Math.min(color.red(), Math.min(color.green(), color.blue()));
		return max == 0 ? 0 : (max - min) / max;
	}

	private static double hueDistance(double a, double b) {
		var distance = Math.abs(a - b) % 360;
		return Math.min(distance, 360 - distance);
	}

	/** A color written {@code #rrggbb}. */
	public static Optional<Color> parse(String hex) {
		if (hex == null || !hex.matches("#[0-9a-fA-F]{6}")) {
			return Optional.empty();
		}
		var value = Integer.parseInt(hex.substring(1).toLowerCase(Locale.ROOT), 16);
		return Optional.of(new Color(((value >> 16) & 255) / 255.0, ((value >> 8) & 255) / 255.0,
				(value & 255) / 255.0));
	}
}
