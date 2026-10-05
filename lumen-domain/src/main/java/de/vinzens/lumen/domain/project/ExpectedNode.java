package de.vinzens.lumen.domain.project;

import de.vinzens.lumen.domain.Validation;

/**
 * An Art-Net node the project needs: it has answered ArtPoll for one of the project's universes before. Remembered,
 * so a node that is missing after a restart – or goes missing during the show – is noticed.
 *
 * @param name short name the node reported, for the operator
 */
public record ExpectedNode(String ipAddress, String name) {

	public ExpectedNode {
		new Validation()
				.requireText(ipAddress, "ipAddress")
				.require(name != null, "name is missing")
				.throwIfInvalid();
	}
}
