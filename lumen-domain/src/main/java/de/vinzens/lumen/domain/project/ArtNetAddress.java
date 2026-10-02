package de.vinzens.lumen.domain.project;

import de.vinzens.lumen.domain.Validation;

/**
 * Art-Net-Port-Adresse, bestehend aus Net (7 Bit), SubNet (4 Bit) und Universe (4 Bit).
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

	/** 15-Bit-Port-Adresse, wie sie im ArtDmx-Paket übertragen wird. */
	public int portAddress() {
		return (net << 8) | (subnet << 4) | universe;
	}

	@Override
	public String toString() {
		return net + ":" + subnet + ":" + universe;
	}
}
