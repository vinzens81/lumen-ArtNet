package de.vinzens.lumen.application;

import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.domain.composition.Chase;
import de.vinzens.lumen.domain.composition.Color;
import de.vinzens.lumen.domain.composition.ColorCycle;
import de.vinzens.lumen.domain.composition.FixtureAttributes;
import de.vinzens.lumen.domain.composition.Flicker;
import de.vinzens.lumen.application.library.port.in.QueryFixtureLibraryUseCase;
import de.vinzens.lumen.application.output.OutputEngine;
import de.vinzens.lumen.application.playback.InMemoryFaders;
import de.vinzens.lumen.application.playback.PlaybackService;
import de.vinzens.lumen.application.playback.port.out.PlaybackStateRepository;
import de.vinzens.lumen.application.project.port.out.FixtureRepository;
import de.vinzens.lumen.application.project.port.out.GroupRepository;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.application.project.port.out.UniverseRepository;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelDefinition;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.library.FixtureCategory;
import de.vinzens.lumen.domain.library.FixtureType;
import de.vinzens.lumen.domain.library.LibraryIssue;
import de.vinzens.lumen.domain.library.Mode;
import de.vinzens.lumen.domain.library.PhysicalProperties;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.output.OperatingMode;
import de.vinzens.lumen.domain.project.ArtNetAddress;
import de.vinzens.lumen.domain.project.ArtNetTarget;
import de.vinzens.lumen.domain.project.FixtureSpec;
import de.vinzens.lumen.domain.project.Group;
import de.vinzens.lumen.domain.project.GroupSpec;
import de.vinzens.lumen.domain.project.PatchedFixture;
import de.vinzens.lumen.domain.project.Project;
import de.vinzens.lumen.domain.project.ProjectSpec;
import de.vinzens.lumen.domain.project.Universe;
import de.vinzens.lumen.domain.project.UniverseSpec;
import de.vinzens.lumen.domain.scene.SceneComposition;
import de.vinzens.lumen.domain.scene.SceneNumber;
import de.vinzens.lumen.domain.scene.SceneSpec;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Durability test of iteration 8: plays a show for three hours of simulated time at the real frame rate –
 * auto-follow chains, manual GOs, pauses, master moves and blackouts, scenes with running compositions – and
 * checks that memory does not grow.
 */
class ShowSoakTest {

	private static final Duration SHOW = Duration.ofHours(3);
	private static final Duration WARM_UP = Duration.ofMinutes(15);
	private static final Duration FRAME = Duration.ofMillis(25); // 40 Hz
	/** Retained heap may vary a little between measurements, but must not grow with the show length. */
	private static final long MAX_GROWTH_BYTES = 8L * 1024 * 1024;

	private static final Mode PAR_MODE = new Mode("4ch", List.of(channel(ChannelFunction.DIMMER),
			channel(ChannelFunction.RED), channel(ChannelFunction.GREEN), channel(ChannelFunction.BLUE)));
	private static final Mode DIMMER_MODE = new Mode("1ch", List.of(channel(ChannelFunction.DIMMER)));
	private static final FixtureType PAR = new FixtureType("par", "Generic", "Par", FixtureCategory.PAR,
			PhysicalProperties.NONE, List.of(PAR_MODE));
	private static final FixtureType DIMMER = new FixtureType("dimmer", "Generic", "Dimmer", FixtureCategory.DIMMER,
			PhysicalProperties.NONE, List.of(DIMMER_MODE));

	private final TestClock clock = new TestClock();
	private final Project project = new Project(1, new ProjectSpec("Soak", Optional.empty(), Duration.ofSeconds(3),
			Duration.ofSeconds(5)), true, Instant.EPOCH, Instant.EPOCH);
	private final List<Universe> universes = List.of(universe(1), universe(2));
	private final List<PatchedFixture> fixtures = new ArrayList<>();
	private final TestShow show = new TestShow();
	private long framesSent;

	@Test
	void threeHourShowRunsWithoutGrowingMemory() {
		patch();
		recordScenes();
		var scenes = show.scenes.findByProject(1);
		var events = new StateEvents();
		var engine = new OutputEngine(projects(), universes(), fixtures(), groups(), library(),
				(universe, frame) -> framesSent++, events, clock);
		var playback = new PlaybackService(projects(), show.scenes, show.resolver(), states(), new InMemoryFaders(),
				Runnable::run, clock, events);
		engine.addLayerSource(playback);
		engine.patchChanged();
		engine.setMode(OperatingMode.SHOW);

		var visited = new HashSet<Long>();
		var ticks = 0L;
		long heapAfterWarmUp = 0;
		var start = clock.instant();
		var nanos = System.nanoTime();
		while (Duration.between(start, clock.instant()).compareTo(SHOW) < 0) {
			clock.advance(FRAME);
			engine.tick();
			ticks++;
			operate(ticks, engine, playback);
			playback.status().currentSceneId().ifPresent(visited::add);
			if (heapAfterWarmUp == 0 && Duration.between(start, clock.instant()).compareTo(WARM_UP) >= 0) {
				heapAfterWarmUp = usedHeapAfterGc();
			}
		}
		var heapAtEnd = usedHeapAfterGc();
		var wallTime = Duration.ofNanos(System.nanoTime() - nanos);

		System.out.printf("Soak test: %d frames (%s simulated) in %d ms, %.1f µs per frame, heap %d KB → %d KB%n",
				ticks, SHOW, wallTime.toMillis(), wallTime.toNanos() / 1000.0 / ticks, heapAfterWarmUp / 1024,
				heapAtEnd / 1024);
		assertThat(ticks).isEqualTo(SHOW.toMillis() / FRAME.toMillis());
		assertThat(framesSent).as("every universe on every frame").isEqualTo(ticks * universes.size());
		assertThat(visited).as("every scene was on stage").hasSize(scenes.size());
		assertThat(heapAtEnd - heapAfterWarmUp).as("heap growth in bytes").isLessThan(MAX_GROWTH_BYTES);
	}

	/** What an operator does during the show; times are relative to the start. */
	private void operate(long tick, OutputEngine engine, PlaybackService playback) {
		var second = tick * FRAME.toMillis() / 1000.0;
		var status = playback.status();
		if (second % 20 == 0 && status.autoFollowIn().isEmpty()) {
			if (status.nextSceneId().isEmpty()) {
				playback.setNext(show.scenes.findByProject(1).getFirst().id()); // start the show again
			}
			playback.go();
		}
		if (second % 47 == 0) {
			engine.setMasterLevel(0.5 + (tick % 50) / 100.0);
		}
		if (second % 600 == 0) {
			engine.setBlackout(true);
		} else if (second % 600 == 1) {
			engine.setBlackout(false);
		}
		if (second % 900 == 300 || second % 900 == 302) {
			playback.togglePause();
		}
	}

	private void patch() {
		long id = 1;
		for (var universe : universes) {
			for (int i = 0; i < 12; i++) {
				fixtures.add(new PatchedFixture(id++, 1, new FixtureSpec("PAR " + id, Optional.empty(), "par", "4ch",
						universe.id(), 1 + i * 4, Optional.empty(), 0)));
				fixtures.add(new PatchedFixture(id++, 1, new FixtureSpec("Dimmer " + id, Optional.empty(), "dimmer",
						"1ch", universe.id(), 100 + i, Optional.empty(), 0)));
			}
		}
	}

	/**
	 * Every scene: its fixed values, a chase on the dimmers and a color cycle on the PARs. Scenes 1–6 share a flicker
	 * that continues across GOs.
	 */
	private void recordScenes() {
		var dimmers = fixtures.stream().filter(f -> f.spec().fixtureTypeId().equals("dimmer")).map(PatchedFixture::id)
				.toList();
		var pars = fixtures.stream().filter(f -> f.spec().fixtureTypeId().equals("par")).map(PatchedFixture::id)
				.toList();
		var attributes = FixtureAttributes.ALL;
		var candle = show.generated(1, "Candle", new Flicker(Flicker.Character.FIRE, 0.4, 1, 9), pars, attributes);
		for (int s = 1; s <= 12; s++) {
			var values = new HashMap<Long, FixtureValues>();
			for (var fixture : fixtures) {
				if ((fixture.id() + s) % 3 == 0) {
					continue; // not in this scene: dark
				}
				var level = ((fixture.id() * 7 + s * 13) % 100) / 100.0;
				values.put(fixture.id(), fixture.spec().fixtureTypeId().equals("par")
						? new FixtureValues(Map.of(Attribute.of(ChannelFunction.DIMMER), level,
								Attribute.of(ChannelFunction.RED), (s % 3) / 2.0,
								Attribute.of(ChannelFunction.GREEN), (s % 4) / 3.0,
								Attribute.of(ChannelFunction.BLUE), (s % 5) / 4.0))
						: new FixtureValues(Map.of(Attribute.of(ChannelFunction.DIMMER), level)));
			}
			var uses = new ArrayList<SceneComposition>();
			uses.add(SceneComposition.of(show.fixed(1, "Scene " + s, values).id()));
			uses.add(SceneComposition.of(show.generated(1, "Chase " + s,
					new Chase(0.3, 2, Chase.Direction.PING_PONG, 0.5, 0, 1), dimmers, attributes).id()));
			uses.add(SceneComposition.of(show.generated(1, "Colors " + s,
					new ColorCycle(List.of(new Color(1, 0, 0), new Color(0, 0, 1), new Color(0, 1, 0)), 2, 1, 0.5, 1),
					pars, attributes).id()));
			if (s <= 6) {
				uses.add(SceneComposition.of(candle.id()));
			}
			var autoFollow = s % 3 == 0 ? Optional.of(Duration.ofSeconds(12)) : Optional.<Duration>empty();
			show.scenes.create(1, new SceneSpec(SceneNumber.of(s), "Scene " + s, Optional.empty(), autoFollow), uses,
					List.of());
		}
	}

	private static long usedHeapAfterGc() {
		var memory = ManagementFactory.getMemoryMXBean();
		for (int i = 0; i < 3; i++) {
			System.gc();
		}
		return memory.getHeapMemoryUsage().getUsed();
	}

	private static Universe universe(int number) {
		return new Universe(10 + number, 1, new UniverseSpec(number, Optional.empty(),
				ArtNetAddress.fromPortAddress(number - 1), new ArtNetTarget.Broadcast()));
	}

	private static ChannelDefinition channel(ChannelFunction function) {
		return new ChannelDefinition(function, Optional.empty(), 8, 0, false, List.of(), Optional.empty());
	}

	// --- in-memory ports -----------------------------------------------------------------------------------

	private QueryFixtureLibraryUseCase library() {
		return new QueryFixtureLibraryUseCase() {
			@Override
			public List<FixtureType> fixtureTypes() {
				return List.of(PAR, DIMMER);
			}

			@Override
			public Optional<FixtureType> fixtureType(String id) {
				return fixtureTypes().stream().filter(type -> type.id().equals(id)).findFirst();
			}

			@Override
			public List<LibraryIssue> issues() {
				return List.of();
			}
		};
	}

	private ProjectRepository projects() {
		return new ReadOnly.Projects(List.of(project));
	}

	private UniverseRepository universes() {
		return new ReadOnly.Universes(universes);
	}

	private FixtureRepository fixtures() {
		return new ReadOnly.Fixtures(fixtures);
	}

	private GroupRepository groups() {
		return new ReadOnly.Groups(List.<Group>of());
	}

	private PlaybackStateRepository states() {
		var saved = new HashMap<Long, Optional<Long>>();
		return new PlaybackStateRepository() {
			@Override
			public Optional<Long> currentScene(long projectId) {
				return saved.getOrDefault(projectId, Optional.empty());
			}

			@Override
			public void save(long projectId, Optional<Long> sceneId, Instant now) {
				saved.put(projectId, sceneId);
			}
		};
	}

	/** Repositories over fixed lists; the soak test never changes the project. */
	private static final class ReadOnly {

		record Projects(List<Project> all) implements ProjectRepository {
			public List<Project> findAll() {
				return all;
			}

			public Optional<Project> findById(long id) {
				return all.stream().filter(p -> p.id() == id).findFirst();
			}

			public Project create(ProjectSpec spec, Instant now) {
				throw new UnsupportedOperationException();
			}

			public Project update(long id, ProjectSpec spec, Instant now) {
				throw new UnsupportedOperationException();
			}

			public void delete(long id) {
				throw new UnsupportedOperationException();
			}

			public void activate(long id) {
				throw new UnsupportedOperationException();
			}
		}

		record Universes(List<Universe> all) implements UniverseRepository {
			public List<Universe> findByProject(long projectId) {
				return all;
			}

			public Optional<Universe> findById(long id) {
				return all.stream().filter(u -> u.id() == id).findFirst();
			}

			public Universe create(long projectId, UniverseSpec spec) {
				throw new UnsupportedOperationException();
			}

			public Universe update(long id, UniverseSpec spec) {
				throw new UnsupportedOperationException();
			}

			public void delete(long id) {
				throw new UnsupportedOperationException();
			}
		}

		record Fixtures(List<PatchedFixture> all) implements FixtureRepository {
			public List<PatchedFixture> findByProject(long projectId) {
				return all;
			}

			public Optional<PatchedFixture> findById(long id) {
				return all.stream().filter(f -> f.id() == id).findFirst();
			}

			public PatchedFixture create(long projectId, FixtureSpec spec) {
				throw new UnsupportedOperationException();
			}

			public PatchedFixture update(long id, FixtureSpec spec) {
				throw new UnsupportedOperationException();
			}

			public void delete(long id) {
				throw new UnsupportedOperationException();
			}

			public int countByUniverse(long universeId) {
				throw new UnsupportedOperationException();
			}
		}

		record Groups(List<Group> all) implements GroupRepository {
			public List<Group> findByProject(long projectId) {
				return all;
			}

			public Optional<Group> findById(long id) {
				return Optional.empty();
			}

			public Group create(long projectId, GroupSpec spec) {
				throw new UnsupportedOperationException();
			}

			public Group update(long id, GroupSpec spec) {
				throw new UnsupportedOperationException();
			}

			public void delete(long id) {
				throw new UnsupportedOperationException();
			}
		}
	}

	private static final class TestClock extends Clock {

		private Instant now = Instant.parse("2026-10-02T19:00:00Z");

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
