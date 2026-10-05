package de.vinzens.lumen.application.output;

import de.vinzens.lumen.application.output.port.in.QueryArtNetNodesUseCase;
import de.vinzens.lumen.application.output.port.out.ArtNetNodeDirectory;
import de.vinzens.lumen.application.output.port.out.LocalNetworks;
import de.vinzens.lumen.domain.project.LocalNetwork;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class ArtNetNodeService implements QueryArtNetNodesUseCase {

	private final ArtNetNodeDirectory directory;
	private final LocalNetworks networks;
	private final Clock clock;

	public ArtNetNodeService(ArtNetNodeDirectory directory, LocalNetworks networks, Clock clock) {
		this.directory = Objects.requireNonNull(directory, "directory");
		this.networks = Objects.requireNonNull(networks, "networks");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Override
	public List<NodeStatus> nodes() {
		var now = clock.instant();
		var local = networks.networks();
		return directory.nodes().stream()
				.map(node -> new NodeStatus(node, node.isOnline(now), local.stream()
						.filter(network -> network.contains(node.ipAddress()))
						.map(LocalNetwork::interfaceName)
						.findFirst()))
				.sorted(Comparator.comparing((NodeStatus s) -> !s.online())
						.thenComparing(s -> s.node().ipAddress())
						.thenComparingInt(s -> s.node().bindIndex()))
				.toList();
	}

	@Override
	public List<LocalNetwork> networks() {
		return networks.networks().stream().sorted(Comparator.comparing(LocalNetwork::interfaceName)).toList();
	}
}
