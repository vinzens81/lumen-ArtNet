package de.vinzens.lumen.application.output.port.out;

import de.vinzens.lumen.domain.project.ExpectedNode;

import java.util.List;

/**
 * The Art-Net nodes each project expects on the network, identified by IP address.
 */
public interface ExpectedNodeRepository {

	/** Sorted by IP address. */
	List<ExpectedNode> findByProject(long projectId);

	/** Adds the node or updates its name. */
	void save(long projectId, ExpectedNode node);

	void delete(long projectId, String ipAddress);
}
