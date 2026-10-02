package de.vinzens.lumen.application.output.port.in;

import de.vinzens.lumen.domain.project.ArtNetNode;

import java.util.List;

public interface QueryArtNetNodesUseCase {

	/** Known nodes, online ones first, then by IP address. */
	List<NodeStatus> nodes();

	record NodeStatus(ArtNetNode node, boolean online) {
	}
}
