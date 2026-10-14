package de.vinzens.lumen.domain.project;

import de.vinzens.lumen.domain.Validation;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * How a universe goes out by sACN (ANSI E1.31).
 *
 * @param universe    sACN universe number, 1–63999 (sACN counts from 1)
 * @param priority    0–200, receivers take the source with the highest priority; 100 by default
 * @param ipAddresses manual IPv4 addresses; none = multicast to {@link #multicastAddress()}, the usual way
 */
public record SacnOutput(int universe, int priority, List<String> ipAddresses) {

	public static final int MIN_UNIVERSE = 1;
	public static final int MAX_UNIVERSE = 63_999;
	public static final int DEFAULT_PRIORITY = 100;
	public static final int MAX_PRIORITY = 200;

	public SacnOutput {
		Objects.requireNonNull(ipAddresses, "ipAddresses");
		ipAddresses = List.copyOf(new LinkedHashSet<>(ipAddresses.stream().map(String::strip)
				.filter(ip -> !ip.isEmpty()).toList()));
		var validation = new Validation()
				.requireRange(universe, MIN_UNIVERSE, MAX_UNIVERSE, "sACN universe")
				.requireRange(priority, 0, MAX_PRIORITY, "sACN priority");
		ipAddresses.forEach(ip -> validation.require(Ipv4.isValid(ip), "'" + ip + "' is not a valid IPv4 address"));
		validation.throwIfInvalid();
	}

	/** sACN universe = Lumen's universe number (capped), multicast, default priority. */
	public static SacnOutput forUniverse(int number) {
		return new SacnOutput(Math.clamp(number, MIN_UNIVERSE, MAX_UNIVERSE), DEFAULT_PRIORITY, List.of());
	}

	public boolean multicast() {
		return ipAddresses.isEmpty();
	}

	/** The multicast group of the universe, {@code 239.255.<high byte>.<low byte>}. */
	public String multicastAddress() {
		return "239.255." + (universe >> 8) + "." + (universe & 0xFF);
	}
}
