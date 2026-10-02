package de.vinzens.lumen.application.playback;

import de.vinzens.lumen.application.ConflictException;
import de.vinzens.lumen.application.NotFoundException;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.output.OutputLayerSource;
import de.vinzens.lumen.application.playback.port.in.ControlPlaybackUseCase;
import de.vinzens.lumen.application.playback.port.out.PlaybackStateRepository;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.application.scene.port.out.SceneRepository;
import de.vinzens.lumen.domain.library.Mode;
import de.vinzens.lumen.domain.output.LayerOutput;
import de.vinzens.lumen.domain.playback.FadeBehaviors;
import de.vinzens.lumen.domain.playback.Playback;
import de.vinzens.lumen.domain.project.Project;
import de.vinzens.lumen.domain.scene.Scene;

import java.lang.System.Logger.Level;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Runs the {@link Playback} of the active project as an output layer below the programmer. The scene list is
 * reloaded whenever scenes, the patch or the active project change.
 *
 * <p>The scene on stage is saved after every command. After a restart the playback starts dark and offers to
 * resume at the saved scene; saving runs on the given executor so the render loop never waits for the database.
 */
public class PlaybackService implements ControlPlaybackUseCase, OutputLayerSource {

	static final int PLAYBACK_PRIORITY = 50;
	/** A second GO within this time is treated as an accidental double press. */
	static final Duration DOUBLE_GO_GUARD = Duration.ofMillis(200);

	private static final System.Logger LOG = System.getLogger(PlaybackService.class.getName());

	private final ProjectRepository projects;
	private final SceneRepository scenes;
	private final PlaybackStateRepository states;
	private final Executor stateWriter;
	private final Clock clock;
	private final StateEvents events;

	private Long projectId;
	private Playback playback;
	private FadeBehaviors behaviors = FadeBehaviors.DEFAULTS;
	private Instant lastGo = Instant.MIN;
	/** The scene as last saved for the active project. */
	private Optional<Long> savedSceneId = Optional.empty();
	/** Scene the show can be resumed at after a restart, until the operator resumes, dismisses or plays on. */
	private Optional<Long> resumeSceneId = Optional.empty();

	/**
	 * @param stateWriter runs the saving of the playback state, e.g. a single background thread
	 */
	public PlaybackService(ProjectRepository projects, SceneRepository scenes, PlaybackStateRepository states,
			Executor stateWriter, Clock clock, StateEvents events) {
		this.projects = Objects.requireNonNull(projects, "projects");
		this.scenes = Objects.requireNonNull(scenes, "scenes");
		this.states = Objects.requireNonNull(states, "states");
		this.stateWriter = Objects.requireNonNull(stateWriter, "stateWriter");
		this.clock = Objects.requireNonNull(clock, "clock");
		this.events = Objects.requireNonNull(events, "events");
		events.subscribe(topic -> {
			if (topic == StateTopic.SCENES || topic == StateTopic.PROJECTS) {
				reload();
			}
		});
		reload();
	}

	/**
	 * Loads the cue list of the active project; a different project starts from the beginning and offers to resume
	 * where it was last played.
	 */
	public synchronized void reload() {
		var active = projects.findAll().stream().filter(Project::active).findFirst();
		if (active.isEmpty()) {
			projectId = null;
			playback = null;
			savedSceneId = Optional.empty();
			resumeSceneId = Optional.empty();
		} else {
			var project = active.get();
			var cueList = scenes.findByProject(project.id());
			var fadeIn = project.spec().fadeIn();
			var fadeOut = project.spec().fadeOut();
			if (playback == null || !Objects.equals(projectId, project.id())) {
				projectId = project.id();
				playback = new Playback(cueList, fadeIn, fadeOut);
				savedSceneId = states.currentScene(project.id());
				resumeSceneId = savedSceneId;
			} else {
				playback.reload(cueList, Map.of(), fadeIn, fadeOut, clock.instant());
			}
			resumeSceneId = resumeSceneId.filter(id -> cueList.stream().anyMatch(scene -> scene.id() == id));
		}
		events.publish(StateTopic.PLAYBACK);
	}

	@Override
	public synchronized LayerOutput layer(Instant now, Map<Long, Mode> modes) {
		if (playback == null) {
			return new LayerOutput(PLAYBACK_PRIORITY, Map.of());
		}
		behaviors = FadeBehaviors.of(modes);
		if (playback.tick(now, behaviors)) {
			saveState(now);
			events.publish(StateTopic.PLAYBACK);
		}
		return new LayerOutput(PLAYBACK_PRIORITY, playback.output(now, behaviors));
	}

	@Override
	public synchronized PlaybackStatus status() {
		return status(clock.instant());
	}

	@Override
	public synchronized PlaybackStatus go() {
		var now = clock.instant();
		if (Duration.between(lastGo, now).compareTo(DOUBLE_GO_GUARD) < 0) {
			return status(now);
		}
		lastGo = now;
		return requirePlayback().go(now, behaviors) ? played(now) : status(now);
	}

	@Override
	public synchronized PlaybackStatus back() {
		var now = clock.instant();
		return requirePlayback().back(now, behaviors) ? played(now) : status(now);
	}

	@Override
	public synchronized PlaybackStatus resume() {
		var current = requirePlayback();
		var sceneId = resumeSceneId.orElseThrow(() -> new ConflictException("There is no show to resume"));
		var now = clock.instant();
		current.restore(sceneId, now);
		return played(now);
	}

	@Override
	public synchronized PlaybackStatus dismissResume() {
		resumeSceneId = Optional.empty();
		return changed(clock.instant());
	}

	@Override
	public synchronized PlaybackStatus togglePause() {
		var now = clock.instant();
		requirePlayback().togglePause(now);
		return changed(now);
	}

	@Override
	public synchronized PlaybackStatus finish() {
		var now = clock.instant();
		requirePlayback().finish(now);
		return changed(now);
	}

	@Override
	public synchronized PlaybackStatus setNext(long sceneId) {
		var current = requirePlayback();
		if (current.scenes().stream().noneMatch(scene -> scene.id() == sceneId)) {
			throw new NotFoundException("Scene " + sceneId + " is not in the cue list of the active project");
		}
		current.setNext(sceneId);
		return changed(clock.instant());
	}

	/** After the position changed: the resume offer is gone and the new position is saved. */
	private PlaybackStatus played(Instant now) {
		resumeSceneId = Optional.empty();
		saveState(now);
		return changed(now);
	}

	private void saveState(Instant now) {
		var sceneId = playback.currentScene().map(Scene::id);
		if (sceneId.equals(savedSceneId)) {
			return;
		}
		savedSceneId = sceneId;
		var project = projectId;
		stateWriter.execute(() -> {
			try {
				states.save(project, sceneId, now);
			} catch (RuntimeException e) {
				LOG.log(Level.WARNING, "Saving the playback state failed", e);
			}
		});
	}

	private PlaybackStatus changed(Instant now) {
		events.publish(StateTopic.PLAYBACK);
		return status(now);
	}

	private Playback requirePlayback() {
		if (playback == null) {
			throw new ConflictException("There is no active project to play");
		}
		return playback;
	}

	private PlaybackStatus status(Instant now) {
		if (playback == null) {
			return PlaybackStatus.UNAVAILABLE;
		}
		var fade = playback.fade(now).map(f -> new Fade(f.fromSceneId(), f.elapsed(), f.fadeIn(), f.fadeOut()));
		return new PlaybackStatus(true, playback.position(), playback.sceneCount(),
				playback.currentScene().map(Scene::id), playback.nextScene().map(Scene::id), playback.nextIsEnd(),
				fade, playback.isPaused(), playback.autoFollowRemaining(now),
				resumeSceneId.filter(id -> playback.position() == Playback.READY));
	}
}
