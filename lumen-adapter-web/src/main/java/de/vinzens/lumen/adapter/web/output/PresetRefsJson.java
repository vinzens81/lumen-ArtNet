package de.vinzens.lumen.adapter.web.output;

import de.vinzens.lumen.domain.library.AttributeGroup;
import de.vinzens.lumen.domain.output.PresetRefs;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * JSON form of preset references: {@code {"3": {"color": 12, "position": 7}}} – fixture id → attribute group →
 * preset id.
 */
public final class PresetRefsJson {

	private PresetRefsJson() {
	}

	public static Map<Long, Map<String, Long>> toJson(PresetRefs presets) {
		var result = new TreeMap<Long, Map<String, Long>>();
		presets.refs().forEach((fixtureId, groups) -> {
			var perGroup = new TreeMap<String, Long>();
			groups.forEach((group, presetId) -> perGroup.put(group.id(), presetId));
			result.put(fixtureId, perGroup);
		});
		return result;
	}

	public static PresetRefs fromJson(Map<Long, Map<String, Long>> json) {
		var refs = new HashMap<Long, Map<AttributeGroup, Long>>();
		(json == null ? Map.<Long, Map<String, Long>>of() : json).forEach((fixtureId, groups) -> {
			if (groups != null) {
				groups.forEach((group, presetId) ->
						refs.computeIfAbsent(fixtureId, id -> new HashMap<>()).put(AttributeGroup.parse(group), presetId));
			}
		});
		return new PresetRefs(refs);
	}
}
