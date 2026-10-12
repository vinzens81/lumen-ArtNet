package de.vinzens.lumen.application.party.port.in;

import de.vinzens.lumen.domain.party.PartyMatrix;

/** Sets up the party matrix of a project. */
public interface ManagePartyMatrixUseCase {

	PartyMatrix matrix(long projectId);

	/** Replaces the matrix; its scenes, compositions, moves and the fixtures of gradients must belong to the project. */
	PartyMatrix save(long projectId, PartyMatrix matrix);
}
