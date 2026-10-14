package de.vinzens.lumen.domain.project;

import de.vinzens.lumen.domain.DomainValidationException;

import java.util.Arrays;
import java.util.List;

/** How a universe goes out: Art-Net, sACN (E1.31) or both at once, e.g. while moving a rig from one to the other. */
public enum OutputProtocol {

	ART_NET("artnet"),
	SACN("sacn"),
	BOTH("both");

	private final String text;

	OutputProtocol(String text) {
		this.text = text;
	}

	/** Text form for storage and the API: {@code artnet}, {@code sacn} or {@code both}. */
	public String asText() {
		return text;
	}

	public boolean sendsArtNet() {
		return this != SACN;
	}

	public boolean sendsSacn() {
		return this != ART_NET;
	}

	public static OutputProtocol parse(String text) {
		return Arrays.stream(values()).filter(p -> p.text.equalsIgnoreCase(text == null ? "" : text.strip()))
				.findFirst()
				.orElseThrow(() -> new DomainValidationException(List.of(
						"protocol must be artnet, sacn or both, was '" + text + "'")));
	}
}
