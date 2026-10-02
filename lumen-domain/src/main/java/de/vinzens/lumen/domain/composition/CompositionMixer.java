package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.output.FixtureValues;

import java.util.HashMap;
import java.util.Map;

/**
 * Lays compositions over a static look:
 * <ul>
 * <li>intensity, absolute: {@code max(base, composition × level)} (HTP)</li>
 * <li>intensity, relative: {@code base × (1 − level + factor × level)}</li>
 * <li>other attributes: {@code base + (composition − base) × level}; without base the composition value</li>
 * <li>a color composition on a fixture without base intensity lights it with its fallback intensity × level</li>
 * </ul>
 * Compositions are applied in order, so a later one wins on the same attribute.
 */
public final class CompositionMixer {

	private CompositionMixer() {
	}

	/** Values of the composition per target fixture at the given time since its start (before the speed factor). */
	public static Map<Long, FixtureValues> contribution(ActiveComposition composition, double seconds,
			FixtureAttributes attributes) {
		var result = new HashMap<Long, FixtureValues>();
		var fixtures = composition.fixtureIds();
		var scaled = composition.patternSeconds(seconds);
		for (int i = 0; i < fixtures.size(); i++) {
			var fixtureId = fixtures.get(i);
			var values = composition.pattern().valuesAt(scaled, i, fixtures.size(), fixtureId);
			var supported = new HashMap<Attribute, Double>();
			values.values().forEach((attribute, value) -> {
				if (attributes.has(fixtureId, attribute)) {
					supported.put(attribute, value);
				}
			});
			result.put(fixtureId, new FixtureValues(supported));
		}
		return result;
	}

	/** Applies one composition to the look (returns a new map). */
	public static Map<Long, FixtureValues> apply(Map<Long, FixtureValues> look, ActiveComposition composition,
			double seconds, FixtureAttributes attributes) {
		return apply(look, contribution(composition, seconds, attributes), composition.pattern(), composition.level(),
				attributes);
	}

	/** Applies precomputed composition values with the rules of the pattern and the given level. */
	public static Map<Long, FixtureValues> apply(Map<Long, FixtureValues> look, Map<Long, FixtureValues> contribution,
			Pattern pattern, double level, FixtureAttributes attributes) {
		var result = new HashMap<>(look);
		contribution.forEach((fixtureId, values) -> {
			var base = look.getOrDefault(fixtureId, FixtureValues.EMPTY);
			var mixed = new HashMap<>(base.values());
			values.values().forEach((attribute, value) -> {
				var baseValue = base.get(attribute);
				if (attribute.equals(Values.DIMMER)) {
					mixed.put(attribute, switch (pattern.intensityMode()) {
						case ABSOLUTE -> Math.max(baseValue.orElse(0.0), value * level);
						case RELATIVE -> baseValue.orElse(0.0) * (1 - level + value * level);
					});
				} else {
					mixed.put(attribute, baseValue.map(b -> b + (value - b) * level).orElse(value));
				}
			});
			var fallback = pattern.fallbackIntensity();
			if (fallback.isPresent() && base.get(Values.DIMMER).isEmpty() && !values.isEmpty()
					&& attributes.has(fixtureId, Values.DIMMER)) {
				mixed.put(Values.DIMMER, fallback.get() * level);
			}
			result.put(fixtureId, new FixtureValues(clamped(mixed)));
		});
		return result;
	}

	/**
	 * Lays a whole look over another one, e.g. a scene on an extra key: intensity HTP
	 * ({@code max(look, value × level)}), other attributes faded in by the level. Fixtures the overlay does not
	 * contain keep their values.
	 */
	public static Map<Long, FixtureValues> overlay(Map<Long, FixtureValues> look, Map<Long, FixtureValues> overlay,
			double level) {
		var result = new HashMap<>(look);
		overlay.forEach((fixtureId, values) -> {
			var base = look.getOrDefault(fixtureId, FixtureValues.EMPTY);
			var mixed = new HashMap<>(base.values());
			values.values().forEach((attribute, value) -> {
				var baseValue = base.get(attribute);
				mixed.put(attribute, attribute.equals(Values.DIMMER)
						? Math.max(baseValue.orElse(0.0), value * level)
						: baseValue.map(b -> b + (value - b) * level).orElse(value));
			});
			result.put(fixtureId, new FixtureValues(clamped(mixed)));
		});
		return result;
	}

	private static Map<Attribute, Double> clamped(Map<Attribute, Double> values) {
		var result = new HashMap<Attribute, Double>();
		values.forEach((attribute, value) -> result.put(attribute, Values.clamp(value)));
		return result;
	}
}
