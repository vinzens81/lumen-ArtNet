package de.vinzens.lumen.domain.party;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The tempo of party mode: BPM set by tapping or entering a value, and the origin of the beat cycle for the UI
 * to animate a beat indicator on. Not thread-safe – the caller synchronizes (as with {@link PartySwitchboard}).
 *
 * <p>The beat phase is computed on the client from {@link #bpm()} and {@link #originMillis()}: the phase is
 * {@code ((now - origin) / (60 / bpm)) % 1}, so the indicator advances smoothly without per-tick WebSocket messages.
 */
public final class BeatClock {

	public static final double DEFAULT_BPM = 120;
	private static final double MIN_BPM = 40;
	private static final double MAX_BPM = 300;
	/** Taps older than this are forgotten, so a pause and resume does not distort the average. */
	private static final long TAP_WINDOW_MS = 2000;
	private static final int MAX_TAPS = 8;

	private double bpm = DEFAULT_BPM;
	private Instant origin;
	private final List<Instant> taps = new ArrayList<>();

	/** The tempo in beats per minute. */
	public double bpm() {
		return bpm;
	}

	/** Sets the tempo directly, clearing any tap history. */
	public void setBpm(double value) {
		bpm = Math.clamp(value, MIN_BPM, MAX_BPM);
		taps.clear();
	}

	/**
	 * Taps the tempo. Two or more taps within the window set the BPM from their average interval; the beat origin
	 * is the last tap so the phase starts fresh.
	 */
	public void tap(Instant now) {
		taps.removeIf(t -> t.isBefore(now.minusMillis(TAP_WINDOW_MS)));
		taps.add(now);
		while (taps.size() > MAX_TAPS + 1) {
			taps.removeFirst();
		}
		if (taps.size() >= 2) {
			long total = 0;
			for (int i = 1; i < taps.size(); i++) {
				total += Duration.between(taps.get(i - 1), taps.get(i)).toMillis();
			}
			double avgMs = (double) total / (taps.size() - 1);
			if (avgMs > 0) {
				bpm = Math.clamp(60_000.0 / avgMs, MIN_BPM, MAX_BPM);
			}
		}
		origin = now;
	}

	/** Epoch milliseconds of the last beat (tap or {@link #setBpm(double)}), or -1 if no tempo has been set. */
	public long originMillis() {
		return origin == null ? -1 : origin.toEpochMilli();
	}

	/** Resets to the default tempo and clears the tap history. */
	public void reset() {
		bpm = DEFAULT_BPM;
		origin = null;
		taps.clear();
	}
}
