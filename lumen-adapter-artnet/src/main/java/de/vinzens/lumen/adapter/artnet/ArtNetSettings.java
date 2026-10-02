package de.vinzens.lumen.adapter.artnet;

import java.util.Objects;

/**
 * Network settings of the Art-Net output.
 *
 * @param bindAddress      local address to send from, {@code 0.0.0.0} for any interface
 * @param port             destination UDP port, 6454 by the Art-Net standard
 * @param broadcastAddress destination for universes with target {@code broadcast}, e.g. {@code 2.255.255.255}
 *                         or the directed broadcast address of the lighting network
 */
public record ArtNetSettings(String bindAddress, int port, String broadcastAddress) {

	public static final int DEFAULT_PORT = 6454;

	public ArtNetSettings {
		Objects.requireNonNull(bindAddress, "bindAddress");
		Objects.requireNonNull(broadcastAddress, "broadcastAddress");
		if (port < 1 || port > 65_535) {
			throw new IllegalArgumentException("port must be between 1 and 65535, was " + port);
		}
	}
}
