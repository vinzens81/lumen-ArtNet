package de.vinzens.lumen.application.composition;

import de.vinzens.lumen.application.ConflictException;
import de.vinzens.lumen.application.NotFoundException;
import de.vinzens.lumen.application.composition.port.in.PreviewCompositionUseCase;
import de.vinzens.lumen.application.composition.port.out.CompositionRepository;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.move.port.out.MoveRepository;
import de.vinzens.lumen.application.output.OutputLayerSource;
import de.vinzens.lumen.application.output.port.in.ControlModeUseCase;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.application.scene.port.out.SceneRepository;
import de.vinzens.lumen.domain.composition.ActiveComposition;
import de.vinzens.lumen.domain.composition.CompositionMixer;
import de.vinzens.lumen.domain.composition.FixtureAttributes;
import de.vinzens.lumen.domain.composition.IntensityMode;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.library.Mode;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.output.LayerOutput;
import de.vinzens.lumen.domain.output.OperatingMode;
import de.vinzens.lumen.domain.playback.FadeBehaviors;
import de.vinzens.lumen.domain.playback.Playback;
import de.vinzens.lumen.domain.playback.Playback.FadeStatus;
import de.vinzens.lumen.domain.scene.Scene;
import de.vinzens.lumen.domain.scene.ScriptMarkers;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs compositions for the editor. The on-screen preview runs on its own clock and never reaches the stage; the
 * stage preview (one composition, one move, a scene or one part of it after a light marker, or a fade from the light
 * on stage into one of them) is an output layer between playback and programmer and stops when show mode starts.
 */
public class CompositionPreviewService implements PreviewCompositionUseCase, OutputLayerSource {

	static final int PREVIEW_PRIORITY = 75;
	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);

	private final CompositionRepository compositions;
	private final MoveRepository moves;
	private final SceneRepository scenes;
	private final ProjectRepository projects;
	private final CompositionResolver resolver;
	private final ControlModeUseCase mode;
	private final Clock clock;
	private final StateEvents events;
	private final Instant origin;

	/** resolved compositions by id, dropped whenever compositions or the patch change */
	private final Map<Long, ActiveComposition> resolved = new ConcurrentHashMap<>();
	private volatile Map<Long, Mode> modes = Map.of();
	private volatile StagePreview onStage;

	public CompositionPreviewService(CompositionRepository compositions, MoveRepository moves, SceneRepository scenes,
			ProjectRepository projects, CompositionResolver resolver, ControlModeUseCase mode, Clock clock,
			StateEvents events) {
		this.compositions = Objects.requireNonNull(compositions, "compositions");
		this.moves = Objects.requireNonNull(moves, "moves");
		this.scenes = Objects.requireNonNull(scenes, "scenes");
		this.projects = Objects.requireNonNull(projects, "projects");
		this.resolver = Objects.requireNonNull(resolver, "resolver");
		this.mode = Objects.requireNonNull(mode, "mode");
		this.clock = Objects.requireNonNull(clock, "clock");
		this.events = Objects.requireNonNull(events, "events");
		this.origin = clock.instant();
		events.subscribe(this::changed);
	}

	private void changed(StateTopic topic) {
		switch (topic) {
			case COMPOSITIONS, LOOKS, PATCH, SCENES, MOVES -> {
				resolved.clear();
				refreshStage();
			}
			case MODE -> {
				if (mode.mode() != OperatingMode.SETUP) {
					stopOnStage();
				}
			}
			case PROJECTS -> resolved.clear();
			default -> {
			}
		}
	}

	@Override
	public Map<Long, FixtureValues> previewValues(long compositionId) {
		var composition = resolved.computeIfAbsent(compositionId, id -> compositions.findById(id)
				.map(resolver::resolve)
				.orElseThrow(() -> new NotFoundException("Composition " + id + " does not exist")));
		var seconds = Duration.between(origin, clock.instant()).toNanos() / 1e9;
		var look = look(composition, seconds, FixtureAttributes.of(modes));
		var result = new HashMap<Long, FixtureValues>();
		composition.fixtureIds().forEach(id -> result.put(id, look.getOrDefault(id, FixtureValues.EMPTY)));
		return result;
	}

	@Override
	public synchronized void showOnStage(long projectId, long compositionId) {
		requireSetupMode();
		var composition = compositions.findById(compositionId).filter(c -> c.projectId() == projectId)
				.orElseThrow(() -> new NotFoundException("Composition " + compositionId + " does not exist in project "
						+ projectId));
		onStage = new CompositionOnStage(compositionId, resolver.resolve(composition), clock.instant());
		events.publish(StateTopic.STAGE_PREVIEW);
	}

	@Override
	public synchronized void showMoveOnStage(long projectId, long moveId) {
		requireSetupMode();
		var move = moves.findById(moveId).filter(m -> m.projectId() == projectId)
				.orElseThrow(() -> new NotFoundException("Move " + moveId + " does not exist in project " + projectId));
		var active = resolver.resolve(move)
				.orElseThrow(() -> new ConflictException("The figure of the move does not exist"));
		onStage = new MoveOnStage(moveId, active, clock.instant());
		events.publish(StateTopic.STAGE_PREVIEW);
	}

	@Override
	public synchronized void showSceneOnStage(long projectId, long sceneId, Optional<String> marker) {
		requireSetupMode();
		var scene = sceneOf(projectId, sceneId);
		var part = partOf(scene, marker);
		onStage = new SceneOnStage(projectId, sceneId, marker, lightOf(projectId, sceneId, part), clock.instant());
		events.publish(StateTopic.STAGE_PREVIEW);
	}

	@Override
	public synchronized void fadeIn(long projectId, long sceneId, Optional<String> marker) {
		requireSetupMode();
		var project = projects.findById(projectId)
				.orElseThrow(() -> new NotFoundException("Project " + projectId + " does not exist"));
		var part = partOf(sceneOf(projectId, sceneId), marker);
		var now = clock.instant();
		var behaviors = FadeBehaviors.of(modes);
		// the fade starts from the light on stage, whatever it is: a running fade goes on from where it stands
		var playback = onStage instanceof FadeOnStage fade && fade.projectId() == projectId ? fade.playback() : null;
		if (playback == null) {
			playback = new Playback(scenes.findByProject(projectId), resolver.scenes(projectId),
					project.spec().fadeIn(), project.spec().fadeOut());
			playback.useSections(resolver.sections(projectId));
			playback.useFixtures(FixtureAttributes.of(modes));
			playback.useFadeCurve(project.spec().fadeCurve());
			playback.useMoveInDark(project.spec().dimOut(), project.spec().travel());
			playback.restoreLight(lightOnStage(), onStage == null ? now : onStage.startedAt());
		}
		synchronized (playback) {
			playback.goTo(sceneId, part, now, behaviors);
		}
		onStage = new FadeOnStage(projectId, sceneId, marker, playback, now);
		events.publish(StateTopic.STAGE_PREVIEW);
	}

	/** The compositions of the stage preview, as they run since its start. */
	private List<ActiveComposition> lightOnStage() {
		return switch (onStage) {
			case CompositionOnStage composition -> List.of(composition.composition());
			case MoveOnStage move -> List.of(move.move());
			case SceneOnStage scene -> scene.compositions();
			case FadeOnStage fade -> {
				synchronized (fade.playback()) {
					yield fade.playback().runningCompositions();
				}
			}
			case null -> List.of();
		};
	}

	private Scene sceneOf(long projectId, long sceneId) {
		return scenes.findById(sceneId).filter(s -> s.projectId() == projectId)
				.orElseThrow(() -> new NotFoundException("Scene " + sceneId + " does not exist in project "
						+ projectId));
	}

	/**
	 * The part of the scene a marker starts: 0 for its start (no marker), n after the n-th marker of the saved script
	 * (the first one of that name).
	 */
	private static int partOf(Scene scene, Optional<String> marker) {
		return partIn(scene, marker).orElseThrow(() -> new NotFoundException("Scene " + scene.spec().number()
				+ " has no light marker \"" + marker.orElseThrow() + "\" – save the script first"));
	}

	/** {@link #partOf}, empty if the saved script has no such marker. */
	private static Optional<Integer> partIn(Scene scene, Optional<String> marker) {
		if (marker.isEmpty()) {
			return Optional.of(0);
		}
		var names = ScriptMarkers.parse(scene.spec().script()).stream().map(ScriptMarkers.Marker::name).toList();
		var index = names.indexOf(marker.get());
		return index < 0 ? Optional.empty() : Optional.of(index + 1);
	}

	/** The compositions of a part of the scene: 0 = its start, n = after the n-th light marker. */
	private List<ActiveComposition> lightOf(long projectId, long sceneId, int part) {
		if (part == 0) {
			return resolver.scenes(projectId).getOrDefault(sceneId, List.of());
		}
		var parts = resolver.sections(projectId).getOrDefault(sceneId, List.of());
		return part <= parts.size() ? parts.get(part - 1) : List.of();
	}

	/** Takes over changes of what is on stage (edited scene or composition); its time keeps running. */
	private synchronized void refreshStage() {
		switch (onStage) {
			case null -> {
			}
			case CompositionOnStage current -> compositions.findById(current.compositionId()).ifPresentOrElse(
					c -> onStage = new CompositionOnStage(current.compositionId(), resolver.resolve(c),
							current.startedAt()),
					this::stopOnStage);
			case MoveOnStage current -> moves.findById(current.moveId()).flatMap(resolver::resolve).ifPresentOrElse(
					active -> onStage = new MoveOnStage(current.moveId(), active, current.startedAt()),
					this::stopOnStage);
			case SceneOnStage current -> {
				// a part whose marker left the script is no longer there to show
				scenes.findById(current.sceneId()).flatMap(scene -> partIn(scene, current.marker())).ifPresentOrElse(
						p -> onStage = new SceneOnStage(current.projectId(), current.sceneId(), current.marker(),
								lightOf(current.projectId(), current.sceneId(), p), current.startedAt()),
						this::stopOnStage);
			}
			case FadeOnStage current -> {
				var target = scenes.findById(current.sceneId());
				var project = projects.findById(current.projectId());
				if (target.isEmpty() || project.isEmpty()) {
					stopOnStage();
					return;
				}
				synchronized (current.playback()) {
					current.playback().useSections(resolver.sections(current.projectId()));
					current.playback().reload(scenes.findByProject(current.projectId()),
							resolver.scenes(current.projectId()),
							project.get().spec().fadeIn(), project.get().spec().fadeOut(), clock.instant());
					current.playback().useFadeCurve(project.get().spec().fadeCurve());
					current.playback().useMoveInDark(project.get().spec().dimOut(), project.get().spec().travel());
				}
			}
		}
	}

	private void requireSetupMode() {
		if (mode.mode() != OperatingMode.SETUP) {
			throw new ConflictException("The stage preview is only available in setup mode");
		}
	}

	@Override
	public synchronized void stopOnStage() {
		if (onStage != null) {
			onStage = null;
			events.publish(StateTopic.STAGE_PREVIEW);
		}
	}

	@Override
	public Optional<Long> compositionOnStage() {
		return onStage instanceof CompositionOnStage preview ? Optional.of(preview.compositionId()) : Optional.empty();
	}

	@Override
	public Optional<Long> moveOnStage() {
		return onStage instanceof MoveOnStage preview ? Optional.of(preview.moveId()) : Optional.empty();
	}

	@Override
	public Optional<Long> sceneOnStage() {
		return switch (onStage) {
			case SceneOnStage preview -> Optional.of(preview.sceneId());
			case FadeOnStage preview -> Optional.of(preview.sceneId());
			case null, default -> Optional.empty();
		};
	}

	@Override
	public Optional<String> markerOnStage() {
		return switch (onStage) {
			case SceneOnStage preview -> preview.marker();
			case FadeOnStage preview -> preview.marker();
			case null, default -> Optional.empty();
		};
	}

	@Override
	public Optional<FadeStatus> fadeOnStage() {
		if (!(onStage instanceof FadeOnStage preview)) {
			return Optional.empty();
		}
		synchronized (preview.playback()) {
			var now = clock.instant();
			preview.playback().tick(now, FadeBehaviors.of(modes));
			return preview.playback().fade(now);
		}
	}

	@Override
	public LayerOutput layer(Instant now, Map<Long, Mode> currentModes) {
		modes = currentModes;
		var preview = onStage;
		if (preview == null) {
			return new LayerOutput(PREVIEW_PRIORITY, Map.of());
		}
		var seconds = Duration.between(preview.startedAt(), now).toNanos() / 1e9;
		var attributes = FixtureAttributes.of(currentModes);
		return new LayerOutput(PREVIEW_PRIORITY, switch (preview) {
			case CompositionOnStage composition -> look(composition.composition(), seconds, attributes);
			case MoveOnStage move -> CompositionMixer.apply(Map.of(), move.move(), seconds, attributes);
			case SceneOnStage scene -> {
				// like the playback shows the scene once its fade-in is done
				Map<Long, FixtureValues> look = Map.of();
				for (var composition : scene.compositions()) {
					look = CompositionMixer.apply(look, composition, seconds, attributes);
				}
				yield look;
			}
			case FadeOnStage fade -> {
				synchronized (fade.playback()) {
					var behaviors = FadeBehaviors.of(currentModes);
					fade.playback().useFixtures(attributes);
					fade.playback().tick(now, behaviors);
					yield fade.playback().output(now, behaviors);
				}
			}
		});
	}

	/** The composition over an empty stage; relative compositions (flicker) over full intensity. */
	private static Map<Long, FixtureValues> look(ActiveComposition composition, double seconds,
			FixtureAttributes attributes) {
		var base = new HashMap<Long, FixtureValues>();
		if (composition.pattern().intensityMode() == IntensityMode.RELATIVE) {
			composition.fixtureIds().stream()
					.filter(id -> attributes.has(id, DIMMER))
					.forEach(id -> base.put(id, new FixtureValues(Map.of(DIMMER, 1.0))));
		}
		return CompositionMixer.apply(base, composition, seconds, attributes);
	}

	/** What runs on stage for the editor, since {@code startedAt}. */
	private sealed interface StagePreview {

		Instant startedAt();
	}

	private record CompositionOnStage(long compositionId, ActiveComposition composition, Instant startedAt)
			implements StagePreview {
	}

	private record MoveOnStage(long moveId, ActiveComposition move, Instant startedAt) implements StagePreview {
	}

	/** @param marker the light marker of the part shown, empty for the start of the scene */
	private record SceneOnStage(long projectId, long sceneId, Optional<String> marker,
			List<ActiveComposition> compositions, Instant startedAt) implements StagePreview {
	}

	/**
	 * A fade from the light that was on stage into a scene, with the times of the GO into it – or into a part after a
	 * light marker, with the times of passing the marker; it stays on stage afterwards. Runs on its own
	 * {@link Playback} with the whole cue list, which callers lock.
	 *
	 * @param marker the light marker of the part faded into, empty for the start of the scene
	 */
	private record FadeOnStage(long projectId, long sceneId, Optional<String> marker, Playback playback,
			Instant startedAt) implements StagePreview {
	}
}
