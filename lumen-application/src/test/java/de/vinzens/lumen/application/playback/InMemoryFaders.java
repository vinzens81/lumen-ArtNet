package de.vinzens.lumen.application.playback;

import de.vinzens.lumen.application.playback.port.out.FaderRepository;
import de.vinzens.lumen.domain.composition.Fader;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/** Faders of a single project, kept in memory. */
public class InMemoryFaders implements FaderRepository {

	private final TreeMap<Integer, Fader> faders = new TreeMap<>();

	@Override
	public List<Fader> findByProject(long projectId) {
		return new ArrayList<>(faders.values());
	}

	@Override
	public Fader save(long projectId, Fader fader) {
		faders.put(fader.slot(), fader);
		return fader;
	}

	@Override
	public void delete(long projectId, int slot) {
		faders.remove(slot);
	}
}
