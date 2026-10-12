package de.vinzens.lumen.application.party.port.in;

import de.vinzens.lumen.domain.party.PartyPlace;
import de.vinzens.lumen.domain.party.PartyPoint;

import java.util.Map;
import java.util.Set;

/**
 * Live control in party mode: the fields of the party matrix of the active project switch their scene or composition
 * on and off. Commands are only accepted in party mode.
 */
public interface ControlPartyUseCase {

	PartyStatus status();

	/** Toggle fields switch, flash fields go on until released; switching on ends the others of the group. */
	void press(PartyPlace place);

	void release(PartyPlace place);

	/** Touches a gradient or pad field: it goes on (ending the others of its group) with the values of the point. */
	void slide(PartyPlace place, PartyPoint point);

	/** Everything off. */
	void clear();

	/**
	 * @param on          the fields that are on
	 * @param faderPoints the point last touched on each gradient and pad field, also of those that are off
	 */
	record PartyStatus(Set<PartyPlace> on, Map<PartyPlace, PartyPoint> faderPoints) {

		public PartyStatus {
			on = Set.copyOf(on);
			faderPoints = Map.copyOf(faderPoints);
		}
	}
}
