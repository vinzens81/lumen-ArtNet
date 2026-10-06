package de.vinzens.lumen.application.project;

import de.vinzens.lumen.domain.composition.Composition;
import de.vinzens.lumen.domain.composition.Fader;
import de.vinzens.lumen.domain.look.NamedLook;
import de.vinzens.lumen.domain.scene.SceneComposition;
import de.vinzens.lumen.domain.scene.SceneKey;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Looks, compositions and what the scenes play, connected by ids of their own (an export, or the result of
 * converting an old show) – to be written into a project with new ids.
 *
 * @param sceneCompositions per scene id: its compositions, referring to the compositions here
 * @param sceneKeys         per scene id: its keys, referring to the compositions here
 * @param faders            faders, referring to the compositions here
 * @param removedScenes     scene ids that are no longer part of the show (scenes that were outside the cue list)
 */
public record ShowContent(List<NamedLook> looks, List<Composition> compositions,
		Map<Long, List<SceneComposition>> sceneCompositions, Map<Long, List<SceneKey>> sceneKeys, List<Fader> faders,
		Set<Long> removedScenes) {

	public ShowContent {
		looks = List.copyOf(looks);
		compositions = List.copyOf(compositions);
		sceneCompositions = Map.copyOf(sceneCompositions);
		sceneKeys = Map.copyOf(sceneKeys);
		faders = List.copyOf(faders);
		removedScenes = Set.copyOf(removedScenes);
	}
}
