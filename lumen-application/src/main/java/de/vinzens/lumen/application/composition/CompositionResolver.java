package de.vinzens.lumen.application.composition;

import de.vinzens.lumen.application.composition.port.out.CompositionRepository;
import de.vinzens.lumen.application.look.port.out.LookRepository;
import de.vinzens.lumen.application.move.port.out.FigureRepository;
import de.vinzens.lumen.application.move.port.out.MoveRepository;
import de.vinzens.lumen.application.scene.port.out.SceneRepository;
import de.vinzens.lumen.domain.composition.ActiveComposition;
import de.vinzens.lumen.domain.composition.Composition;
import de.vinzens.lumen.domain.composition.CompositionMixer;
import de.vinzens.lumen.domain.composition.ExtraKeys.PlayableKey;
import de.vinzens.lumen.domain.composition.Fader;
import de.vinzens.lumen.domain.composition.Faders.PlayableFader;
import de.vinzens.lumen.domain.composition.FixtureAttributes;
import de.vinzens.lumen.domain.composition.StepSequence;
import de.vinzens.lumen.domain.look.Look;
import de.vinzens.lumen.domain.move.Figure;
import de.vinzens.lumen.domain.move.Move;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.scene.Scene;
import de.vinzens.lumen.domain.scene.SceneComposition;
import de.vinzens.lumen.domain.scene.SceneLook;
import de.vinzens.lumen.domain.scene.SceneMove;
import de.vinzens.lumen.domain.scene.ScriptMarkers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Turns stored compositions and moves into playable ones: steps with the values of their looks, moves with their
 * figure, and the settings of their use in a scene.
 */
public class CompositionResolver {

	private final CompositionRepository compositions;
	private final LookRepository looks;
	private final SceneRepository scenes;
	private final MoveRepository moves;
	private final FigureRepository figures;

	public CompositionResolver(CompositionRepository compositions, LookRepository looks, SceneRepository scenes,
			MoveRepository moves, FigureRepository figures) {
		this.compositions = Objects.requireNonNull(compositions, "compositions");
		this.looks = Objects.requireNonNull(looks, "looks");
		this.scenes = Objects.requireNonNull(scenes, "scenes");
		this.moves = Objects.requireNonNull(moves, "moves");
		this.figures = Objects.requireNonNull(figures, "figures");
	}

	/** Compositions of every scene of the project, by scene id, in their order within the scene. */
	public Map<Long, List<ActiveComposition>> scenes(long projectId) {
		var project = load(projectId);
		var result = new HashMap<Long, List<ActiveComposition>>();
		scenes.findByProject(projectId).forEach(scene -> result.put(scene.id(), project.compositionsOf(scene)));
		return result;
	}

	/**
	 * The light of the parts after the light markers of every scene, by scene id: one list per marker in the order of
	 * the script. A marker without light chosen is dark.
	 */
	public Map<Long, List<List<ActiveComposition>>> sections(long projectId) {
		var project = load(projectId);
		var result = new HashMap<Long, List<List<ActiveComposition>>>();
		for (var scene : scenes.findByProject(projectId)) {
			var markers = ScriptMarkers.parse(scene.spec().script());
			if (!markers.isEmpty()) {
				result.put(scene.id(), markers.stream()
						.map(marker -> {
							var section = scene.section(marker.name());
							return project.lightOf(section.looks(), section.compositions(), section.moves());
						})
						.toList());
			}
		}
		return result;
	}

	/**
	 * The extra keys of every scene of the project, by scene id. A key whose composition or move is gone is left out.
	 */
	public Map<Long, List<PlayableKey>> keys(long projectId) {
		var project = load(projectId);
		var result = new HashMap<Long, List<PlayableKey>>();
		for (var scene : scenes.findByProject(projectId)) {
			var keys = new ArrayList<PlayableKey>();
			for (var key : scene.keys()) {
				key.compositionId().map(project.compositions()::get).ifPresent(composition ->
						keys.add(new PlayableKey(key.slot(), key.mode(), key.fade(), composition.spec().name(),
								ActiveComposition.of(composition, project.looks()))));
				key.moveId().flatMap(project::move).ifPresent(move ->
						keys.add(new PlayableKey(key.slot(), key.mode(), key.fade(), move.name(), move.active())));
			}
			result.put(scene.id(), List.copyOf(keys));
		}
		return result;
	}

	/** Every move of the project that can play (its figure exists), by move id. */
	public Map<Long, ActiveComposition> moves(long projectId) {
		var project = load(projectId);
		var result = new HashMap<Long, ActiveComposition>();
		project.moves().keySet().forEach(id -> project.move(id).ifPresent(move -> result.put(id, move.active())));
		return result;
	}

	/** A move on its own; empty if its figure is gone. */
	public Optional<ActiveComposition> resolve(Move move) {
		return figures.findById(move.spec().figureId()).map(figure -> ActiveComposition.ofMove(move, figure));
	}

	/** Faders ready to play; a fader whose composition no longer exists is left out. */
	public List<PlayableFader> faders(long projectId, List<Fader> faders) {
		var project = load(projectId);
		var result = new ArrayList<PlayableFader>();
		for (var fader : faders) {
			var composition = project.compositions().get(fader.compositionId());
			if (composition != null) {
				result.add(new PlayableFader(fader.slot(), fader.name().orElse(composition.spec().name()),
						ActiveComposition.of(composition, project.looks())));
			}
		}
		return result;
	}

	/** Every composition of the project on its own, at full level and speed, by id. */
	public Map<Long, ActiveComposition> compositions(long projectId) {
		var project = load(projectId);
		var result = new HashMap<Long, ActiveComposition>();
		project.compositions().forEach((id, composition) ->
				result.put(id, ActiveComposition.of(composition, project.looks())));
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
		var moveById = new HashMap<Long, Move>();
		moves.findByProject(projectId).forEach(move -> moveById.put(move.id(), move));
		var figureById = new HashMap<Long, Figure>();
		figures.findByProject(projectId).forEach(figure -> figureById.put(figure.id(), figure));
		return new Project(Map.copyOf(lookContents), Map.copyOf(byId), Map.copyOf(moveById), Map.copyOf(figureById));
	}

	/** A move ready to play, with its name. */
	private record NamedMove(String name, ActiveComposition active) {
	}

	/** Looks, compositions, moves and figures of a project, read once per resolution. */
	private record Project(Map<Long, Look> looks, Map<Long, Composition> compositions, Map<Long, Move> moves,
			Map<Long, Figure> figures) {

		/** The start of a scene: its looks, then its compositions, then its moves; missing ones are left out. */
		List<ActiveComposition> compositionsOf(Scene scene) {
			return lightOf(scene.looks(), scene.compositions(), scene.moves());
		}

		List<ActiveComposition> lightOf(List<SceneLook> sceneLooks, List<SceneComposition> uses,
				List<SceneMove> sceneMoves) {
			var result = new ArrayList<ActiveComposition>();
			sceneLooks.forEach(use -> Optional.ofNullable(looks.get(use.lookId())).ifPresent(look ->
					result.add(ActiveComposition.ofLook(use.lookId(), look, use.level()))));
			uses.forEach(use -> Optional.ofNullable(compositions.get(use.compositionId())).ifPresent(composition ->
					result.add(ActiveComposition.of(composition, looks, use.level(), use.speedFactor(),
							use.restart()).withFades(use.fadeIn(), use.fadeOut()))));
			sceneMoves.forEach(use -> move(use.moveId()).ifPresent(move -> result.add(move.active())));
			return List.copyOf(result);
		}

		/** A move whose figure exists. */
		Optional<NamedMove> move(long moveId) {
			var move = moves.get(moveId);
			var figure = move == null ? null : figures.get(move.spec().figureId());
			return figure == null ? Optional.empty()
					: Optional.of(new NamedMove(move.spec().name(), ActiveComposition.ofMove(move, figure)));
		}
	}
}
