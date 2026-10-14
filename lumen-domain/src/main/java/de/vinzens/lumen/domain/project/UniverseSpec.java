package de.vinzens.lumen.domain.project;

import de.vinzens.lumen.domain.Validation;

import java.util.Objects;
import java.util.Optional;

/**
 * Editable properties of a universe.
 *
 * @param number           logical number within the project (1, 2, …), shown in the patch
 * @param networkInterface the network interface its packets go out on ({@link LocalNetwork#interfaceName()}), e.g.
 *                         the adapter of the lighting network; empty = all networks
 * @param protocol         Art-Net, sACN or both; {@code artNet}/{@code target} and {@code sacn} are kept either way
 * @param sacn             how it goes out by sACN
 */
public record UniverseSpec(int number, Optional<String> name, ArtNetAddress artNet, ArtNetTarget target,
		Optional<String> networkInterface, OutputProtocol protocol, SacnOutput sacn) {

	public UniverseSpec {
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(networkInterface, "networkInterface");
		new Validation()
				.require(number >= 1, "number must be at least 1, was " + number)
				.require(artNet != null, "artnet is missing")
				.require(target != null, "target is missing")
				.require(protocol != null, "protocol is missing")
				.require(sacn != null, "sacn is missing")
				.require(networkInterface.map(n -> n.length() <= 64).orElse(true),
						"networkInterface must have at most 64 characters")
				.throwIfInvalid();
		name = name.map(String::strip).filter(n -> !n.isEmpty());
		networkInterface = networkInterface.map(String::strip).filter(n -> !n.isEmpty());
	}

	/** An Art-Net universe; its sACN settings are the defaults for its number. */
	public UniverseSpec(int number, Optional<String> name, ArtNetAddress artNet, ArtNetTarget target,
			Optional<String> networkInterface) {
		this(number, name, artNet, target, networkInterface, OutputProtocol.ART_NET, SacnOutput.forUniverse(number));
	}

	/** An Art-Net universe sent on all networks. */
	public UniverseSpec(int number, Optional<String> name, ArtNetAddress artNet, ArtNetTarget target) {
		this(number, name, artNet, target, Optional.empty());
	}
}
