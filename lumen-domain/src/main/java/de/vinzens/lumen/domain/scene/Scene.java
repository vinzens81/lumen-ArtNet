package de.vinzens.lumen.domain.scene;

import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.output.PresetRefs;

import java.util.Map;
import java.util.Objects;

/**
 * A look of the show: values per fixture. Fixtures that are not part of the scene are dark in it.
 *
 * @param presets presets the values come from; the values already contain the presets' current values
 */
public record Scene(long id, long projectId, SceneSpec spec, Map<Long, FixtureValues> values, PresetRefs presets) {

	public Scene {
		Objects.requireNonNull(spec, "spec");
		values = Map.copyOf(values);
		Objects.requireNonNull(presets, "presets");
	}

	/** A scene without presets. */
	public Scene(long id, long projectId, SceneSpec spec, Map<Long, FixtureValues> values) {
		this(id, projectId, spec, values, PresetRefs.NONE);
	}

	public Look look() {
		return new Look(values, presets);
	}
}
