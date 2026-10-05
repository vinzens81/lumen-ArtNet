package de.vinzens.lumen.domain.party;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Tempo from taps: the average interval of the last taps gives the BPM, and the last tap marks a beat, so the beat
 * runs in time with the music. A pause of more than two seconds starts a new measurement. Not thread-safe.
 */
public final class TapTempo {

	public static final double MIN_BPM = 40;
	public static final double MAX_BPM = 240;
	public static final double DEFAULT_BPM = 120;
	private static final Duration RESTART_AFTER = Duration.ofSeconds(2);
	private static final int MAX_TAPS = 8;

	private final Deque<Instant> taps = new ArrayDeque<>();
	private double bpm = DEFAULT_BPM;
	private Instant beatAt;

	public TapTempo(Instant start) {
		this.beatAt = Objects.requireNonNull(start, "start");
	}

	/** Registers a tap; from the second tap on it sets the tempo. */
	public void tap(Instant now) {
		if (!taps.isEmpty() && Duration.between(taps.getLast(), now).compareTo(RESTART_AFTER) > 0) {
			taps.clear();
		}
		taps.addLast(now);
		if (taps.size() > MAX_TAPS) {
			taps.removeFirst();
		}
		if (taps.size() >= 2) {
			var span = Duration.between(taps.getFirst(), taps.getLast()).toNanos() / 1e9;
			bpm = clamp(60 * (taps.size() - 1) / span);
		}
		beatAt = now;
	}

	/** Sets the tempo directly, keeping the beat. */
	public void setBpm(double value) {
		bpm = clamp(value);
	}

	public double bpm() {
		return bpm;
	}

	public Duration beatLength() {
		return Duration.ofNanos(Math.round(60e9 / bpm));
	}

	/** Beats since the last tap, e.g. 2.5 = halfway through the third beat; the fraction is the beat phase. */
	public double beats(Instant now) {
		return Duration.between(beatAt, now).toNanos() / 1e9 * bpm / 60;
	}

	/** The last beat at or before {@code now}. */
	public Instant lastBeat(Instant now) {
		var whole = (long) Math.floor(beats(now));
		return beatAt.plusNanos(Math.round(whole * 60e9 / bpm));
	}

	private static double clamp(double value) {
		return Math.max(MIN_BPM, Math.min(MAX_BPM, value));
	}
}
