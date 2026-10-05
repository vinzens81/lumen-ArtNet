package de.vinzens.lumen.domain.scene;

import de.vinzens.lumen.domain.library.AttributeGroup;
import de.vinzens.lumen.domain.output.FixtureValues;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Operations on the values of a scene (per fixture id).
 */
public final class SceneValues {

	private SceneValues() {
	}

	/**
	 * Takes values from {@code changes} into {@code base}: attribute by attribute, so everything the changes do not
	 * set stays as it is.
	 *
	 * @param fixtureIds only these fixtures, empty = all fixtures of the changes
	 * @param groups     only attributes of these groups, empty = all attributes
	 */
	public static Map<Long, FixtureValues> merge(Map<Long, FixtureValues> base, Map<Long, FixtureValues> changes,
			Optional<Set<Long>> fixtureIds, Set<AttributeGroup> groups) {
		var result = new HashMap<>(base);
		changes.forEach((fixtureId, values) -> {
			if (fixtureIds.isPresent() && !fixtureIds.get().contains(fixtureId)) {
				return;
			}
			var taken = new HashMap<>(values.values());
			taken.keySet().removeIf(attribute -> !groups.isEmpty()
					&& groups.stream().noneMatch(group -> group.contains(attribute)));
			if (!taken.isEmpty()) {
				result.merge(fixtureId, new FixtureValues(taken), FixtureValues::with);
			}
		});
		return result;
	}
}
