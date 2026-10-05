package de.vinzens.lumen.application.playback.port.out;

import de.vinzens.lumen.domain.composition.Fader;

import java.util.List;

public interface FaderRepository {

	/** Faders of the project by slot. */
	List<Fader> findByProject(long projectId);

	/** Puts a scene on a fader slot, replacing what was there. */
	Fader save(long projectId, Fader fader);

	void delete(long projectId, int slot);
}
