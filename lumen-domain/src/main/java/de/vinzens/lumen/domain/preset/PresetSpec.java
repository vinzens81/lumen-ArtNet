package de.vinzens.lumen.domain.preset;

import de.vinzens.lumen.domain.Validation;
import de.vinzens.lumen.domain.library.AttributeGroup;

import java.util.Set;

/**
 * Editable properties of a preset.
 *
 * @param group what the preset stores: colors or beam settings (a stored position is a move with one point)
 */
public record PresetSpec(String name, AttributeGroup group) {

	/** Groups a preset can be made for. */
	public static final Set<AttributeGroup> GROUPS = Set.of(AttributeGroup.COLOR, AttributeGroup.BEAM);

	public PresetSpec {
		new Validation()
				.requireText(name, "name")
				.require(group != null && GROUPS.contains(group), "group must be color or beam – positions are moves")
				.throwIfInvalid();
		name = name.strip();
	}
}
