package de.vinzens.lumen.domain.playback;

import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.library.FadeBehavior;
import de.vinzens.lumen.domain.output.FixtureValues;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Mixes the outgoing look (a snapshot of the output at GO) with the incoming scene. Fade-out and fade-in run
 * in parallel, each with its own duration:
 * <ul>
 * <li>intensity: HTP – {@code max(old × levelOut, new × levelIn)}</li>
 * <li>smooth attributes (color, position, …): LTP, faded from the old to the new value with the fade-in;
 * if the fixture was dark at GO the new value is taken at once (invisible, no color smearing)</li>
 * <li>snap attributes (gobo, color wheel, …): LTP, jump at GO</li>
 * <li>attributes the new scene does not set keep their old value</li>
 * </ul>
 */
public final class Crossfade {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	private static final double DARK = 0.001;

	private Crossfade() {
	}

	public static Map<Long, FixtureValues> mix(Map<Long, FixtureValues> from, Map<Long, FixtureValues> to,
			Duration elapsed, Duration fadeIn, Duration fadeOut, FadeBehaviors behaviors) {
		return mix(from, to, elapsed, fadeIn, fadeOut, behaviors, null);
	}

	/**
	 * @param darkAtGo fixtures that were dark at GO; {@code null} = decide by the intensity in {@code from}.
	 *                 Needed when {@code from} keeps moving (running compositions).
	 */
	public static Map<Long, FixtureValues> mix(Map<Long, FixtureValues> from, Map<Long, FixtureValues> to,
			Duration elapsed, Duration fadeIn, Duration fadeOut, FadeBehaviors behaviors, Set<Long> darkAtGo) {
		var levelIn = progress(elapsed, fadeIn);
		var levelOut = 1 - progress(elapsed, fadeOut);

		var fixtures = new HashSet<>(from.keySet());
		fixtures.addAll(to.keySet());
		var result = new HashMap<Long, FixtureValues>();
		for (var fixtureId : fixtures) {
			var old = from.getOrDefault(fixtureId, FixtureValues.EMPTY);
			var target = to.getOrDefault(fixtureId, FixtureValues.EMPTY);
			var wasDark = darkAtGo != null ? darkAtGo.contains(fixtureId) : isDark(old);
			result.put(fixtureId, mixFixture(fixtureId, old, target, wasDark, levelIn, levelOut, behaviors));
		}
		return result;
	}

	/** Whether a fixture gives no visible light. */
	public static boolean isDark(FixtureValues values) {
		return values.get(DIMMER).orElse(0.0) < DARK;
	}

	private static FixtureValues mixFixture(long fixtureId, FixtureValues old, FixtureValues target, boolean wasDark,
			double levelIn, double levelOut, FadeBehaviors behaviors) {
		var attributes = new HashSet<>(old.values().keySet());
		attributes.addAll(target.values().keySet());
		attributes.add(DIMMER);

		var values = new HashMap<Attribute, Double>();
		for (var attribute : attributes) {
			var oldValue = old.get(attribute);
			var newValue = target.get(attribute);
			var behavior = behaviors.of(fixtureId, attribute);
			double value;
			if (behavior == FadeBehavior.INTENSITY) {
				value = Math.max(oldValue.orElse(0.0) * levelOut, newValue.orElse(0.0) * levelIn);
			} else if (newValue.isEmpty()) {
				value = oldValue.orElseThrow();
			} else if (oldValue.isEmpty() || wasDark || behavior == FadeBehavior.SNAP) {
				value = newValue.get();
			} else {
				value = oldValue.get() + (newValue.get() - oldValue.get()) * levelIn;
			}
			values.put(attribute, value);
		}
		return new FixtureValues(values);
	}

	/** 0 → 1 over the duration; a zero duration is complete at once. */
	static double progress(Duration elapsed, Duration duration) {
		if (duration.isZero() || elapsed.compareTo(duration) >= 0) {
			return 1;
		}
		if (elapsed.isNegative()) {
			return 0;
		}
		return (double) elapsed.toNanos() / duration.toNanos();
	}
}
