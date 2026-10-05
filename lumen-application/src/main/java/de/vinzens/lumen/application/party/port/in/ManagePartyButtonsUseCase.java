package de.vinzens.lumen.application.party.port.in;

import de.vinzens.lumen.domain.composition.KeyMode;
import de.vinzens.lumen.domain.party.PartyButton;

import java.util.List;

/**
 * The party buttons of a project. They are recorded from the programmer, so recording works only for the active
 * project in setup mode.
 */
public interface ManagePartyButtonsUseCase {

	/** Buttons in their order. */
	List<PartyButton> buttons(long projectId);

	/** Creates a button with the current programmer values, at the end of the list. */
	PartyButton record(long projectId, String name, KeyMode mode);

	PartyButton update(long projectId, long buttonId, String name, KeyMode mode);

	/** Overwrites the values of the button with the current programmer. */
	PartyButton rerecord(long projectId, long buttonId);

	/** Loads the values of the button into the programmer, to check or change them. */
	void loadIntoProgrammer(long projectId, long buttonId);

	/** New order of all buttons of the project. */
	List<PartyButton> reorder(long projectId, List<Long> buttonIds);

	void delete(long projectId, long buttonId);
}
