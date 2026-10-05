package de.vinzens.lumen.domain.library;

import de.vinzens.lumen.domain.DomainValidationException;

import java.util.Arrays;
import java.util.List;

/**
 * What an attribute is about, e.g. to update only the colors of a scene or to keep palettes apart.
 */
public enum AttributeGroup {
	INTENSITY("intensity"),
	COLOR("color"),
	POSITION("position"),
	BEAM("beam"),
	OTHER("other");

	private final String id;

	AttributeGroup(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public static AttributeGroup parse(String id) {
		return Arrays.stream(values())
				.filter(group -> group.id.equals(id))
				.findFirst()
				.orElseThrow(() -> new DomainValidationException(List.of("attribute group '" + id + "' is unknown, use "
						+ Arrays.stream(values()).map(AttributeGroup::id).toList())));
	}

	public static AttributeGroup of(ChannelFunction function) {
		return switch (function) {
			case DIMMER -> INTENSITY;
			case RED, GREEN, BLUE, WHITE, WARM_WHITE, COLD_WHITE, AMBER, UV, LIME, CYAN, MAGENTA, YELLOW, CTO,
				 COLOR_WHEEL -> COLOR;
			case PAN, TILT -> POSITION;
			case FOCUS, ZOOM, IRIS, GOBO, GOBO_ROTATION, PRISM, SHUTTER, STROBE -> BEAM;
			case PROGRAM, MACRO, RESET, SPEED, FOG, FAN, GENERIC -> OTHER;
		};
	}

	public boolean contains(Attribute attribute) {
		return of(attribute.function()) == this;
	}
}
