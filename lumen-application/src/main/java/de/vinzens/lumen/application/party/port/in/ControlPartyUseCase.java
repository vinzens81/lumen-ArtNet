package de.vinzens.lumen.application.party.port.in;

import de.vinzens.lumen.domain.party.PartyCell.Position;

import java.util.Set;

/**
 * Live control in party mode: the fields of the party matrix of the active project switch their scene or composition
 * on and off. Commands are only accepted in party mode.
 */
public interface ControlPartyUseCase {

	PartyStatus status();

	/** Toggle fields switch, flash fields go on until released; switching on ends the others of the group. */
	void press(int row, int column);

	void release(int row, int column);

	/** Everything off. */
	void clear();

	/** @param on the fields that are on */
	record PartyStatus(Set<Position> on) {

		public PartyStatus {
			on = Set.copyOf(on);
		}
	}
}
