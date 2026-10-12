package de.vinzens.lumen.domain.look;

import de.vinzens.lumen.domain.Folders;
import de.vinzens.lumen.domain.Validation;

import java.util.Objects;
import java.util.Optional;

/**
 * Editable properties of a look.
 *
 * @param owner  the composition the look belongs to (recorded as a step or made by a generator); empty = a shared
 *               look of the project that any composition can use
 * @param folder the folder the look is listed in, e.g. "Manual" or "Auto generated"
 */
public record LookSpec(String name, Optional<Long> owner, String folder) {

	public LookSpec {
		Objects.requireNonNull(owner, "owner");
		folder = Folders.normalize(folder);
		new Validation()
				.requireText(name, "name")
				.require(folder.length() <= Folders.MAX_LENGTH,
						"folder must have at most " + Folders.MAX_LENGTH + " characters")
				.throwIfInvalid();
		name = name.strip();
	}

	/** In the default folder. */
	public LookSpec(String name, Optional<Long> owner) {
		this(name, owner, Folders.MANUAL);
	}

	/** A shared look. */
	public static LookSpec shared(String name) {
		return new LookSpec(name, Optional.empty());
	}

	public LookSpec withFolder(String newFolder) {
		return new LookSpec(name, owner, newFolder);
	}

	public boolean isShared() {
		return owner.isEmpty();
	}
}
