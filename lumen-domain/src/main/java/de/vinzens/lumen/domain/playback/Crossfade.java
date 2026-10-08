package de.vinzens.lumen.domain.playback;

import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.library.FadeBehavior;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.scene.FadeCurve;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Mixes the outgoing look (a snapshot of the output at GO) with the incoming scene. Fade-out and fade-in run
 * in parallel, each with its own duration:
 * <ul>
 * <li>intensity: straight from the old to the new value, never through a dip – rising with the fade-in (after the
 * delay), falling with the fade-out. A fixture only one side lights fades in or out alone.</li>
 * <li>smooth attributes (color, position, …): LTP, faded from the old to the new value with the fade-in;
 * if the fixture was dark at GO the new value is taken at once (invisible, no color smearing)</li>
 * <li>snap attributes (gobo, color wheel, …): LTP, jump when the fade-in starts</li>
 * <li>attributes the new scene does not set keep their old value</li>
 * </ul>
 * An optional delay holds back the fade-in while the fade-out starts at GO. A {@link FadeCurve} shapes the levels
 * and the progress of smooth attributes.
 */
public final class Crossfade {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	private static final double DARK = 0.001;

	private Crossfade() {
	}

	public static Map<Long, FixtureValues> mix(Map<Long, FixtureValues> from, Map<Long, FixtureValues> to,
			Duration elapsed, Duration fadeIn, Duration fadeOut, FadeBehaviors behaviors) {
		return mix(from, to, elapsed, fadeIn, fadeOut, Duration.ZERO, behaviors, null);
	}

	/**
	 * @param delay    time after GO before the fade-in starts
	 * @param darkAtGo fixtures that were dark at GO; {@code null} = decide by the intensity in {@code from}.
	 *                 Needed when {@code from} keeps moving (running compositions).
	 */
	public static Map<Long, FixtureValues> mix(Map<Long, FixtureValues> from, Map<Long, FixtureValues> to,
			Duration elapsed, Duration fadeIn, Duration fadeOut, Duration delay, FadeBehaviors behaviors,
			Set<Long> darkAtGo) {
		return mix(from, to, elapsed, fadeIn, fadeOut, delay, FadeCurve.LINEAR, behaviors, darkAtGo);
	}

	/** @param curve shape of the fade */
	public static Map<Long, FixtureValues> mix(Map<Long, FixtureValues> from, Map<Long, FixtureValues> to,
			Duration elapsed, Duration fadeIn, Duration fadeOut, Duration delay, FadeCurve curve,
			FadeBehaviors behaviors, Set<Long> darkAtGo) {
		var sinceFadeIn = elapsed.minus(delay);
		var linearIn = progress(sinceFadeIn, fadeIn);
		var levelIn = curve.level(linearIn);
		var levelOut = curve.level(1 - progress(elapsed, fadeOut));
		var moved = curve.progress(linearIn);
		var fadeInStarted = !sinceFadeIn.isNegative();

		var fixtures = new HashSet<>(from.keySet());
		fixtures.addAll(to.keySet());
		var result = new HashMap<Long, FixtureValues>();
		for (var fixtureId : fixtures) {
			var old = from.getOrDefault(fixtureId, FixtureValues.EMPTY);
			var target = to.getOrDefault(fixtureId, FixtureValues.EMPTY);
			var wasDark = darkAtGo != null ? darkAtGo.contains(fixtureId) : isDark(old);
			result.put(fixtureId, mixFixture(fixtureId, old, target, wasDark, levelIn, levelOut, moved,
					fadeInStarted, behaviors));
		}
		return result;
	}

	/** Whether a fixture gives no visible light. */
	public static boolean isDark(FixtureValues values) {
		return values.get(DIMMER).orElse(0.0) < DARK;
	}

	private static FixtureValues mixFixture(long fixtureId, FixtureValues old, FixtureValues target, boolean wasDark,
			double levelIn, double levelOut, double moved, boolean fadeInStarted, FadeBehaviors behaviors) {
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
				value = intensity(oldValue.orElse(0.0), newValue.orElse(0.0), levelIn, levelOut);
			} else if (newValue.isEmpty()) {
				value = oldValue.orElseThrow();
			} else if (oldValue.isEmpty() || wasDark) {
				value = newValue.get();
			} else if (behavior == FadeBehavior.SNAP) {
				value = fadeInStarted ? newValue.get() : oldValue.get();
			} else {
				value = oldValue.get() + (newValue.get() - oldValue.get()) * moved;
			}
			values.put(attribute, value);
		}
		return new FixtureValues(values);
	}

	/**
	 * A level going up follows the fade-in, one going down the fade-out. Fading the old value out and the new one in
	 * at the same time (HTP) would dip a fixture lit on both sides, e.g. 30 % → 23 % → 100 %, with a delay or a
	 * short fade-out almost to dark.
	 */
	static double intensity(double old, double target, double levelIn, double levelOut) {
		return old <= target ? old + (target - old) * levelIn : target + (old - target) * levelOut;
	}

	/** 0 → 1 over the duration; a zero duration is complete at once, a negative elapsed time (delay) is 0. */
	static double progress(Duration elapsed, Duration duration) {
		if (elapsed.isNegative()) {
			return 0;
		}
		if (duration.isZero() || elapsed.compareTo(duration) >= 0) {
			return 1;
		}
		return (double) elapsed.toNanos() / duration.toNanos();
	}
}
