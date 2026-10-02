package de.vinzens.lumen.domain.output;

import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.FadeBehavior;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Combines all output layers into one set of values per fixture: intensity is merged HTP (highest takes
 * precedence), all other attributes LTP (the layer with the highest priority wins).
 */
public final class LayerMerger {

	private LayerMerger() {
	}

	public static Map<Long, FixtureValues> merge(List<LayerOutput> layers) {
		var merged = new HashMap<Long, Map<Attribute, Double>>();
		var byPriority = layers.stream().sorted(Comparator.comparingInt(LayerOutput::priority)).toList();

		for (var layer : byPriority) {
			layer.fixtures().forEach((fixtureId, values) -> {
				var target = merged.computeIfAbsent(fixtureId, id -> new HashMap<>());
				values.values().forEach((attribute, value) -> {
					if (attribute.function().defaultFade() == FadeBehavior.INTENSITY) {
						target.merge(attribute, value, Math::max);
					} else {
						target.put(attribute, value);
					}
				});
			});
		}

		var result = new HashMap<Long, FixtureValues>();
		merged.forEach((fixtureId, values) -> result.put(fixtureId, new FixtureValues(values)));
		return result;
	}
}
