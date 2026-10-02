package de.vinzens.lumen.domain.library;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Ein logischer Kanal eines Modus. Bei 16 Bit belegt er zwei DMX-Kanäle (coarse + fine).
 *
 * @param name         Anzeigename, Pflicht bei {@link ChannelFunction#GENERIC}
 * @param resolution   8 oder 16 Bit
 * @param defaultValue Grundwert; bei 16 Bit im Bereich 0–65535
 * @param fadeBehavior überschriebenes Fade-Verhalten, sonst {@link ChannelFunction#defaultFade()}
 */
public record ChannelDefinition(
		ChannelFunction function,
		Optional<String> name,
		int resolution,
		int defaultValue,
		boolean invert,
		List<ChannelRange> ranges,
		Optional<FadeBehavior> fadeBehavior) {

	public static final int RESOLUTION_8_BIT = 8;
	public static final int RESOLUTION_16_BIT = 16;

	public ChannelDefinition {
		Objects.requireNonNull(function, "function");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(fadeBehavior, "fadeBehavior");
		ranges = List.copyOf(ranges);
	}

	/** Anzahl belegter DMX-Kanäle. */
	public int dmxChannelCount() {
		return resolution == RESOLUTION_16_BIT ? 2 : 1;
	}

	public int maxValue() {
		return resolution == RESOLUTION_16_BIT ? 65_535 : 255;
	}

	public FadeBehavior effectiveFadeBehavior() {
		return fadeBehavior.orElse(function.defaultFade());
	}

	/** Name für die Anzeige: expliziter Name oder die Funktions-ID. */
	public String displayName() {
		return name.orElse(function.id());
	}
}
