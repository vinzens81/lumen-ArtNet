package de.vinzens.lumen.domain.composition;

import java.util.Objects;
import java.util.Optional;

/**
 * A composition of a project: in the library ({@code sceneId} empty) or a copy in a scene.
 *
 * @param sourceId the library composition a copy was made from, if it still exists
 * @param position order within the scene; later compositions win on the same attribute
 */
public record Composition(long id, long projectId, Optional<Long> sceneId, Optional<Long> sourceId, int position,
		CompositionSpec spec) {

	public Composition {
		Objects.requireNonNull(sceneId, "sceneId");
		Objects.requireNonNull(sourceId, "sourceId");
		Objects.requireNonNull(spec, "spec");
	}

	public boolean inLibrary() {
		return sceneId.isEmpty();
	}
}
