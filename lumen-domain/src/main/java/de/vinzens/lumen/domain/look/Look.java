package de.vinzens.lumen.domain.look;

import de.vinzens.lumen.domain.library.AttributeGroup;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.output.PresetRefs;
import de.vinzens.lumen.domain.preset.Preset;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Values per fixture id together with the presets they come from: what a look or the programmer holds.
 */
public record Look(Map<Long, FixtureValues> values, PresetRefs presets) {

	public static final Look EMPTY = new Look(Map.of(), PresetRefs.NONE);

	public Look {
		values = Map.copyOf(values);
		Objects.requireNonNull(presets, "presets");
		presets = presets.only(values.keySet());
	}

	public static Look of(Map<Long, FixtureValues> values) {
		return new Look(values, PresetRefs.NONE);
	}

	/**
	 * Takes values from {@code changes} attribute by attribute, so everything the changes do not set stays as it is.
	 * Where values are taken, the preset references of the changes come along: a group taken without a reference
	 * no longer refers to a preset.
	 *
	 * @param fixtureIds only these fixtures, empty = all fixtures of the changes
	 * @param groups     only attributes of these groups, empty = all attributes
	 */
	public Look merge(Look changes, Optional<Set<Long>> fixtureIds, Set<AttributeGroup> groups) {
		var mergedValues = new HashMap<>(values);
		var mergedPresets = presets;
		for (var entry : changes.values().entrySet()) {
			var fixtureId = entry.getKey();
			if (fixtureIds.isPresent() && !fixtureIds.get().contains(fixtureId)) {
				continue;
			}
			var taken = new HashMap<>(entry.getValue().values());
			taken.keySet().removeIf(attribute -> !groups.isEmpty()
					&& groups.stream().noneMatch(group -> group.contains(attribute)));
			if (taken.isEmpty()) {
				continue;
			}
			mergedValues.merge(fixtureId, new FixtureValues(taken), FixtureValues::with);
			var touched = EnumSet.noneOf(AttributeGroup.class);
			taken.keySet().forEach(attribute -> touched.add(AttributeGroup.of(attribute.function())));
			mergedPresets = mergedPresets.without(fixtureId, touched);
			for (var group : touched) {
				var preset = changes.presets().presetOf(fixtureId, group);
				if (preset.isPresent()) {
					mergedPresets = mergedPresets.with(fixtureId, group, preset.get());
				}
			}
		}
		return new Look(mergedValues, mergedPresets);
	}

	/**
	 * Takes the current values of a preset for every fixture that refers to it. A fixture the preset has no values
	 * for keeps its values.
	 */
	public Look withPreset(Preset preset) {
		var group = preset.spec().group();
		var updated = new HashMap<>(values);
		for (var fixtureId : presets.fixturesUsing(preset.id())) {
			var presetValues = preset.values().get(fixtureId);
			if (presetValues == null || presets.presetOf(fixtureId, group).orElse(-1L) != preset.id()) {
				continue;
			}
			var fixtureValues = new HashMap<>(values.getOrDefault(fixtureId, FixtureValues.EMPTY).values());
			fixtureValues.keySet().removeIf(group::contains);
			fixtureValues.putAll(presetValues.values());
			updated.put(fixtureId, new FixtureValues(fixtureValues));
		}
		return new Look(updated, presets);
	}

	/** Whether any fixture refers to the preset. */
	public boolean uses(long presetId) {
		return !presets.fixturesUsing(presetId).isEmpty();
	}
}
