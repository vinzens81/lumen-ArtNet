package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.output.FixtureValues;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The extra keys while the show runs: which are on, their fades, and what they play laid over the playback – a
 * composition with the same rules as in a scene, or a whole scene as one look. Keys run on the real clock: pausing the playback does not stop them.
 * A key can only be switched on while it is available in the current scene; when it stops being available it fades
 * out. Not thread-safe – callers synchronize.
 */
public final class ExtraKeys {

	/** A key ready to play. */
	public record PlayableKey(int slot, KeyMode mode, Duration fade, String name, Look look) {

		public PlayableKey {
			Objects.requireNonNull(mode, "mode");
			Objects.requireNonNull(fade, "fade");
			Objects.requireNonNull(look, "look");
		}

		public PlayableKey(int slot, KeyMode mode, Duration fade, String name, ActiveComposition composition) {
			this(slot, mode, fade, name, new CompositionLook(composition));
		}
	}

	/** What a key lays over the playback. */
	public sealed interface Look {

		/** Identifies the content: a key keeps running when it is reconfigured with the same content. */
		String identity();
	}

	/** A composition, laid over the playback with its own rules (a flicker flickers around the scene). */
	public record CompositionLook(ActiveComposition composition) implements Look {

		public String identity() {
			return "composition:" + composition.id();
		}
	}

	/** A whole scene: its values with its compositions, laid over the playback as one look. */
	public record SceneLook(long sceneId, Map<Long, FixtureValues> values, List<ActiveComposition> compositions)
			implements Look {

		public SceneLook {
			values = Map.copyOf(values);
			compositions = List.copyOf(compositions);
		}

		public String identity() {
			return "scene:" + sceneId;
		}
	}

	/**
	 * @param kind      kind of the composition, empty if the key plays a scene
	 * @param on        target state (fading in or on)
	 * @param level     current fade level 0–1
	 * @param available can be switched on in the current scene
	 */
	public record KeyStatus(int slot, String name, Optional<PatternKind> kind, KeyMode mode, boolean on,
			double level, boolean available) {
	}

	private Map<Integer, PlayableKey> keys = Map.of();
	private Set<Integer> available = Set.of();
	private final Map<Integer, State> states = new HashMap<>();

	/** Replaces the key setup; keys that keep their composition keep running. */
	public void configure(List<PlayableKey> newKeys, Instant now) {
		var bySlot = new HashMap<Integer, PlayableKey>();
		newKeys.forEach(key -> bySlot.put(key.slot(), key));
		for (var slot : List.copyOf(states.keySet())) {
			var key = bySlot.get(slot);
			var old = keys.get(slot);
			if (key == null || old == null || !key.look().identity().equals(old.look().identity())) {
				states.remove(slot);
			}
		}
		keys = Map.copyOf(bySlot);
		setAvailable(available, now);
	}

	/** Keys that can be used in the current scene; others fade out. */
	public void setAvailable(Set<Integer> slots, Instant now) {
		available = Set.copyOf(slots);
		for (var slot : List.copyOf(states.keySet())) {
			if (!available.contains(slot) || !keys.containsKey(slot)) {
				switchOff(slot, now);
			}
		}
	}

	/** Toggle: switches on or off. Flash: switches on until {@link #release}. Unavailable keys do nothing. */
	public boolean press(int slot, Instant now) {
		var key = keys.get(slot);
		if (key == null || !available.contains(slot)) {
			return false;
		}
		var state = states.get(slot);
		if (key.mode() == KeyMode.TOGGLE && state != null && state.on()) {
			switchOff(slot, now);
		} else {
			switchOn(slot, now);
		}
		return true;
	}

	/** Releasing a flash key switches it off; toggles ignore it. */
	public boolean release(int slot, Instant now) {
		var key = keys.get(slot);
		if (key == null || key.mode() != KeyMode.FLASH || !states.containsKey(slot)) {
			return false;
		}
		switchOff(slot, now);
		return true;
	}

	/** Fades every key out, e.g. at the end of the show. */
	public void allOff(Instant now) {
		List.copyOf(states.keySet()).forEach(slot -> switchOff(slot, now));
	}

	/**
	 * Forgets keys that have faded out completely.
	 *
	 * @return whether a fade ended (for notifying clients)
	 */
	public boolean tick(Instant now) {
		var changed = false;
		for (var slot : List.copyOf(states.keySet())) {
			var state = states.get(slot);
			if (!state.on() && level(slot, now) <= 0) {
				states.remove(slot);
				changed = true;
			} else if (state.fading(now, fadeOf(slot))) {
				continue;
			} else if (!state.reported()) {
				states.put(slot, state.markReported());
				changed = true;
			}
		}
		return changed;
	}

	/** Lays the active keys over the look (in slot order: a higher key wins on the same attribute). */
	public Map<Long, FixtureValues> apply(Map<Long, FixtureValues> look, Instant now, FixtureAttributes attributes) {
		var result = look;
		for (var slot : List.copyOf(states.keySet()).stream().sorted().toList()) {
			var key = keys.get(slot);
			var level = level(slot, now);
			if (key == null || level <= 0) {
				continue;
			}
			var seconds = Math.max(0, Duration.between(states.get(slot).startedAt(), now).toNanos() / 1e9);
			result = switch (key.look()) {
				case CompositionLook played -> CompositionMixer.apply(result,
						CompositionMixer.contribution(played.composition(), seconds, attributes),
						played.composition().pattern(), played.composition().level() * level, attributes);
				case SceneLook played -> {
					var scene = played.values();
					for (var composition : played.compositions()) {
						scene = CompositionMixer.apply(scene, composition, seconds, attributes);
					}
					yield CompositionMixer.overlay(result, scene, level);
				}
			};
		}
		return result;
	}

	public List<KeyStatus> status(Instant now) {
		var result = new ArrayList<KeyStatus>();
		for (var key : keys.values().stream().sorted((a, b) -> Integer.compare(a.slot(), b.slot())).toList()) {
			var state = states.get(key.slot());
			var kind = key.look() instanceof CompositionLook look
					? Optional.of(look.composition().pattern().kind())
					: Optional.<PatternKind>empty();
			result.add(new KeyStatus(key.slot(), key.name(), kind, key.mode(),
					state != null && state.on(), level(key.slot(), now), available.contains(key.slot())));
		}
		return result;
	}

	/** Current fade level of a key, 0 if it is off. */
	public double level(int slot, Instant now) {
		var state = states.get(slot);
		return state == null ? 0 : state.level(now, fadeOf(slot));
	}

	public Set<Integer> activeSlots() {
		var result = new HashSet<Integer>();
		states.forEach((slot, state) -> {
			if (state.on()) {
				result.add(slot);
			}
		});
		return result;
	}

	private void switchOn(int slot, Instant now) {
		var state = states.get(slot);
		// a key switched on again while fading out keeps its phase
		var startedAt = state != null ? state.startedAt() : now;
		var from = state != null ? state.level(now, fadeOf(slot)) : 0;
		states.put(slot, new State(true, from, now, startedAt, false));
	}

	private void switchOff(int slot, Instant now) {
		var state = states.get(slot);
		if (state == null || !state.on()) {
			return;
		}
		states.put(slot, new State(false, state.level(now, fadeOf(slot)), now, state.startedAt(), false));
	}

	private Duration fadeOf(int slot) {
		var key = keys.get(slot);
		return key == null ? Duration.ZERO : key.fade();
	}

	/**
	 * @param on        fading towards 1 (true) or 0 (false)
	 * @param fromLevel level when the direction last changed
	 * @param changedAt when the direction last changed
	 * @param startedAt when the composition started (phase)
	 * @param reported  the end of the fade was already reported
	 */
	private record State(boolean on, double fromLevel, Instant changedAt, Instant startedAt, boolean reported) {

		/** A full fade takes {@code fade}; starting part-way takes proportionally less. */
		double level(Instant now, Duration fade) {
			var target = on ? 1.0 : 0.0;
			if (fade.isZero()) {
				return target;
			}
			var progress = Duration.between(changedAt, now).toNanos() / (double) fade.toNanos();
			var level = on ? fromLevel + progress : fromLevel - progress;
			return Math.min(1, Math.max(0, level));
		}

		boolean fading(Instant now, Duration fade) {
			var level = level(now, fade);
			return on ? level < 1 : level > 0;
		}

		State markReported() {
			return new State(on, fromLevel, changedAt, startedAt, true);
		}
	}
}
