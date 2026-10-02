package de.vinzens.lumen.application.composition.port.in;

import de.vinzens.lumen.domain.composition.Composition;
import de.vinzens.lumen.domain.composition.CompositionSpec;

import java.util.List;
import java.util.Map;

/**
 * The composition library of a project and the copies of compositions in scenes.
 */
public interface ManageCompositionsUseCase {

	List<Composition> library(long projectId);

	Composition composition(long projectId, long compositionId);

	Composition createInLibrary(long projectId, CompositionSpec spec);

	/** Changes a library composition or a copy in a scene; copies in other scenes are not affected. */
	Composition update(long projectId, long compositionId, CompositionSpec spec);

	/** Deleting a library composition keeps its copies; they only lose their origin. */
	void delete(long projectId, long compositionId);

	/** Copies in all scenes of the project: scene id → copies in their order. */
	Map<Long, List<Composition>> sceneCompositions(long projectId);

	/** Copies in the scene, in their order (later ones win on the same attribute). */
	List<Composition> sceneCompositions(long projectId, long sceneId);

	/** Copies a library composition into a scene, at the end. */
	Composition insertIntoScene(long projectId, long sceneId, long libraryCompositionId);

	/** New order of all copies in the scene. */
	List<Composition> reorderScene(long projectId, long sceneId, List<Long> compositionIds);

	/** Takes pattern and target of the library origin again; level, speed and restart of the copy stay. */
	Composition reloadFromLibrary(long projectId, long copyId);

	/** Writes pattern and target of a copy back to its library origin (or creates one if it has none). */
	Composition saveToLibrary(long projectId, long copyId);
}
