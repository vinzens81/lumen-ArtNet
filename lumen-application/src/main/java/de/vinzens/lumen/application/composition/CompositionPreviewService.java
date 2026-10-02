package de.vinzens.lumen.application.composition;

import de.vinzens.lumen.application.ConflictException;
import de.vinzens.lumen.application.NotFoundException;
import de.vinzens.lumen.application.composition.port.in.PreviewCompositionUseCase;
import de.vinzens.lumen.application.composition.port.out.CompositionRepository;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.output.OutputLayerSource;
import de.vinzens.lumen.application.output.port.in.ControlModeUseCase;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs compositions for the editor. The on-screen preview runs on its own clock and never reaches the stage; the
 * stage preview is an output layer between playback and programmer and stops when show mode starts.
 */
public class CompositionPreviewService implements PreviewCompositionUseCase, OutputLayerSource {

	static final int PREVIEW_PRIORITY = 75;
	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);

	private final CompositionRepository compositions;
	private final CompositionResolver resolver;
	private final ControlModeUseCase mode;
	private final Clock clock;
	private final StateEvents events;
	private final Instant origin;

	/** resolved compositions by id, dropped whenever compositions or the patch change */
	private final Map<Long, ActiveComposition> resolved = new ConcurrentHashMap<>();
	private volatile Map<Long, Mode> modes = Map.of();
	private volatile StagePreview onStage;

	public CompositionPreviewService(CompositionRepository compositions, CompositionResolver resolver,
			ControlModeUseCase mode, Clock clock, StateEvents events) {
		this.compositions = Objects.requireNonNull(compositions, "compositions");
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
				var current = onStage;
				if (current != null) {
					var composition = compositions.findById(current.composition().id());
					if (composition.isEmpty()) {
						stopOnStage();
					} else {
						onStage = new StagePreview(current.projectId(), resolver.resolve(composition.get()),
								current.startedAt());
					}
				}
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
		if (mode.mode() == OperatingMode.SHOW) {
			throw new ConflictException("The stage preview is not available in show mode");
		}
		var composition = compositions.findById(compositionId).filter(c -> c.projectId() == projectId)
				.orElseThrow(() -> new NotFoundException("Composition " + compositionId + " does not exist in project "
						+ projectId));
		onStage = new StagePreview(projectId, resolver.resolve(composition), clock.instant());
		events.publish(StateTopic.COMPOSITIONS);
	}

	@Override
	public synchronized void stopOnStage() {
		if (onStage != null) {
			onStage = null;
			events.publish(StateTopic.COMPOSITIONS);
		}
	}

	@Override
	public Optional<Long> compositionOnStage() {
		return Optional.ofNullable(onStage).map(preview -> preview.composition().id());
	}

	@Override
	public LayerOutput layer(Instant now, Map<Long, Mode> currentModes) {
		modes = currentModes;
		var preview = onStage;
		if (preview == null) {
			return new LayerOutput(PREVIEW_PRIORITY, Map.of());
		}
		var seconds = Duration.between(preview.startedAt(), now).toNanos() / 1e9;
		return new LayerOutput(PREVIEW_PRIORITY, look(preview.composition(), seconds,
				FixtureAttributes.of(currentModes)));
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

	private record StagePreview(long projectId, ActiveComposition composition, Instant startedAt) {
	}
}
