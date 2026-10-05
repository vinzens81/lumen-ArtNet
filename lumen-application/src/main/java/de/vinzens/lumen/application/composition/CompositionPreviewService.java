package de.vinzens.lumen.application.composition;

import de.vinzens.lumen.application.ConflictException;
import de.vinzens.lumen.application.NotFoundException;
import de.vinzens.lumen.application.composition.port.in.PreviewCompositionUseCase;
import de.vinzens.lumen.application.composition.port.out.CompositionRepository;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.output.OutputLayerSource;
import de.vinzens.lumen.application.output.port.in.ControlModeUseCase;
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
 * stage preview (one composition or a whole scene with its compositions) is an output layer between playback and
 * programmer and stops when show mode starts.
 */
public class CompositionPreviewService implements PreviewCompositionUseCase, OutputLayerSource {

	static final int PREVIEW_PRIORITY = 75;
	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);

	private final CompositionRepository compositions;
	private final SceneRepository scenes;
	private final CompositionResolver resolver;
	private final ControlModeUseCase mode;
	private final Clock clock;
	private final StateEvents events;
	private final Instant origin;

	/** resolved compositions by id, dropped whenever compositions or the patch change */
	private final Map<Long, ActiveComposition> resolved = new ConcurrentHashMap<>();
	private volatile Map<Long, Mode> modes = Map.of();
	private volatile StagePreview onStage;

	public CompositionPreviewService(CompositionRepository compositions, SceneRepository scenes,
			CompositionResolver resolver, ControlModeUseCase mode, Clock clock, StateEvents events) {
		this.compositions = Objects.requireNonNull(compositions, "compositions");
		this.scenes = Objects.requireNonNull(scenes, "scenes");
		this.resolver = Objects.requireNonNull(resolver, "resolver");
		this.mode = Objects.requireNonNull(mode, "mode");
		this.clock = Objects.requireNonNull(clock, "clock");
		this.events = Objects.requireNonNull(events, "events");
		this.origin = clock.instant();
		events.subscribe(this::changed);
	}

	private void changed(StateTopic topic) {
		switch (topic) {
			case COMPOSITIONS, PATCH, SCENES -> {
				resolved.clear();
				refreshStage();
			}
			case MODE -> {
				if (mode.mode() == OperatingMode.SHOW) {
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
	public synchronized void showSceneOnStage(long projectId, long sceneId) {
		requireSetupMode();
		var scene = scenes.findById(sceneId).filter(s -> s.projectId() == projectId)
				.orElseThrow(() -> new NotFoundException("Scene " + sceneId + " does not exist in project "
						+ projectId));
		onStage = new SceneOnStage(projectId, sceneId, scene.values(),
				resolver.scenes(projectId).getOrDefault(sceneId, List.of()), clock.instant());
		events.publish(StateTopic.STAGE_PREVIEW);
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
			case SceneOnStage current -> scenes.findById(current.sceneId()).ifPresentOrElse(
					scene -> onStage = new SceneOnStage(current.projectId(), current.sceneId(), scene.values(),
							resolver.scenes(current.projectId()).getOrDefault(current.sceneId(), List.of()),
							current.startedAt()),
					this::stopOnStage);
		}
	}

	private void requireSetupMode() {
		if (mode.mode() == OperatingMode.SHOW) {
			throw new ConflictException("The stage preview is not available in show mode");
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
	public Optional<Long> sceneOnStage() {
		return onStage instanceof SceneOnStage preview ? Optional.of(preview.sceneId()) : Optional.empty();
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
			case SceneOnStage scene -> {
				// like the playback shows the scene once its fade-in is done
				var look = scene.base();
				for (var composition : scene.compositions()) {
					look = CompositionMixer.apply(look, composition, seconds, attributes);
				}
				yield look;
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

	private record SceneOnStage(long projectId, long sceneId, Map<Long, FixtureValues> base,
			List<ActiveComposition> compositions, Instant startedAt) implements StagePreview {
	}
}
