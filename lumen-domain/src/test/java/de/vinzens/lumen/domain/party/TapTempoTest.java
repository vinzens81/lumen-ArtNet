package de.vinzens.lumen.domain.party;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TapTempoTest {

	private Instant now = Instant.parse("2026-10-05T22:00:00Z");
	private final TapTempo tempo = new TapTempo(now);

	@Test
	void averagesTheTapsAndStartsTheBeatAtTheLastTap() {
		tap(0);
		tap(500);
		tap(500);
		tap(500);

		assertThat(tempo.bpm()).isCloseTo(120, within(1e-9));
		assertThat(tempo.beats(now)).isZero();
		now = now.plusMillis(750);
		assertThat(tempo.beats(now)).isCloseTo(1.5, within(1e-9));
		assertThat(tempo.lastBeat(now)).isEqualTo(now.minusMillis(250));
	}

	@Test
	void aLongPauseStartsANewMeasurement() {
		tap(0);
		tap(500);
		tap(3000);
		tap(400);

		assertThat(tempo.bpm()).isCloseTo(150, within(1e-9));
	}

	@Test
	void tempoStaysInARange() {
		tap(0);
		tap(50);
		assertThat(tempo.bpm()).isEqualTo(TapTempo.MAX_BPM);

		tempo.setBpm(10);
		assertThat(tempo.bpm()).isEqualTo(TapTempo.MIN_BPM);
		assertThat(tempo.beatLength()).isEqualTo(Duration.ofMillis(1500));
	}

	private void tap(long afterMs) {
		now = now.plusMillis(afterMs);
		tempo.tap(now);
	}
}
