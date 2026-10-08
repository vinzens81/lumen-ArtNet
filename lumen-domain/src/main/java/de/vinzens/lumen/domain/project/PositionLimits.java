package de.vinzens.lumen.domain.project;

import de.vinzens.lumen.domain.Validation;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.AttributeGroup;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.output.FixtureValues;

import java.util.HashMap;
import java.util.Map;

/**
 * The allowed range of pan and tilt of a fixture in DMX values (inclusive), e.g. so a head never points into the
 * audience. Applies to everything the show plays – scenes, party, keys – but not to the programmer.
 */
public record PositionLimits(int panMin, int panMax, int tiltMin, int tiltMax) {

	public PositionLimits {
		new Validation()
				.require(panMin >= 0 && panMax <= 255 && panMin <= panMax,
						"pan limits must be within 0–255, min not above max")
				.require(tiltMin >= 0 && tiltMax <= 255 && tiltMin <= tiltMax,
						"tilt limits must be within 0–255, min not above max")
				.throwIfInvalid();
	}

	/** The values with pan and tilt (of every head) held within the limits. */
	public FixtureValues apply(FixtureValues values) {
		Map<Attribute, Double> result = null;
		for (var entry : values.values().entrySet()) {
			var attribute = entry.getKey();
			if (AttributeGroup.of(attribute.function()) != AttributeGroup.POSITION) {
				continue;
			}
			var pan = attribute.function() == ChannelFunction.PAN;
			var min = (pan ? panMin : tiltMin) / 255.0;
			var max = (pan ? panMax : tiltMax) / 255.0;
			var limited = Math.min(max, Math.max(min, entry.getValue()));
			if (limited != entry.getValue()) {
				if (result == null) {
					result = new HashMap<>(values.values());
				}
				result.put(attribute, limited);
			}
		}
		return result == null ? values : new FixtureValues(result);
	}

	/** Limits every fixture that has limits. */
	public static Map<Long, FixtureValues> apply(Map<Long, FixtureValues> values, Map<Long, PositionLimits> limits) {
		if (limits.isEmpty()) {
			return values;
		}
		var result = new HashMap<>(values);
		limits.forEach((fixtureId, limit) -> {
			var fixtureValues = values.get(fixtureId);
			if (fixtureValues != null) {
				result.put(fixtureId, limit.apply(fixtureValues));
			}
		});
		return result;
	}
}
