package de.vinzens.lumen.application.playback;

import de.vinzens.lumen.application.ConflictException;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.playback.port.out.PlaybackStateRepository;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.application.scene.port.out.SceneRepository;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.project.Project;
import de.vinzens.lumen.domain.project.ProjectSpec;
import de.vinzens.lumen.domain.scene.Scene;
import de.vinzens.lumen.domain.scene.SceneNumber;
import de.vinzens.lumen.domain.scene.SceneSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaybackServiceTest {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);

	private final TestClock clock = new TestClock();
	private final List<Scene> scenes = new ArrayList<>();
	private final Map<Long, Optional<Long>> saved = new HashMap<>();
	private final List<Optional<Long>> writes = new ArrayList<>();
	private final StateEvents events = new StateEvents();

	@BeforeEach
	void setUp() {
		for (int i = 1; i <= 5; i++) {
			scenes.add(new Scene(i, 1, new SceneSpec(SceneNumber.parse(String.valueOf(i)), "Scene " + i,
					Optional.empty(), Optional.empty()), Map.of((long) i, new FixtureValues(Map.of(DIMMER, 1.0)))));
		}
	}

	@Test
	void savesTheSceneOnStageAfterEveryChange() {
		var service = service();
		assertThat(service.status().resumeSceneId()).isEmpty();

		go(service);
		go(service);
		service.back();
		assertThat(writes).containsExactly(Optional.of(1L), Optional.of(2L), Optional.of(1L));

		service.back();
		assertThat(saved.get(1L)).as("dark before the first scene").isEmpty();
	}

	@Test
	void offersToResumeAfterARestartAndPutsTheSceneOnStageAtOnce() {
		var before = service();
		for (int i = 0; i < 4; i++) {
			go(before);
		}
		assertThat(saved.get(1L)).contains(4L);

		var after = service(); // restart
		var status = after.status();
		assertThat(status.position()).as("starts dark").isEqualTo(-1);
		assertThat(status.resumeSceneId()).contains(4L);
		assertThat(after.layer(clock.instant(), Map.of()).fixtures()).isEmpty();

		status = after.resume();
		assertThat(status.currentSceneId()).contains(4L);
		assertThat(status.fade()).as("no fade").isEmpty();
		assertThat(status.resumeSceneId()).isEmpty();
		assertThat(after.layer(clock.instant(), Map.of()).fixtures().get(4L).get(DIMMER)).contains(1.0);
		assertThat(after.status().nextSceneId()).contains(5L);
	}

	@Test
	void goOrDismissDropsTheOfferWithoutForgettingTheSavedScene() {
		saved.put(1L, Optional.of(3L));
		var service = service();

		service.back();
		assertThat(service.status().resumeSceneId()).as("back before the first scene changes nothing").contains(3L);

		service.dismissResume();
		assertThat(service.status().resumeSceneId()).isEmpty();
		assertThat(writes).isEmpty();
		assertThatThrownBy(service::resume).isInstanceOf(ConflictException.class);

		saved.put(1L, Optional.of(3L));
		var other = service();
		go(other);
		assertThat(other.status().resumeSceneId()).isEmpty();
		assertThat(saved.get(1L)).contains(1L);
	}

	@Test
	void noOfferWhenTheSavedSceneWasDeleted() {
		saved.put(1L, Optional.of(3L));
		scenes.remove(2);

		assertThat(service().status().resumeSceneId()).isEmpty();
	}

	private PlaybackService service() {
		PlaybackStateRepository states = new PlaybackStateRepository() {
			@Override
			public Optional<Long> currentScene(long projectId) {
				return saved.getOrDefault(projectId, Optional.empty());
			}

			@Override
			public void save(long projectId, Optional<Long> sceneId, Instant now) {
				saved.put(projectId, sceneId);
				writes.add(sceneId);
			}
		};
		return new PlaybackService(projects(), sceneRepository(), states, Runnable::run, clock, events);
	}

	private void go(PlaybackService service) {
		service.go();
		clock.advance(Duration.ofSeconds(1)); // beyond the double-GO guard
	}

	private ProjectRepository projects() {
		var project = new Project(1, new ProjectSpec("Show", Optional.empty(), Duration.ZERO, Duration.ZERO), true,
				Instant.EPOCH, Instant.EPOCH);
		return new ProjectRepository() {
			@Override
			public List<Project> findAll() {
				return List.of(project);
			}

			@Override
			public Optional<Project> findById(long id) {
				return Optional.of(project).filter(p -> p.id() == id);
			}

			@Override
			public Project create(ProjectSpec spec, Instant now) {
				throw new UnsupportedOperationException();
			}

			@Override
			public Project update(long id, ProjectSpec spec, Instant now) {
				throw new UnsupportedOperationException();
			}

			@Override
			public void delete(long id) {
				throw new UnsupportedOperationException();
			}

			@Override
			public void activate(long id) {
				throw new UnsupportedOperationException();
			}
		};
	}

	private SceneRepository sceneRepository() {
		return new SceneRepository() {
			@Override
			public List<Scene> findByProject(long projectId) {
				return List.copyOf(scenes);
			}

			@Override
			public Optional<Scene> findById(long id) {
				return scenes.stream().filter(s -> s.id() == id).findFirst();
			}

			@Override
			public Scene create(long projectId, SceneSpec spec, Map<Long, FixtureValues> values) {
				throw new UnsupportedOperationException();
			}

			@Override
			public Scene updateSpec(long id, SceneSpec spec) {
				throw new UnsupportedOperationException();
			}

			@Override
			public Scene replaceValues(long id, Map<Long, FixtureValues> values) {
				throw new UnsupportedOperationException();
			}

			@Override
			public void delete(long id) {
				throw new UnsupportedOperationException();
			}
		};
	}

	static final class TestClock extends Clock {

		private Instant now = Instant.parse("2026-10-02T20:00:00Z");

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}
}
