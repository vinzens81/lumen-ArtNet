package de.vinzens.lumen.application.party.port.out;

import de.vinzens.lumen.domain.composition.KeyMode;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.party.PartyButton;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PartyButtonRepository {

	/** Buttons ordered by position. */
	List<PartyButton> findByProject(long projectId);

	Optional<PartyButton> findById(long id);

	/** Adds the button after the last one. */
	PartyButton create(long projectId, String name, KeyMode mode, Map<Long, FixtureValues> values);

	PartyButton update(long id, String name, KeyMode mode);

	PartyButton replaceValues(long id, Map<Long, FixtureValues> values);

	/** Sets the positions in the order of the ids. */
	void reorder(List<Long> ids);

	void delete(long id);
}
