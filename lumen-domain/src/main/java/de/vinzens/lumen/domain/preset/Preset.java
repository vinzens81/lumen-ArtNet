package de.vinzens.lumen.domain.preset;

import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.output.FixtureValues;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Named values for one attribute group, per fixture – e.g. the color "Warm" for each front light or the position
 * "Chair" for each moving head. Scenes and the programmer refer to a preset, so changing it changes them all.
 *
 * @param values only attributes of the preset's group, per fixture id
 */
public record Preset(long id, long projectId, PresetSpec spec, Map<Long, FixtureValues> values) {

	public static final int MAX_PER_PROJECT = 200;

	public Preset {
		Objects.requireNonNull(spec, "spec");
		values = Map.copyOf(values);
		values.forEach((fixtureId, fixtureValues) -> fixtureValues.values().keySet().forEach(attribute -> {
			if (!spec.group().contains(attribute)) {
				throw new DomainValidationException(List.of("A " + spec.group().id() + " preset cannot hold "
						+ attribute.id()));
			}
		}));
	}

	/** Takes the values of the preset's group from a look, e.g. the programmer. Fixtures without any are left out. */
	public static Map<Long, FixtureValues> valuesOf(PresetSpec spec, Map<Long, FixtureValues> look) {
		var result = new HashMap<Long, FixtureValues>();
		look.forEach((fixtureId, fixtureValues) -> {
			var taken = new HashMap<>(fixtureValues.values());
			taken.keySet().removeIf(attribute -> !spec.group().contains(attribute));
			if (!taken.isEmpty()) {
				result.put(fixtureId, new FixtureValues(taken));
			}
		});
		return result;
	}
}
