package de.vinzens.lumen.application.preset.port.out;

import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.preset.Preset;
import de.vinzens.lumen.domain.preset.PresetSpec;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PresetRepository {

	/** Presets of the project in the order they were made. */
	List<Preset> findByProject(long projectId);

	Optional<Preset> findById(long id);

	Preset create(long projectId, PresetSpec spec, Map<Long, FixtureValues> values);

	Preset updateSpec(long id, PresetSpec spec);

	Preset replaceValues(long id, Map<Long, FixtureValues> values);

	/** Deletes the preset; scenes keep their values but no longer refer to it. */
	void delete(long id);
}
