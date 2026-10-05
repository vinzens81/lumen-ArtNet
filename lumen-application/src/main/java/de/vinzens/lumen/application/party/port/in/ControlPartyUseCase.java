package de.vinzens.lumen.application.party.port.in;

import de.vinzens.lumen.domain.composition.KeyMode;
import de.vinzens.lumen.domain.party.Palette;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Live control in party mode: tempo, palettes and intensities for fixtures, and the party buttons of the active
 * project. Commands are only accepted in party mode.
 */
public interface ControlPartyUseCase {

	PartyStatus status();

	/** Tap tempo: the taps set the BPM, the last tap marks a beat. */
	void tap();

	void setBpm(double bpm);

	/** Gives the fixtures a palette; dark fixtures come on at full. */
	void setPalette(Collection<Long> fixtureIds, Palette palette);

	void setIntensity(Collection<Long> fixtureIds, double level);

	/** Toggle buttons switch, flash buttons go on until released. */
	void pressButton(long buttonId);

	void releaseButton(long buttonId);

	/** Palettes, intensities and buttons off. */
	void clear();

	/**
	 * @param bpm      the tempo
	 * @param lastBeat the last beat; with {@code beatLength} clients run the beat indicator themselves
	 * @param fixtures palette and intensity of the fixtures party mode has touched
	 * @param buttons  the party buttons of the active project, in their order
	 */
	record PartyStatus(double bpm, Instant lastBeat, Duration beatLength, Map<Long, FixtureState> fixtures,
			List<ButtonStatus> buttons) {
	}

	record FixtureState(Optional<Palette> palette, double intensity) {
	}

	record ButtonStatus(long id, String name, KeyMode mode, boolean on) {
	}
}
