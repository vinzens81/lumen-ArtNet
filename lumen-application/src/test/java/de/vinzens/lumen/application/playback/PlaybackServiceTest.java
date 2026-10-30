package de.vinzens.lumen.application.playback;

import de.vinzens.lumen.application.ConflictException;
import de.vinzens.lumen.application.TestShow;
import de.vinzens.lumen.application.composition.CompositionResolver;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.playback.port.out.PlaybackStateRepository;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.application.scene.port.out.SceneRepository;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.composition.Fader;
import de.vinzens.lumen.domain.composition.KeyMode;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelDefinition;
import de.vinzens.lumen.domain.library.Mode;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.project.Project;
import de.vinzens.lumen.domain.project.ProjectSpec;
import de.vinzens.lumen.domain.scene.Scene;
import de.vinzens.lumen.domain.scene.SceneComposition;
import de.vinzens.lumen.domain.scene.SceneLook;
import de.vinzens.lumen.domain.scene.SceneMove;
import de.vinzens.lumen.domain.scene.SceneKey;
import de.vinzens.lumen.domain.scene.SceneNumber;
import de.vinzens.lumen.domain.scene.SceneSpec;
import de.vinzens.lumen.domain.scene.SectionTiming;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaybackServiceTest {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	private static final Mode DIMMER_MODE = new Mode("1ch", List.of(new ChannelDefinition(ChannelFunction.DIMMER,
			Optional.empty(), 8, 0, false, List.of(), Optional.empty())));
	/** fixtures 1–9 are dimmers: the output only sets attributes a fixture has */
	private static final Map<Long, Mode> DIMMERS = Map.of(1L, DIMMER_MODE, 2L, DIMMER_MODE, 3L, DIMMER_MODE,
			4L, DIMMER_MODE, 5L, DIMMER_MODE, 9L, DIMMER_MODE);

	private final TestClock clock = new TestClock();
	private final TestShow show = new TestShow();
	private final Map<Long, Optional<Long>> saved = new HashMap<>();
	private final List<Optional<Long>> writes = new ArrayList<>();
	private final StateEvents events = new StateEvents();
	private final InMemoryFaders faders = new InMemoryFaders();
	/** Runs inside every scene query, e.g. to make the database slow. */
	private Runnable onSceneQuery = () -> {
	};

	@BeforeEach
	void setUp() {
		// scene n lights fixture n
		for (int i = 1; i <= 5; i++) {
			var lit = show.fixed(1, "Scene " + i, Map.of((long) i, new FixtureValues(Map.of(DIMMER, 1.0))));
			show.scenes.create(1, new SceneSpec(SceneNumber.parse(String.valueOf(i)), "Scene " + i,
					Optional.empty(), Optional.empty()), List.of(SceneComposition.of(lit.id())), List.of());
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
		assertThat(after.layer(clock.instant(), DIMMERS).fixtures().get(4L).get(DIMMER)).contains(1.0);
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
		show.scenes.delete(3);

		assertThat(service().status().resumeSceneId()).isEmpty();
	}

	@Test
	void fadersLayTheirSceneOverTheCueListAndKeepTheirLevel() {
		faders.save(1, new Fader(1, Optional.of("House lights"), 5));
		var service = service();

		assertThat(service.status().faders()).singleElement()
				.satisfies(f -> assertThat(f.name()).isEqualTo("House lights"));
		service.setFader(1, 0.4);
		assertThat(service.layer(clock.instant(), DIMMERS).fixtures().get(5L).get(DIMMER)).contains(0.4);

		events.publish(StateTopic.FADERS); // reload: the level stays
		assertThat(service.status().faders().getFirst().level()).isEqualTo(0.4);
		assertThatThrownBy(() -> service.setFader(2, 1)).hasMessageContaining("No fader on slot 2");
	}

	@Test
	void extraKeysBelongToTheirScenesAndGoOffAfterwards() {
		// key 1: full intensity on fixture 9 in scene 1, key 4 with the same composition in scene 2
		var storm = show.fixed(1, "Storm", Map.of(9L, new FixtureValues(Map.of(DIMMER, 1.0))));
		show.scenes.replaceKeys(1, List.of(new SceneKey(1, storm.id(), KeyMode.TOGGLE, Duration.ZERO)));
		show.scenes.replaceKeys(2, List.of(new SceneKey(4, storm.id(), KeyMode.TOGGLE, Duration.ZERO)));
		var service = service();

		service.pressKey(1);
		assertThat(keyDimmer(service)).as("no scene on stage yet").isZero();

		go(service);
		var status = service.pressKey(1);
		assertThat(status.keys()).singleElement().satisfies(key -> {
			assertThat(key.on()).isTrue();
			assertThat(key.name()).isEqualTo("Storm");
		});
		assertThat(keyDimmer(service)).isEqualTo(1.0);

		go(service); // scene 2: the same composition is on key 4 – stays on
		assertThat(keyDimmer(service)).isEqualTo(1.0);
		assertThat(service.status().keys()).singleElement().satisfies(key -> {
			assertThat(key.slot()).isEqualTo(4);
			assertThat(key.on()).isTrue();
		});
		go(service); // scene 3: no keys – off
		assertThat(keyDimmer(service)).isZero();
		assertThat(service.status().keys()).isEmpty();
	}

	private double keyDimmer(PlaybackService service) {
		return service.layer(clock.instant(), DIMMERS).fixtures().getOrDefault(9L, FixtureValues.EMPTY)
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
		show.scenes.updateSpec(1, new SceneSpec(SceneNumber.of(1), "Scene 1", Optional.empty(),
				Optional.of(Duration.ofSeconds(2))));
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
		var scenes = sceneRepository();
		return new PlaybackService(projects(), scenes,
				new CompositionResolver(show.compositions, show.looks, scenes, show.moves, show.figures), states(), faders,
				Runnable::run, clock,
				events);
	}

	private PlaybackStateRepository states() {
		return new PlaybackStateRepository() {
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

	/** The scenes of the show; every query of the cue list runs {@link #onSceneQuery} first. */
	private SceneRepository sceneRepository() {
		return new SceneRepository() {
			@Override
			public List<Scene> findByProject(long projectId) {
				onSceneQuery.run();
				return show.scenes.findByProject(projectId);
			}

			@Override
			public Optional<Scene> findById(long id) {
				return show.scenes.findById(id);
			}

			@Override
			public Scene create(long projectId, SceneSpec spec, List<SceneComposition> compositions,
					List<SceneKey> keys) {
				return show.scenes.create(projectId, spec, compositions, keys);
			}

			@Override
			public Scene updateSpec(long id, SceneSpec spec) {
				return show.scenes.updateSpec(id, spec);
			}

			@Override
			public Scene replaceCompositions(long id, List<SceneComposition> compositions) {
				return show.scenes.replaceCompositions(id, compositions);
			}

			@Override
			public Scene replaceSection(long id, String marker, List<SceneComposition> compositions,
					List<SceneLook> looks, List<SceneMove> moves, SectionTiming timing) {
				return show.scenes.replaceSection(id, marker, compositions, looks, moves, timing);
			}

			@Override
			public Scene replaceKeys(long id, List<SceneKey> keys) {
				return show.scenes.replaceKeys(id, keys);
			}

			@Override
			public void delete(long id) {
				show.scenes.delete(id);
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
