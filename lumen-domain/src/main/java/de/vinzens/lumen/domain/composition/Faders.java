package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.output.FixtureValues;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The faders while the show runs: each lays its composition over the playback at its level – intensity highest takes
 * precedence, other attributes faded in by the level. Levels are set by hand and stay when the setup is reloaded, as
 * long as a fader keeps its composition. Not thread-safe – callers synchronize.
 */
public final class Faders {

	/** A fader ready to play: its composition runs from the moment the fader goes up. */
	public record PlayableFader(int slot, String name, ActiveComposition composition) {

		public PlayableFader {
			Objects.requireNonNull(name, "name");
			Objects.requireNonNull(composition, "composition");
		}
	}

	/** @param level 0–1 */
	public record FaderStatus(int slot, String name, double level) {
	}

	private Map<Integer, PlayableFader> faders = Map.of();
	private final Map<Integer, Double> levels = new HashMap<>();
	/** When a fader went up from 0: its compositions run from there. */
	private final Map<Integer, Instant> startedAt = new HashMap<>();

	/** Replaces the setup; a fader that keeps its composition keeps its level. */
	public void configure(List<PlayableFader> newFaders) {
		var bySlot = new HashMap<Integer, PlayableFader>();
		newFaders.forEach(fader -> bySlot.put(fader.slot(), fader));
		for (var slot : List.copyOf(levels.keySet())) {
			var fader = bySlot.get(slot);
			var old = faders.get(slot);
			if (fader == null || old == null || fader.composition().id() != old.composition().id()) {
				levels.remove(slot);
				startedAt.remove(slot);
			}
		}
		faders = Map.copyOf(bySlot);
	}

	/** @return whether the level changed */
	public boolean set(int slot, double level, Instant now) {
		if (!faders.containsKey(slot)) {
			throw new DomainValidationException(List.of("No fader on slot " + slot));
		}
		if (Double.isNaN(level) || level < 0 || level > 1) {
			throw new DomainValidationException(List.of("level must be between 0 and 1, was " + level));
		}
		var old = levels.getOrDefault(slot, 0.0);
		if (old == level) {
			return false;
		}
		if (level == 0) {
			levels.remove(slot);
			startedAt.remove(slot);
		} else {
			levels.put(slot, level);
			startedAt.putIfAbsent(slot, now);
		}
		return true;
	}

	/** Every fader to 0 at once, e.g. when the show is stopped. @return whether one was up */
	public boolean allDown() {
		var wasUp = !levels.isEmpty();
		levels.clear();
		startedAt.clear();
		return wasUp;
	}

	/** Lays the faders that are up over the look, in slot order. */
	public Map<Long, FixtureValues> apply(Map<Long, FixtureValues> look, Instant now, FixtureAttributes attributes) {
		var result = look;
		for (var slot : levels.keySet().stream().sorted().toList()) {
			var fader = faders.get(slot);
			if (fader == null) {
				continue;
			}
			var seconds = Math.max(0, Duration.between(startedAt.get(slot), now).toNanos() / 1e9);
			var values = CompositionMixer.apply(Map.of(), fader.composition(), seconds, attributes);
			result = CompositionMixer.overlay(result, values, levels.get(slot));
		}
		return result;
	}

	public List<FaderStatus> status() {
		var result = new ArrayList<FaderStatus>();
		faders.values().stream().sorted((a, b) -> Integer.compare(a.slot(), b.slot())).forEach(fader ->
				result.add(new FaderStatus(fader.slot(), fader.name(), levels.getOrDefault(fader.slot(), 0.0))));
		return result;
	}
}
