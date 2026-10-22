package de.vinzens.lumen.domain.party;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BeatClockTest {

	@Test
	void defaultsTo120Bpm() {
		var beat = new BeatClock();
		assertThat(beat.bpm()).isEqualTo(120);
		assertThat(beat.originMillis()).isEqualTo(-1);
	}

	@Test
	void setBpmClampsToRange() {
		var beat = new BeatClock();
		beat.setBpm(200);
		assertThat(beat.bpm()).isEqualTo(200);
		beat.setBpm(10);
		assertThat(beat.bpm()).isEqualTo(40);
		beat.setBpm(500);
		assertThat(beat.bpm()).isEqualTo(300);
	}

	@Test
	void twoTapsSetTheBpmFromTheirInterval() {
		var beat = new BeatClock();
		beat.tap(Instant.parse("2026-10-22T20:00:00.000Z"));
		beat.tap(Instant.parse("2026-10-22T20:00:00.500Z"));
		// 500 ms per beat = 120 BPM
		assertThat(beat.bpm()).isEqualTo(120);
		assertThat(beat.originMillis()).isEqualTo(Instant.parse("2026-10-22T20:00:00.500Z").toEpochMilli());
	}

	@Test
	void fourTapsAverageTheIntervals() {
		var beat = new BeatClock();
		beat.tap(Instant.parse("2026-10-22T20:00:00.000Z"));
		beat.tap(Instant.parse("2026-10-22T20:00:00.300Z"));
		beat.tap(Instant.parse("2026-10-22T20:00:00.600Z"));
		beat.tap(Instant.parse("2026-10-22T20:00:00.900Z"));
		// 300 ms per beat = 200 BPM
		assertThat(beat.bpm()).isEqualTo(200);
	}

	@Test
	void tapsOlderThanTwoSecondsAreForgotten() {
		var beat = new BeatClock();
		beat.tap(Instant.parse("2026-10-22T20:00:00.000Z"));
		beat.tap(Instant.parse("2026-10-22T20:00:00.500Z"));
		// 2.6 s later: all old taps are outside the 2 s window, only the new tap remains
		beat.tap(Instant.parse("2026-10-22T20:00:02.600Z"));
		// only one tap in the window: BPM stays from the previous taps
		assertThat(beat.bpm()).isEqualTo(120);
	}

	@Test
	void resetRestoresDefault() {
		var beat = new BeatClock();
		beat.setBpm(180);
		beat.tap(Instant.parse("2026-10-22T20:00:00.000Z"));
		beat.reset();
		assertThat(beat.bpm()).isEqualTo(120);
		assertThat(beat.originMillis()).isEqualTo(-1);
	}
}
