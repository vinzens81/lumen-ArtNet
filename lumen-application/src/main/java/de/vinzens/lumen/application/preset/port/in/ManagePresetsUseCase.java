package de.vinzens.lumen.application.preset.port.in;

import de.vinzens.lumen.domain.library.AttributeGroup;
import de.vinzens.lumen.domain.preset.Preset;

import java.util.Collection;
import java.util.List;

/**
 * Presets of a project: named colors, positions or beam settings per fixture. Recording and applying work with the
 * programmer and therefore only for the active project.
 */
public interface ManagePresetsUseCase {

	List<Preset> presets(long projectId);

	/**
	 * Records a preset from the programmer values of the given fixtures (only the attributes of the group). The
	 * programmer then refers to the new preset, so a look recorded next uses it.
	 */
	Preset create(long projectId, String name, AttributeGroup group, Collection<Long> fixtureIds);

	/**
	 * Takes the programmer values of the given fixtures into the preset; its other fixtures keep their values. All
	 * looks that refer to the preset change with it.
	 */
	Preset record(long projectId, long presetId, Collection<Long> fixtureIds);

	Preset rename(long projectId, long presetId, String name);

	/** Sets the preset's values on the given fixtures in the programmer; fixtures it has no values for are skipped. */
	void apply(long projectId, long presetId, Collection<Long> fixtureIds);

	/** Deletes the preset; looks keep the values they have but no longer change with it. */
	void delete(long projectId, long presetId);
}
