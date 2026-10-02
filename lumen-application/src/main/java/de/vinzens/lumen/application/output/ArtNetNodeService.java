package de.vinzens.lumen.application.output;

import de.vinzens.lumen.application.output.port.in.QueryArtNetNodesUseCase;
import de.vinzens.lumen.application.output.port.out.ArtNetNodeDirectory;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class ArtNetNodeService implements QueryArtNetNodesUseCase {

	private final ArtNetNodeDirectory directory;
	private final Clock clock;

	public ArtNetNodeService(ArtNetNodeDirectory directory, Clock clock) {
		this.directory = Objects.requireNonNull(directory, "directory");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Override
	public List<NodeStatus> nodes() {
		var now = clock.instant();
		return directory.nodes().stream()
				.map(node -> new NodeStatus(node, node.isOnline(now)))
				.sorted(Comparator.comparing((NodeStatus s) -> !s.online())
						.thenComparing(s -> s.node().ipAddress())
						.thenComparingInt(s -> s.node().bindIndex()))
				.toList();
	}
}
