package de.vinzens.lumen.adapter.sacn;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * Settings of the sACN output.
 *
 * @param bindAddress       local address to send from, {@code 0.0.0.0} for any interface
 * @param port              destination UDP port, 5568 by the standard
 * @param sourceName        shown by receivers and consoles, at most 63 bytes are sent
 * @param cid               identifies this source; must stay the same across restarts
 * @param discoveryInterval how often the universes sent are announced (E1.31: every 10 s)
 */
public record SacnSettings(String bindAddress, int port, String sourceName, UUID cid, Duration discoveryInterval) {

	public static final int DEFAULT_PORT = 5568;

	public SacnSettings {
		Objects.requireNonNull(bindAddress, "bindAddress");
		Objects.requireNonNull(sourceName, "sourceName");
		Objects.requireNonNull(cid, "cid");
		Objects.requireNonNull(discoveryInterval, "discoveryInterval");
		if (port < 1 || port > 65_535) {
			throw new IllegalArgumentException("port must be between 1 and 65535, was " + port);
		}
		if (!discoveryInterval.isPositive()) {
			throw new IllegalArgumentException("discoveryInterval must be positive");
		}
	}

	public SacnSettings(String bindAddress, int port, String sourceName, UUID cid) {
		this(bindAddress, port, sourceName, cid, Duration.ofSeconds(10));
	}
}
