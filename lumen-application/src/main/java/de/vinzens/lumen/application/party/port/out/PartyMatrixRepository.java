package de.vinzens.lumen.application.party.port.out;

import de.vinzens.lumen.domain.party.PartyMatrix;

public interface PartyMatrixRepository {

	/** The matrix of the project; {@link PartyMatrix#EMPTY} if it has none yet. */
	PartyMatrix find(long projectId);

	/** Replaces the whole matrix. */
	void save(long projectId, PartyMatrix matrix);
}
