package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.output.FixtureValues;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The extra keys while the show runs: the keys of the scene on stage, which are on, their fades, and their
 * compositions laid over the playback with the same rules as in a scene. Keys run on the real clock: pausing the
 * playback does not stop them.
 *
 * <p>When the scene changes, a key that is on stays on if its composition is on a key of the new scene too (also
 * under another number); otherwise it fades out with its fade time. Not thread-safe – callers synchronize.
 */
public final class ExtraKeys {

	/** A key of the current scene, ready to play. */
	public record PlayableKey(int slot, KeyMode mode, Duration fade, String name, ActiveComposition composition) {

		public PlayableKey {
			Objects.requireNonNull(mode, "mode");
			Objects.requireNonNull(fade, "fade");
			Objects.requireNonNull(name, "name");
			Objects.requireNonNull(composition, "composition");
		}

		long compositionId() {
			return composition.id();
		}
	}

	/**
	 * @param on    target state (fading in or on)
	 * @param level current fade level 0–1
	 */
	public record KeyStatus(int slot, String name, KeyMode mode, boolean on, double level) {
	}

	/** Keys of the current scene by slot. */
	private Map<Integer, PlayableKey> keys = Map.of();
	/** Keys that are on or fading, by composition id; each remembers the key it plays (also after it left). */
	private final Map<Long, State> states = new HashMap<>();

	/**
	 * Takes the keys of the scene now on stage. Keys whose composition is on a key of this scene keep running (with
	 * the new key's settings); all others fade out.
	 */
	public void configure(List<PlayableKey> newKeys, Instant now) {
		var bySlot = new HashMap<Integer, PlayableKey>();
		newKeys.forEach(key -> bySlot.put(key.slot(), key));
		keys = Map.copyOf(bySlot);
		var byComposition = new HashMap<Long, PlayableKey>();
		newKeys.forEach(key -> byComposition.put(key.compositionId(), key));
		for (var entry : List.copyOf(states.entrySet())) {
			var key = byComposition.get(entry.getKey());
			var state = entry.getValue();
			if (key != null) {
				// the level at this moment stays, only the fade time of the new key applies from now on
				states.put(entry.getKey(), state.withKey(key, now));
			} else if (state.on()) {
				states.put(entry.getKey(), state.switchedOff(now));
			}
		}
	}

	/** Toggle: switches on or off. Flash: switches on until {@link #release}. Slots without a key do nothing. */
	public boolean press(int slot, Instant now) {
		var key = keys.get(slot);
		if (key == null) {
			return false;
		}
		var state = states.get(key.compositionId());
		if (key.mode() == KeyMode.TOGGLE && state != null && state.on()) {
			states.put(key.compositionId(), state.switchedOff(now));
		} else {
			// a key switched on again while fading out keeps its phase
			var from = state != null ? state.level(now) : 0;
			var startedAt = state != null ? state.startedAt() : now;
			states.put(key.compositionId(), new State(key, true, from, now, startedAt, false));
		}
		return true;
	}

	/** Releasing a flash key switches it off; toggles ignore it. */
	public boolean release(int slot, Instant now) {
		var key = keys.get(slot);
		if (key == null || key.mode() != KeyMode.FLASH) {
			return false;
		}
		var state = states.get(key.compositionId());
		if (state == null || !state.on()) {
			return false;
		}
		states.put(key.compositionId(), state.switchedOff(now));
		return true;
	}

	/** Fades every key out, e.g. at the end of the show. */
	public void allOff(Instant now) {
		for (var entry : List.copyOf(states.entrySet())) {
			if (entry.getValue().on()) {
				states.put(entry.getKey(), entry.getValue().switchedOff(now));
			}
		}
	}

	/**
	 * Forgets keys that have faded out completely.
	 *
	 * @return whether a fade ended (for notifying clients)
	 */
	public boolean tick(Instant now) {
		var changed = false;
		for (var entry : List.copyOf(states.entrySet())) {
			var state = entry.getValue();
			if (!state.on() && state.level(now) <= 0) {
				states.remove(entry.getKey());
				changed = true;
			} else if (!state.fading(now) && !state.reported()) {
				states.put(entry.getKey(), state.markReported());
				changed = true;
			}
		}
		return changed;
	}

	/** Lays the active keys over the look (in slot order: a higher key wins on the same attribute). */
	public Map<Long, FixtureValues> apply(Map<Long, FixtureValues> look, Instant now, FixtureAttributes attributes) {
		var result = look;
		var active = states.values().stream().sorted(Comparator.comparingInt(s -> s.key().slot())).toList();
		for (var state : active) {
			var level = state.level(now);
			if (level <= 0) {
				continue;
			}
			var composition = state.key().composition();
			var seconds = Math.max(0, Duration.between(state.startedAt(), now).toNanos() / 1e9);
			result = CompositionMixer.apply(result, CompositionMixer.contribution(composition, seconds, attributes),
					composition.pattern(), composition.level() * level, attributes);
		}
		return result;
	}

	/** The keys of the current scene, in slot order. */
	public List<KeyStatus> status(Instant now) {
		var result = new ArrayList<KeyStatus>();
		for (var key : keys.values().stream().sorted(Comparator.comparingInt(PlayableKey::slot)).toList()) {
			var state = states.get(key.compositionId());
			result.add(new KeyStatus(key.slot(), key.name(), key.mode(), state != null && state.on(),
					state == null ? 0 : state.level(now)));
		}
		return result;
	}

	/** Current fade level of the key on a slot of the current scene, 0 if it is off. */
	public double level(int slot, Instant now) {
		var key = keys.get(slot);
		var state = key == null ? null : states.get(key.compositionId());
		return state == null ? 0 : state.level(now);
	}

	/** Slots of the current scene whose key is on. */
	public Set<Integer> activeSlots() {
		var result = new HashSet<Integer>();
		keys.values().forEach(key -> {
			var state = states.get(key.compositionId());
			if (state != null && state.on()) {
				result.add(key.slot());
			}
		});
		return result;
	}

	/**
	 * @param key       the key it plays (the last one, if the scene with the key has left the stage)
	 * @param on        fading towards 1 (true) or 0 (false)
	 * @param fromLevel level when the direction last changed
	 * @param changedAt when the direction last changed
	 * @param startedAt when the composition started (phase)
	 * @param reported  the end of the fade was already reported
	 */
	private record State(PlayableKey key, boolean on, double fromLevel, Instant changedAt, Instant startedAt,
			boolean reported) {

		/** A full fade takes the key's fade time; starting part-way takes proportionally less. */
		double level(Instant now) {
			var target = on ? 1.0 : 0.0;
			var fade = key.fade();
			if (fade.isZero()) {
				return target;
			}
			var progress = Duration.between(changedAt, now).toNanos() / (double) fade.toNanos();
			var level = on ? fromLevel + progress : fromLevel - progress;
			return Math.min(1, Math.max(0, level));
		}

		boolean fading(Instant now) {
			var level = level(now);
			return on ? level < 1 : level > 0;
		}

		State switchedOff(Instant now) {
			return new State(key, false, level(now), now, startedAt, false);
		}

		State withKey(PlayableKey newKey, Instant now) {
			return new State(newKey, on, level(now), now, startedAt, reported);
		}

		State markReported() {
			return new State(key, on, fromLevel, changedAt, startedAt, true);
		}
	}
}
