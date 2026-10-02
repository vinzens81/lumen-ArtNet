package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.Validation;

/**
 * An RGB color with components 0–1.
 */
public record Color(double red, double green, double blue) {

	public Color {
		new Validation()
				.require(inRange(red) && inRange(green) && inRange(blue),
						"color components must be between 0 and 1, was " + red + "/" + green + "/" + blue)
				.throwIfInvalid();
	}

	/** Linear blend: {@code amount} 0 = this color, 1 = the other. */
	public Color blend(Color other, double amount) {
		return new Color(red + (other.red - red) * amount, green + (other.green - green) * amount,
				blue + (other.blue - blue) * amount);
	}

	/** h, s, v in 0–1. */
	public static Color hsv(double hue, double saturation, double value) {
		var h = (hue - Math.floor(hue)) * 6;
		var i = (int) Math.floor(h) % 6;
		var f = h - Math.floor(h);
		var p = value * (1 - saturation);
		var q = value * (1 - f * saturation);
		var t = value * (1 - (1 - f) * saturation);
		return switch (i) {
			case 0 -> new Color(value, t, p);
			case 1 -> new Color(q, value, p);
			case 2 -> new Color(p, value, t);
			case 3 -> new Color(p, q, value);
			case 4 -> new Color(t, p, value);
			default -> new Color(value, p, q);
		};
	}

	private static boolean inRange(double value) {
		return value >= 0 && value <= 1;
	}
}
