package de.vinzens.lumen.application.output;

import de.vinzens.lumen.application.ConflictException;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.output.port.in.WatchNodesUseCase;
import de.vinzens.lumen.application.output.port.out.ArtNetNodeDirectory;
import de.vinzens.lumen.application.output.port.out.ExpectedNodeRepository;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.application.project.port.out.UniverseRepository;
import de.vinzens.lumen.domain.project.ArtNetNode;
import de.vinzens.lumen.domain.project.ArtNetTarget;
import de.vinzens.lumen.domain.project.ExpectedNode;
import de.vinzens.lumen.domain.project.Project;
import de.vinzens.lumen.domain.project.Universe;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Knows which Art-Net nodes the active project needs and checks whether they answer. A node is expected from the
 * moment it reports one of the project's universes in its ArtPollReply; that is saved, so it is also missed after a
 * restart. Manual IP addresses of the universes are watched too, but a node that never answers ArtPoll cannot be
 * checked.
 */
public class NodeWatchService implements WatchNodesUseCase {

	private final ProjectRepository projects;
	private final UniverseRepository universes;
	private final ExpectedNodeRepository expected;
	private final ArtNetNodeDirectory directory;
	private final Clock clock;

	private Long projectId;
	private List<Universe> projectUniverses = List.of();
	/** by IP address */
	private final Map<String, ExpectedNode> known = new LinkedHashMap<>();
	private List<NodeCheck> last = List.of();

	public NodeWatchService(ProjectRepository projects, UniverseRepository universes, ExpectedNodeRepository expected,
			ArtNetNodeDirectory directory, Clock clock, StateEvents events) {
		this.projects = Objects.requireNonNull(projects, "projects");
		this.universes = Objects.requireNonNull(universes, "universes");
		this.expected = Objects.requireNonNull(expected, "expected");
		this.directory = Objects.requireNonNull(directory, "directory");
		this.clock = Objects.requireNonNull(clock, "clock");
		events.subscribe(topic -> {
			if (topic == StateTopic.PROJECTS || topic == StateTopic.PATCH) {
				reload();
			}
		});
		reload();
	}

	/** Reads the active project, its universes and its expected nodes. */
	public synchronized void reload() {
		var active = projects.findAll().stream().filter(Project::active).findFirst();
		projectId = active.map(Project::id).orElse(null);
		projectUniverses = active.map(project -> universes.findByProject(project.id())).orElse(List.of());
		known.clear();
		active.ifPresent(project -> expected.findByProject(project.id())
				.forEach(node -> known.put(node.ipAddress(), node)));
	}

	/** Learns newly answering nodes and checks all expected ones; called every second while Art-Net is on. */
	public synchronized List<NodeCheck> check() {
		if (projectId == null) {
			return last = List.of();
		}
		var now = clock.instant();
		var found = directory.nodes();
		learn(found, now);

		var result = new ArrayList<NodeCheck>();
		for (var node : known.values()) {
			var replies = found.stream().filter(n -> n.ipAddress().equals(node.ipAddress())).toList();
			var online = replies.stream().anyMatch(n -> n.isOnline(now));
			result.add(new NodeCheck(node.ipAddress(), node.name(), online ? NodeState.ONLINE : NodeState.OFFLINE,
					universesOf(node.ipAddress(), replies), lastSeen(replies)));
		}
		for (var ip : manualAddresses()) {
			if (known.containsKey(ip)) {
				continue;
			}
			var replies = found.stream().filter(n -> n.ipAddress().equals(ip)).toList();
			var online = replies.stream().anyMatch(n -> n.isOnline(now));
			result.add(new NodeCheck(ip, replies.stream().map(ArtNetNode::shortName).findFirst().orElse(""),
					online ? NodeState.ONLINE : NodeState.UNCHECKED, universesOf(ip, replies), lastSeen(replies)));
		}
		result.sort(Comparator.comparing(NodeCheck::ipAddress));
		return last = List.copyOf(result);
	}

	@Override
	public synchronized List<NodeCheck> nodes() {
		return last;
	}

	@Override
	public synchronized void forget(String ipAddress) {
		if (projectId == null) {
			throw new ConflictException("There is no active project");
		}
		expected.delete(projectId, ipAddress);
		known.remove(ipAddress);
		last = last.stream().filter(check -> !check.ipAddress().equals(ipAddress)
				|| manualAddresses().contains(ipAddress)).toList();
	}

	/** Online nodes that output a universe of the project become expected; a changed name is updated. */
	private void learn(List<ArtNetNode> found, Instant now) {
		var ports = projectUniverses.stream().map(u -> u.spec().artNet().portAddress()).toList();
		for (var node : found) {
			if (!node.isOnline(now) || node.portAddresses().stream().noneMatch(ports::contains)) {
				continue;
			}
			var name = node.shortName().isBlank() ? node.longName() : node.shortName();
			var previous = known.get(node.ipAddress());
			if (previous == null || !previous.name().equals(name)) {
				var learned = new ExpectedNode(node.ipAddress(), name);
				expected.save(projectId, learned);
				known.put(learned.ipAddress(), learned);
			}
		}
	}

	private TreeSet<String> manualAddresses() {
		var result = new TreeSet<String>();
		projectUniverses.forEach(universe -> {
			if (universe.spec().target() instanceof ArtNetTarget.Unicast unicast) {
				result.addAll(unicast.ipAddresses());
			}
		});
		return result;
	}

	/** Universes the node reported (as far as still known) plus those that list its IP address. */
	private List<Integer> universesOf(String ip, List<ArtNetNode> replies) {
		var ports = replies.stream().flatMap(n -> n.portAddresses().stream()).toList();
		return projectUniverses.stream()
				.filter(universe -> ports.contains(universe.spec().artNet().portAddress())
						|| universe.spec().target() instanceof ArtNetTarget.Unicast unicast
								&& unicast.ipAddresses().contains(ip))
				.map(universe -> universe.spec().number())
				.sorted()
				.toList();
	}

	private static Optional<Instant> lastSeen(List<ArtNetNode> replies) {
		return replies.stream().map(ArtNetNode::lastSeen).max(Comparator.naturalOrder());
	}
}
