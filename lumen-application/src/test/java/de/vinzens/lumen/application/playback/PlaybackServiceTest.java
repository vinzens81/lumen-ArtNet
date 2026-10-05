package de.vinzens.lumen.application.playback;

import de.vinzens.lumen.application.ConflictException;
import de.vinzens.lumen.application.FixedCompositions;
import de.vinzens.lumen.application.composition.CompositionResolver;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.playback.port.out.PlaybackStateRepository;
import de.vinzens.lumen.application.project.port.out.GroupRepository;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.application.scene.port.out.SceneRepository;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.composition.Composition;
import de.vinzens.lumen.domain.composition.CompositionSpec;
import de.vinzens.lumen.domain.composition.ExtraKey;
import de.vinzens.lumen.domain.composition.KeyMode;
import de.vinzens.lumen.domain.composition.Target;
import de.vinzens.lumen.domain.composition.Wave;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelDefinition;
import de.vinzens.lumen.domain.library.Mode;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.project.Group;
import de.vinzens.lumen.domain.project.GroupSpec;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaybackServiceTest {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);

	private final TestClock clock = new TestClock();
	private final List<Scene> scenes = new ArrayList<>();
	private final Map<Long, Optional<Long>> saved = new HashMap<>();
	private final List<Optional<Long>> writes = new ArrayList<>();
	private final StateEvents events = new StateEvents();
	private FixedCompositions compositions = FixedCompositions.NONE;
	/** Runs inside every scene query, e.g. to make the database slow. */
	private Runnable onSceneQuery = () -> {
	};

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

	@Test
	void extraKeysWorkOnlyInTheirScenesAndGoOffAfterwards() {
		// key 1: full intensity on fixture 9, usable in scenes 1 and 2
		var storm = new Composition(50, 1, Optional.empty(), Optional.empty(), 0, CompositionSpec.of("Storm",
				new Wave(Wave.Shape.SQUARE, 3600, 1, 1, 0), new Target.Fixtures(List.of(9L))));
		compositions = new FixedCompositions(Map.of(),
				List.of(new ExtraKey(1, KeyMode.TOGGLE, Duration.ZERO, storm)), Map.of(1L, Set.of(1), 2L, Set.of(1)));
		var service = service();

		service.pressKey(1);
		assertThat(keyDimmer(service)).as("no scene on stage yet").isZero();

		go(service);
		var status = service.pressKey(1);
		assertThat(status.keys()).singleElement().satisfies(key -> {
			assertThat(key.on()).isTrue();
			assertThat(key.available()).isTrue();
			assertThat(key.name()).isEqualTo("Storm");
		});
		assertThat(keyDimmer(service)).isEqualTo(1.0);

		go(service); // scene 2: still usable
		assertThat(keyDimmer(service)).isEqualTo(1.0);
		go(service); // scene 3: not enabled – off
		assertThat(keyDimmer(service)).isZero();
		assertThat(service.status().keys().getFirst().available()).isFalse();
	}

	private double keyDimmer(PlaybackService service) {
		var dimmer = new Mode("1ch", List.of(new ChannelDefinition(ChannelFunction.DIMMER, Optional.empty(), 8, 0, false,
				List.of(), Optional.empty())));
		return service.layer(clock.instant(), Map.of(9L, dimmer)).fixtures().getOrDefault(9L, FixtureValues.EMPTY)
				.get(DIMMER).orElse(0.0);
	}

	@Test
	void scriptLineFollowsTheSceneOnStageAndStartsOverAtEverySceneChange() {
		var service = service();
		go(service);
		assertThat(service.setScriptLine(1, 3).scriptLine()).isEqualTo(3);

		go(service);
		assertThat(service.status().scriptLine()).as("scene 2 starts at its first sentence").isZero();
		assertThat(service.setScriptLine(1, 5).scriptLine()).as("a late step for scene 1 is ignored").isZero();
		service.setScriptLine(2, 1);

		service.back();
		assertThat(service.status().currentSceneId()).contains(1L);
		assertThat(service.status().scriptLine()).as("back into scene 1 starts it over").isZero();

		assertThatThrownBy(() -> service.setScriptLine(1, -1)).isInstanceOf(DomainValidationException.class);
	}

	@Test
	void autoFollowStartsTheNextSceneAtItsFirstSentence() {
		scenes.set(0, new Scene(1, 1, new SceneSpec(SceneNumber.of(1), "Scene 1", Optional.empty(),
				Optional.of(Duration.ofSeconds(2))), Map.of()));
		var service = service();
		go(service);
		service.setScriptLine(1, 4);

		clock.advance(Duration.ofSeconds(3));
		service.layer(clock.instant(), Map.of());

		assertThat(service.status().currentSceneId()).contains(2L);
		assertThat(service.status().scriptLine()).isZero();
	}

	@Test
	void renderLoopDoesNotWaitForTheDatabaseDuringAReload() throws Exception {
		var service = service();
		go(service);
		var reading = new CountDownLatch(1);
		var release = new CountDownLatch(1);
		onSceneQuery = () -> {
			reading.countDown();
			try {
				release.await();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		};
		var reload = Thread.ofPlatform().start(service::reload);
		try {
			assertThat(reading.await(5, TimeUnit.SECONDS)).isTrue();
			var tick = CompletableFuture.supplyAsync(() -> service.layer(clock.instant(), Map.of()));
			assertThat(tick.get(5, TimeUnit.SECONDS).fixtures()).containsKey(1L);
		} finally {
			release.countDown();
			reload.join();
		}
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
		return new PlaybackService(projects(), sceneRepository(), new CompositionResolver(compositions, groups()), states,
				Runnable::run, clock, events);
	}

	private void go(PlaybackService service) {
		service.go();
		clock.advance(Duration.ofSeconds(1)); // beyond the double-GO guard
	}

	private static GroupRepository groups() {
		return new GroupRepository() {
			@Override
			public List<Group> findByProject(long projectId) {
				return List.of();
			}

			@Override
			public Optional<Group> findById(long id) {
				return Optional.empty();
			}

			@Override
			public Group create(long projectId, GroupSpec spec) {
				throw new UnsupportedOperationException();
			}

			@Override
			public Group update(long id, GroupSpec spec) {
				throw new UnsupportedOperationException();
			}

			@Override
			public void delete(long id) {
				throw new UnsupportedOperationException();
			}
		};
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
				onSceneQuery.run();
				return List.copyOf(scenes);
			}

			@Override
			public Optional<Scene> findById(long id) {
				return scenes.stream().filter(s -> s.id() == id).findFirst();
			}

			@Override
			public Scene create(long projectId, SceneSpec spec, de.vinzens.lumen.domain.scene.Look look) {
				throw new UnsupportedOperationException();
			}

			@Override
			public Scene updateSpec(long id, SceneSpec spec) {
				throw new UnsupportedOperationException();
			}

			@Override
			public Scene replaceLook(long id, de.vinzens.lumen.domain.scene.Look look) {
				throw new UnsupportedOperationException();
			}

			@Override
			public void delete(long id) {
				throw new UnsupportedOperationException();
			}

			public de.vinzens.lumen.domain.scene.SceneRevision saveRevision(long sceneId, java.time.Instant createdAt,
					String reason, de.vinzens.lumen.domain.scene.Look look, int keep) {
				throw new UnsupportedOperationException();
			}

			public java.util.List<de.vinzens.lumen.domain.scene.SceneRevision> findRevisions(long sceneId) {
				return java.util.List.of();
			}

			public java.util.Optional<de.vinzens.lumen.domain.scene.SceneRevision> findRevision(long id) {
				return java.util.Optional.empty();
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
