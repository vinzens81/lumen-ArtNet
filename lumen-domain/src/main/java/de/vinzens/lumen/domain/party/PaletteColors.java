package de.vinzens.lumen.domain.party;

import de.vinzens.lumen.domain.composition.Color;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelDefinition;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.library.ChannelRange;
import de.vinzens.lumen.domain.library.Mode;
import de.vinzens.lumen.domain.output.FixtureValues;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;

/**
 * Turns a palette into the color channels a fixture has:
 * <ul>
 * <li>a dedicated emitter (amber, UV, warm white) is used alone where the fixture has it</li>
 * <li>RGB fixtures get the color; with a white channel the common part of R, G and B goes to white</li>
 * <li>all other color emitters of the fixture are set to 0, so switching palettes leaves nothing behind</li>
 * <li>fixtures without RGB get the color wheel slot whose display color is closest</li>
 * </ul>
 */
public final class PaletteColors {

	private static final Set<ChannelFunction> EMITTERS = EnumSet.of(ChannelFunction.RED, ChannelFunction.GREEN,
			ChannelFunction.BLUE, ChannelFunction.WHITE, ChannelFunction.WARM_WHITE, ChannelFunction.COLD_WHITE,
			ChannelFunction.AMBER, ChannelFunction.UV, ChannelFunction.LIME);

	private PaletteColors() {
	}

	/** The color values of the palette for a fixture of the mode; empty if it has no way to show a color. */
	public static FixtureValues of(Palette palette, Mode mode) {
		var functions = EnumSet.noneOf(ChannelFunction.class);
		mode.channels().forEach(channel -> functions.add(channel.function()));
		var values = new HashMap<Attribute, Double>();
		for (var function : functions) {
			if (EMITTERS.contains(function)) {
				values.put(Attribute.of(function), 0.0);
			}
		}
		var hasRgb = functions.containsAll(EnumSet.of(ChannelFunction.RED, ChannelFunction.GREEN,
				ChannelFunction.BLUE));
		var emitter = palette.emitter().filter(functions::contains);
		if (emitter.isPresent()) {
			values.put(Attribute.of(emitter.get()), 1.0);
		} else if (hasRgb) {
			var color = palette.color();
			var white = functions.contains(ChannelFunction.WHITE)
					? Math.min(color.red(), Math.min(color.green(), color.blue()))
					: 0;
			values.put(Attribute.of(ChannelFunction.RED), color.red() - white);
			values.put(Attribute.of(ChannelFunction.GREEN), color.green() - white);
			values.put(Attribute.of(ChannelFunction.BLUE), color.blue() - white);
			if (white > 0) {
				values.put(Attribute.of(ChannelFunction.WHITE), white);
			}
		} else if (palette == Palette.WHITE && functions.contains(ChannelFunction.WHITE)) {
			values.put(Attribute.of(ChannelFunction.WHITE), 1.0);
		} else {
			mode.channels().stream()
					.filter(channel -> channel.function() == ChannelFunction.COLOR_WHEEL)
					.findFirst()
					.flatMap(wheel -> closestSlot(wheel, palette.color()))
					.ifPresent(value -> values.put(Attribute.of(ChannelFunction.COLOR_WHEEL), value));
		}
		return new FixtureValues(values);
	}

	/** Middle of the color wheel slot closest to the color, normalized like the wheel's DMX range (0–255). */
	private static Optional<Double> closestSlot(ChannelDefinition wheel, Color color) {
		ChannelRange best = null;
		var bestDistance = Double.MAX_VALUE;
		for (var range : wheel.ranges()) {
			if (range.color().isEmpty()) {
				continue;
			}
			var distance = distance(parse(range.color().get()), color);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = range;
			}
		}
		return Optional.ofNullable(best).map(range -> (range.from() + range.to()) / 2.0 / 255);
	}

	private static double distance(Color a, Color b) {
		var red = a.red() - b.red();
		var green = a.green() - b.green();
		var blue = a.blue() - b.blue();
		return red * red + green * green + blue * blue;
	}

	private static Color parse(String hex) {
		var rgb = Integer.parseInt(hex.substring(1), 16);
		return new Color(((rgb >> 16) & 0xff) / 255.0, ((rgb >> 8) & 0xff) / 255.0, (rgb & 0xff) / 255.0);
	}
}
