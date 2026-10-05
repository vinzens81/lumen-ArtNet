package de.vinzens.lumen.application.output.port.in;

import de.vinzens.lumen.domain.project.ArtNetNode;
import de.vinzens.lumen.domain.project.LocalNetwork;

import java.util.List;
import java.util.Optional;

public interface QueryArtNetNodesUseCase {

	/** Known nodes, online ones first, then by IP address. */
	List<NodeStatus> nodes();

	/** Networks of this computer a universe can be bound to, by interface name. */
	List<LocalNetwork> networks();

	/** @param networkInterface the interface whose network the node is in, empty if none matches */
	record NodeStatus(ArtNetNode node, boolean online, Optional<String> networkInterface) {
	}
}
