package de.vinzens.lumen.application.playback.port.in;

import de.vinzens.lumen.domain.composition.Fader;

import java.util.List;

/**
 * The fader setup of a project: which scene each fader of the show plays, e.g. the house lights.
 */
public interface ManageFadersUseCase {

	List<Fader> faders(long projectId);

	/** Puts a scene of the project on a fader slot, replacing what was there. */
	Fader save(long projectId, Fader fader);

	void delete(long projectId, int slot);
}
