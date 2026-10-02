package de.vinzens.lumen.domain.scene;

import de.vinzens.lumen.domain.output.FixtureValues;

import java.util.Map;
import java.util.Objects;

/**
 * A look of the show: values per fixture. Fixtures that are not part of the scene are dark in it.
 */
public record Scene(long id, long projectId, SceneSpec spec, Map<Long, FixtureValues> values) {

	public Scene {
		Objects.requireNonNull(spec, "spec");
		values = Map.copyOf(values);
	}
}
