package de.vinzens.lumen.domain.party;

import de.vinzens.lumen.domain.composition.Color;
import de.vinzens.lumen.domain.library.ChannelFunction;

import java.util.Arrays;
import java.util.Optional;

/**
 * The built-in color palettes of party mode. Each has an RGB color; Amber and UV prefer the fixture's own emitter
 * when it has one, the whites use the white channel.
 */
public enum Palette {
	RED("red", "Red", new Color(1, 0, 0), null),
	AMBER("amber", "Amber", new Color(1, 0.55, 0), ChannelFunction.AMBER),
	YELLOW("yellow", "Yellow", new Color(1, 0.9, 0), null),
	GREEN("green", "Green", new Color(0, 1, 0), null),
	CYAN("cyan", "Cyan", new Color(0, 1, 1), null),
	BLUE("blue", "Blue", new Color(0, 0, 1), null),
	MAGENTA("magenta", "Magenta", new Color(1, 0, 1), null),
	UV("uv", "UV", new Color(0.35, 0, 1), ChannelFunction.UV),
	WARM_WHITE("warm-white", "Warm white", new Color(1, 0.72, 0.4), ChannelFunction.WARM_WHITE),
	WHITE("white", "White", new Color(1, 1, 1), null);

	private final String id;
	private final String label;
	private final Color color;
	private final ChannelFunction emitter;

	Palette(String id, String label, Color color, ChannelFunction emitter) {
		this.id = id;
		this.label = label;
		this.color = color;
		this.emitter = emitter;
	}

	public String id() {
		return id;
	}

	public String label() {
		return label;
	}

	/** The color as RGB, also used for display. */
	public Color color() {
		return color;
	}

	/** A dedicated emitter that shows this color better than an RGB mix, if the fixture has it. */
	public Optional<ChannelFunction> emitter() {
		return Optional.ofNullable(emitter);
	}

	public static Optional<Palette> fromId(String id) {
		return Arrays.stream(values()).filter(p -> p.id.equals(id)).findFirst();
	}
}
