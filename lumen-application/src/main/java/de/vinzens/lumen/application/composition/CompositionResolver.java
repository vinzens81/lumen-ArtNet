package de.vinzens.lumen.application.composition;

import de.vinzens.lumen.application.composition.port.out.CompositionRepository;
import de.vinzens.lumen.application.look.port.out.LookRepository;
import de.vinzens.lumen.application.scene.port.out.SceneRepository;
import de.vinzens.lumen.domain.composition.ActiveComposition;
import de.vinzens.lumen.domain.composition.Composition;
import de.vinzens.lumen.domain.composition.CompositionMixer;
import de.vinzens.lumen.domain.composition.ExtraKeys.PlayableKey;
import de.vinzens.lumen.domain.composition.Fader;
import de.vinzens.lumen.domain.composition.Faders.PlayableFader;
import de.vinzens.lumen.domain.composition.Faders.SceneLook;
import de.vinzens.lumen.domain.composition.FixtureAttributes;
import de.vinzens.lumen.domain.composition.StepSequence;
import de.vinzens.lumen.domain.look.Look;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.scene.Scene;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Turns stored compositions into playable ones: their steps with the values of their looks, and the settings of
 * their use in a scene.
 */
public class CompositionResolver {

	private final CompositionRepository compositions;
	private final LookRepository looks;
	private final SceneRepository scenes;

	public CompositionResolver(CompositionRepository compositions, LookRepository looks, SceneRepository scenes) {
		this.compositions = Objects.requireNonNull(compositions, "compositions");
		this.looks = Objects.requireNonNull(looks, "looks");
		this.scenes = Objects.requireNonNull(scenes, "scenes");
	}

	/** Compositions of every scene of the project, by scene id, in their order within the scene. */
	public Map<Long, List<ActiveComposition>> scenes(long projectId) {
		var project = load(projectId);
		var result = new HashMap<Long, List<ActiveComposition>>();
		scenes.findByProject(projectId).forEach(scene -> result.put(scene.id(), project.compositionsOf(scene)));
		return result;
	}

	/** The extra keys of every scene of the project, by scene id. A key whose composition is gone is left out. */
	public Map<Long, List<PlayableKey>> keys(long projectId) {
		var project = load(projectId);
		var result = new HashMap<Long, List<PlayableKey>>();
		for (var scene : scenes.findByProject(projectId)) {
			var keys = new ArrayList<PlayableKey>();
			for (var key : scene.keys()) {
				var composition = project.compositions().get(key.compositionId());
				if (composition != null) {
					keys.add(new PlayableKey(key.slot(), key.mode(), key.fade(), composition.spec().name(),
							ActiveComposition.of(composition, project.looks())));
				}
			}
			result.put(scene.id(), List.copyOf(keys));
		}
		return result;
	}

	/** Faders ready to play; a fader whose scene no longer exists is left out. */
	public List<PlayableFader> faders(List<Fader> faders) {
		var result = new ArrayList<PlayableFader>();
		for (var fader : faders) {
			scenes.findById(fader.sceneId()).ifPresent(scene -> result.add(new PlayableFader(fader.slot(),
					fader.name().orElse(scene.spec().name()),
					new SceneLook(scene.id(), load(scene.projectId()).compositionsOf(scene)))));
		}
		return result;
	}

	/** A composition on its own, at full level and speed. */
	public ActiveComposition resolve(Composition composition) {
		return ActiveComposition.of(composition, load(composition.projectId()).looks());
	}

	/**
	 * The scene as it shows once every composition has faded into its first step – e.g. to load it into the
	 * programmer.
	 */
	public Map<Long, FixtureValues> sceneAtStart(long projectId, long sceneId) {
		var running = scenes(projectId).getOrDefault(sceneId, List.of());
		Map<Long, FixtureValues> result = Map.of();
		for (var composition : running) {
			var firstFade = composition.pattern() instanceof StepSequence sequence && !sequence.steps().isEmpty()
					? sequence.steps().getFirst().fadeSeconds()
					: 0;
			result = CompositionMixer.apply(result, composition, firstFade / composition.speedFactor(),
					FixtureAttributes.ALL);
		}
		return result;
	}

	private Project load(long projectId) {
		var lookContents = new HashMap<Long, Look>();
		looks.findByProject(projectId).forEach(look -> lookContents.put(look.id(), look.look()));
		var byId = new HashMap<Long, Composition>();
		compositions.findByProject(projectId).forEach(composition -> byId.put(composition.id(), composition));
		return new Project(Map.copyOf(lookContents), Map.copyOf(byId));
	}

	/** Looks and compositions of a project, read once per resolution. */
	private record Project(Map<Long, Look> looks, Map<Long, Composition> compositions) {

		/** The compositions of a scene with the settings of their use; missing ones are left out. */
		List<ActiveComposition> compositionsOf(Scene scene) {
			return scene.compositions().stream()
					.flatMap(use -> Optional.ofNullable(compositions.get(use.compositionId())).stream()
							.map(composition -> ActiveComposition.of(composition, looks, use.level(),
									use.speedFactor(), use.restart())))
					.toList();
		}
	}
}
