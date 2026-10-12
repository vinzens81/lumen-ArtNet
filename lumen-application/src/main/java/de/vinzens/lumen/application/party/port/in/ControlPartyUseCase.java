package de.vinzens.lumen.application.party.port.in;

import de.vinzens.lumen.domain.party.PartyCell.Position;

import java.util.Map;
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

	/**
	 * Touches a gradient field: it goes on (ending the others of its group) with the values of the point.
	 *
	 * @param point 0 = left end, 1 = right end
	 */
	void slide(int row, int column, double point);

	/** Everything off. */
	void clear();

	/**
	 * @param on          the fields that are on
	 * @param faderPoints the point last touched on each gradient field (0–1), also of those that are off
	 */
	record PartyStatus(Set<Position> on, Map<Position, Double> faderPoints) {

		public PartyStatus {
			on = Set.copyOf(on);
			faderPoints = Map.copyOf(faderPoints);
		}
	}
}
