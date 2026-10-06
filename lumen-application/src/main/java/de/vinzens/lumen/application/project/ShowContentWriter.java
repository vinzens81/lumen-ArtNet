package de.vinzens.lumen.application.project;

import de.vinzens.lumen.application.composition.port.out.CompositionRepository;
import de.vinzens.lumen.application.look.port.out.LookRepository;
import de.vinzens.lumen.application.playback.port.out.FaderRepository;
import de.vinzens.lumen.application.scene.port.out.SceneRepository;
import de.vinzens.lumen.domain.composition.Fader;
import de.vinzens.lumen.domain.look.LookSpec;
import de.vinzens.lumen.domain.scene.SceneComposition;
import de.vinzens.lumen.domain.scene.SceneKey;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Writes {@link ShowContent} into a project: compositions, their looks (own looks stay with their composition), what
 * the scenes play and the faders. Fixture, group and preset ids in the content must already be those of the project.
 */
public final class ShowContentWriter {

	private final LookRepository looks;
	private final CompositionRepository compositions;
	private final SceneRepository scenes;
	private final FaderRepository faders;

	public ShowContentWriter(LookRepository looks, CompositionRepository compositions, SceneRepository scenes,
			FaderRepository faders) {
		this.looks = Objects.requireNonNull(looks, "looks");
		this.compositions = Objects.requireNonNull(compositions, "compositions");
		this.scenes = Objects.requireNonNull(scenes, "scenes");
		this.faders = Objects.requireNonNull(faders, "faders");
	}

	/**
	 * @param sceneIds scene ids of the content → scenes of the project, which must exist; scenes the content does not
	 *                 mention keep what they play
	 */
	public Written write(long projectId, ShowContent content, Map<Long, Long> sceneIds) {
		var compositionIds = new HashMap<Long, Long>();
		for (var composition : content.compositions()) {
			compositionIds.put(composition.id(), compositions.create(projectId, composition.spec(), List.of()).id());
		}
		var lookIds = new HashMap<Long, Long>();
		for (var look : content.looks()) {
			var owner = look.spec().owner().map(compositionIds::get);
			lookIds.put(look.id(), looks.create(projectId, new LookSpec(look.spec().name(), owner), look.look()).id());
		}
		for (var composition : content.compositions()) {
			var steps = composition.steps().stream()
					.filter(step -> lookIds.containsKey(step.lookId()))
					.map(step -> step.withLook(lookIds.get(step.lookId())))
					.toList();
			compositions.update(compositionIds.get(composition.id()), composition.spec(), steps);
		}
		content.sceneCompositions().forEach((sceneId, uses) -> mapped(sceneIds, sceneId).ifPresent(id ->
				scenes.replaceCompositions(id, uses.stream()
						.filter(use -> compositionIds.containsKey(use.compositionId()))
						.map(use -> new SceneComposition(compositionIds.get(use.compositionId()), use.level(),
								use.speedFactor(), use.restart()))
						.toList())));
		content.sceneKeys().forEach((sceneId, keys) -> mapped(sceneIds, sceneId).ifPresent(id ->
				scenes.replaceKeys(id, keys.stream()
						.filter(key -> compositionIds.containsKey(key.compositionId()))
						.map(key -> new SceneKey(key.slot(), compositionIds.get(key.compositionId()), key.mode(),
								key.fade()))
						.toList())));
		for (var fader : content.faders()) {
			mapped(compositionIds, fader.compositionId()).ifPresent(id ->
					faders.save(projectId, new Fader(fader.slot(), fader.name(), id)));
		}
		return new Written(Map.copyOf(compositionIds), Map.copyOf(lookIds));
	}

	/** Deletes the scenes the content no longer has; their compositions stay. */
	public void removeScenes(ShowContent content, Map<Long, Long> sceneIds) {
		content.removedScenes().forEach(sceneId -> mapped(sceneIds, sceneId).ifPresent(scenes::delete));
	}

	/** Ids of the content → ids in the project. */
	public record Written(Map<Long, Long> compositionIds, Map<Long, Long> lookIds) {
	}

	private static Optional<Long> mapped(Map<Long, Long> ids, long id) {
		return Optional.ofNullable(ids.get(id));
	}
}
