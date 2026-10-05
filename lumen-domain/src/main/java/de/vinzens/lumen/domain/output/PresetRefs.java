package de.vinzens.lumen.domain.output;

import de.vinzens.lumen.domain.library.AttributeGroup;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Which preset the values of a fixture come from, per attribute group: e.g. fixture 3 takes its color from preset
 * "Warm". A scene that refers to a preset changes with it. Immutable.
 */
public record PresetRefs(Map<Long, Map<AttributeGroup, Long>> refs) {

	public static final PresetRefs NONE = new PresetRefs(Map.of());

	public PresetRefs {
		var copy = new HashMap<Long, Map<AttributeGroup, Long>>();
		refs.forEach((fixtureId, groups) -> {
			if (!groups.isEmpty()) {
				copy.put(fixtureId, Map.copyOf(groups));
			}
		});
		refs = Map.copyOf(copy);
	}

	public Optional<Long> presetOf(long fixtureId, AttributeGroup group) {
		return Optional.ofNullable(refs.getOrDefault(fixtureId, Map.of()).get(group));
	}

	public PresetRefs with(long fixtureId, AttributeGroup group, long presetId) {
		var updated = mutable();
		updated.computeIfAbsent(fixtureId, id -> new EnumMap<>(AttributeGroup.class)).put(group, presetId);
		return new PresetRefs(updated);
	}

	/** Drops the references of a fixture for the given groups. */
	public PresetRefs without(long fixtureId, Collection<AttributeGroup> groups) {
		if (!refs.containsKey(fixtureId)) {
			return this;
		}
		var updated = mutable();
		updated.get(fixtureId).keySet().removeAll(groups);
		return new PresetRefs(updated);
	}

	public PresetRefs withoutFixture(long fixtureId) {
		return without(fixtureId, Set.of(AttributeGroup.values()));
	}

	/** Drops every reference to a preset, e.g. when it is deleted. */
	public PresetRefs withoutPreset(long presetId) {
		var updated = mutable();
		updated.values().forEach(groups -> groups.values().removeIf(id -> id == presetId));
		return new PresetRefs(updated);
	}

	/** Fixtures that take values from the preset. */
	public Set<Long> fixturesUsing(long presetId) {
		var result = new HashSet<Long>();
		refs.forEach((fixtureId, groups) -> {
			if (groups.containsValue(presetId)) {
				result.add(fixtureId);
			}
		});
		return result;
	}

	/** Only the references of these fixtures. */
	public PresetRefs only(Collection<Long> fixtureIds) {
		var updated = mutable();
		updated.keySet().retainAll(fixtureIds);
		return new PresetRefs(updated);
	}

	public boolean isEmpty() {
		return refs.isEmpty();
	}

	private Map<Long, Map<AttributeGroup, Long>> mutable() {
		var copy = new HashMap<Long, Map<AttributeGroup, Long>>();
		refs.forEach((fixtureId, groups) -> {
			var groupCopy = new EnumMap<AttributeGroup, Long>(AttributeGroup.class);
			groupCopy.putAll(groups);
			copy.put(fixtureId, groupCopy);
		});
		return copy;
	}
}
