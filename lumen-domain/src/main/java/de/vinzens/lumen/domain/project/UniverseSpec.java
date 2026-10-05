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
 */
public record UniverseSpec(int number, Optional<String> name, ArtNetAddress artNet, ArtNetTarget target,
		Optional<String> networkInterface) {

	public UniverseSpec {
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(networkInterface, "networkInterface");
		new Validation()
				.require(number >= 1, "number must be at least 1, was " + number)
				.require(artNet != null, "artnet is missing")
				.require(target != null, "target is missing")
				.require(networkInterface.map(n -> n.length() <= 64).orElse(true),
						"networkInterface must have at most 64 characters")
				.throwIfInvalid();
		name = name.map(String::strip).filter(n -> !n.isEmpty());
		networkInterface = networkInterface.map(String::strip).filter(n -> !n.isEmpty());
	}

	/** A universe sent on all networks. */
	public UniverseSpec(int number, Optional<String> name, ArtNetAddress artNet, ArtNetTarget target) {
		this(number, name, artNet, target, Optional.empty());
	}
}
