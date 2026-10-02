package de.vinzens.lumen.adapter.artnet;

import java.time.Duration;
import java.util.Objects;

/**
 * Network settings of the Art-Net output and discovery.
 *
 * @param bindAddress      local address to send from and listen on, {@code 0.0.0.0} for any interface
 * @param port             destination UDP port of nodes, 6454 by the Art-Net standard
 * @param broadcastAddress destination for broadcast universes and ArtPoll, e.g. {@code 2.255.255.255} or the
 *                         directed broadcast address of the lighting network
 * @param listenPort       local port for ArtPollReply – nodes always answer to 6454
 * @param pollInterval     how often ArtPoll is sent to discover nodes
 */
public record ArtNetSettings(String bindAddress, int port, String broadcastAddress, int listenPort,
		Duration pollInterval) {

	public static final int DEFAULT_PORT = 6454;

	public ArtNetSettings {
		Objects.requireNonNull(bindAddress, "bindAddress");
		Objects.requireNonNull(broadcastAddress, "broadcastAddress");
		Objects.requireNonNull(pollInterval, "pollInterval");
		requirePort(port, "port");
		requirePort(listenPort, "listenPort");
		if (!pollInterval.isPositive()) {
			throw new IllegalArgumentException("pollInterval must be positive");
		}
	}

	public ArtNetSettings(String bindAddress, int port, String broadcastAddress) {
		this(bindAddress, port, broadcastAddress, DEFAULT_PORT, Duration.ofSeconds(3));
	}

	private static void requirePort(int value, String name) {
		if (value < 1 || value > 65_535) {
			throw new IllegalArgumentException(name + " must be between 1 and 65535, was " + value);
		}
	}
}
