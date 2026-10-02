package de.vinzens.lumen.domain.library;

import java.util.Arrays;
import java.util.Optional;

/**
 * Meaning of a DMX channel. The {@link #id()} is the value used in the YAML files.
 */
public enum ChannelFunction {
	DIMMER("dimmer", FadeBehavior.INTENSITY),

	RED("red", FadeBehavior.SMOOTH),
	GREEN("green", FadeBehavior.SMOOTH),
	BLUE("blue", FadeBehavior.SMOOTH),
	WHITE("white", FadeBehavior.SMOOTH),
	WARM_WHITE("warm-white", FadeBehavior.SMOOTH),
	COLD_WHITE("cold-white", FadeBehavior.SMOOTH),
	AMBER("amber", FadeBehavior.SMOOTH),
	UV("uv", FadeBehavior.SMOOTH),
	LIME("lime", FadeBehavior.SMOOTH),
	CYAN("cyan", FadeBehavior.SMOOTH),
	MAGENTA("magenta", FadeBehavior.SMOOTH),
	YELLOW("yellow", FadeBehavior.SMOOTH),
	CTO("cto", FadeBehavior.SMOOTH),

	PAN("pan", FadeBehavior.SMOOTH),
	TILT("tilt", FadeBehavior.SMOOTH),

	FOCUS("focus", FadeBehavior.SMOOTH),
	ZOOM("zoom", FadeBehavior.SMOOTH),
	IRIS("iris", FadeBehavior.SMOOTH),
	GOBO_ROTATION("gobo-rotation", FadeBehavior.SMOOTH),

	COLOR_WHEEL("color-wheel", FadeBehavior.SNAP),
	GOBO("gobo", FadeBehavior.SNAP),
	PRISM("prism", FadeBehavior.SNAP),
	SHUTTER("shutter", FadeBehavior.SNAP),
	STROBE("strobe", FadeBehavior.SNAP),
	PROGRAM("program", FadeBehavior.SNAP),
	MACRO("macro", FadeBehavior.SNAP),
	RESET("reset", FadeBehavior.SNAP),
	SPEED("speed", FadeBehavior.SNAP),

	FOG("fog", FadeBehavior.SMOOTH),
	FAN("fan", FadeBehavior.SMOOTH),
	GENERIC("generic", FadeBehavior.SMOOTH);

	private final String id;
	private final FadeBehavior defaultFade;

	ChannelFunction(String id, FadeBehavior defaultFade) {
		this.id = id;
		this.defaultFade = defaultFade;
	}

	public String id() {
		return id;
	}

	public FadeBehavior defaultFade() {
		return defaultFade;
	}

	public static Optional<ChannelFunction> fromId(String id) {
		return Arrays.stream(values()).filter(f -> f.id.equals(id)).findFirst();
	}
}
