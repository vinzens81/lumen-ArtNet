package de.vinzens.lumen.application.output;

import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.output.port.in.WatchNodesUseCase.NodeCheck;
import de.vinzens.lumen.application.output.port.in.WatchNodesUseCase.NodeState;
import de.vinzens.lumen.application.output.port.out.ExpectedNodeRepository;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.application.project.port.out.UniverseRepository;
import de.vinzens.lumen.domain.project.ArtNetAddress;
import de.vinzens.lumen.domain.project.ArtNetNode;
import de.vinzens.lumen.domain.project.ArtNetTarget;
import de.vinzens.lumen.domain.project.ExpectedNode;
import de.vinzens.lumen.domain.project.Project;
import de.vinzens.lumen.domain.project.ProjectSpec;
import de.vinzens.lumen.domain.project.Universe;
import de.vinzens.lumen.domain.project.UniverseSpec;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class NodeWatchServiceTest {

	private static final Instant START = Instant.parse("2026-10-05T19:00:00Z");

	private Instant now = START;
	private final Clock clock = new Clock() {
		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	};
	private final List<ArtNetNode> found = new ArrayList<>();
	private final TreeMap<String, ExpectedNode> saved = new TreeMap<>();
	private final StateEvents events = new StateEvents();
	private final List<Universe> universes = new ArrayList<>(List.of(
			universe(1, 0, ArtNetTarget.auto()),
			universe(2, 1, ArtNetTarget.parse("auto,10.0.0.5"))));

	@Test
	void learnsNodesOfTheProjectAndMissesThemWhenTheyStopAnswering() {
		found.add(node("192.168.0.150", "ESP dimmer", 0));
		found.add(node("192.168.0.160", "Other show", 7));
		var watch = service();

		assertThat(watch.check()).extracting(NodeCheck::ipAddress, NodeCheck::state, NodeCheck::universes)
				.containsExactly(
						tuple("10.0.0.5", NodeState.UNCHECKED, List.of(2)),
						tuple("192.168.0.150", NodeState.ONLINE, List.of(1)));
		assertThat(saved).containsOnlyKeys("192.168.0.150");

		now = now.plus(Duration.ofSeconds(11)); // no ArtPollReply since
		assertThat(watch.check()).filteredOn(c -> c.ipAddress().equals("192.168.0.150")).singleElement()
				.satisfies(check -> {
					assertThat(check.state()).isEqualTo(NodeState.OFFLINE);
					assertThat(check.name()).isEqualTo("ESP dimmer");
					assertThat(check.lastSeen()).contains(START);
				});
	}

	@Test
	void missesANodeThatIsAlreadyGoneAfterARestart() {
		saved.put("192.168.0.150", new ExpectedNode("192.168.0.150", "ESP dimmer"));

		assertThat(service().check()).extracting(NodeCheck::ipAddress, NodeCheck::state, NodeCheck::universes)
				.contains(tuple("192.168.0.150", NodeState.OFFLINE, List.of()));
	}

	@Test
	void aManualAddressThatAnswersIsOnline() {
		found.add(node("10.0.0.5", "Dimmer rack", 9));

		assertThat(service().check()).extracting(NodeCheck::ipAddress, NodeCheck::name, NodeCheck::state)
				.containsExactly(tuple("10.0.0.5", "Dimmer rack", NodeState.ONLINE));
		assertThat(saved).as("does not output a universe of the project").isEmpty();
	}

	@Test
	void forgottenNodesAreNoLongerExpected() {
		saved.put("192.168.0.150", new ExpectedNode("192.168.0.150", "ESP dimmer"));
		var watch = service();
		watch.check();

		watch.forget("192.168.0.150");

		assertThat(saved).isEmpty();
		assertThat(watch.nodes()).extracting(NodeCheck::ipAddress).containsExactly("10.0.0.5");
		assertThat(watch.check()).extracting(NodeCheck::ipAddress).containsExactly("10.0.0.5");
	}

	@Test
	void followsPatchChanges() {
		var watch = service();
		universes.removeLast();
		events.publish(StateTopic.PATCH);

		assertThat(watch.check()).isEmpty();
	}

	private NodeWatchService service() {
		return new NodeWatchService(projects(), universeRepository(), expectedNodes(), () -> List.copyOf(found), clock,
				events);
	}

	private ArtNetNode node(String ip, String name, int portAddress) {
		return new ArtNetNode(ip, 1, name, name + " (long)", "", List.of(portAddress), "", now);
	}

	private static Universe universe(int number, int portAddress, ArtNetTarget target) {
		return new Universe(number, 1, new UniverseSpec(number, Optional.empty(),
				ArtNetAddress.fromPortAddress(portAddress), target));
	}

	private ExpectedNodeRepository expectedNodes() {
		return new ExpectedNodeRepository() {
			@Override
			public List<ExpectedNode> findByProject(long projectId) {
				return List.copyOf(saved.values());
			}

			@Override
			public void save(long projectId, ExpectedNode node) {
				saved.put(node.ipAddress(), node);
			}

			@Override
			public void delete(long projectId, String ipAddress) {
				saved.remove(ipAddress);
			}
		};
	}

	private UniverseRepository universeRepository() {
		return new UniverseRepository() {
			@Override
			public List<Universe> findByProject(long projectId) {
				return List.copyOf(universes);
			}

			@Override
			public Optional<Universe> findById(long id) {
				return universes.stream().filter(u -> u.id() == id).findFirst();
			}

			@Override
			public Universe create(long projectId, UniverseSpec spec) {
				throw new UnsupportedOperationException();
			}

			@Override
			public Universe update(long id, UniverseSpec spec) {
				throw new UnsupportedOperationException();
			}

			@Override
			public void delete(long id) {
				throw new UnsupportedOperationException();
			}
		};
	}

	private static ProjectRepository projects() {
		var project = new Project(1, new ProjectSpec("Show", Optional.empty(), Duration.ZERO, Duration.ZERO), true,
				START, START);
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
}
