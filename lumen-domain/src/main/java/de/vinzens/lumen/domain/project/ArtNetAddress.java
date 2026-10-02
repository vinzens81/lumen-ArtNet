package de.vinzens.lumen.domain.project;

import de.vinzens.lumen.domain.Validation;

/**
 * Art-Net port address, consisting of net (7 bit), subnet (4 bit) and universe (4 bit).
 */
public record ArtNetAddress(int net, int subnet, int universe) {

	public static final int MAX_PORT_ADDRESS = 32_767;

	public ArtNetAddress {
		new Validation()
				.requireRange(net, 0, 127, "Art-Net net")
				.requireRange(subnet, 0, 15, "Art-Net subnet")
				.requireRange(universe, 0, 15, "Art-Net universe")
				.throwIfInvalid();
	}

	public static ArtNetAddress fromPortAddress(int portAddress) {
		new Validation().requireRange(portAddress, 0, MAX_PORT_ADDRESS, "Art-Net port-address").throwIfInvalid();
		return new ArtNetAddress(portAddress >> 8, (portAddress >> 4) & 0xF, portAddress & 0xF);
	}

	/** 15-bit port address as transmitted in the ArtDmx packet. */
	public int portAddress() {
		return (net << 8) | (subnet << 4) | universe;
	}

	@Override
	public String toString() {
		return net + ":" + subnet + ":" + universe;
	}
}
