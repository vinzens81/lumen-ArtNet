package de.vinzens.lumen.application.output;

import de.vinzens.lumen.domain.project.ArtNetNode;
import de.vinzens.lumen.domain.project.LocalNetwork;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ArtNetNodeServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-05T20:00:00Z");

	@Test
	void nodesKnowTheInterfaceTheyAreReachedOn() {
		var wifi = new LocalNetwork("en0", "192.168.0.23", 24);
		var lighting = new LocalNetwork("en7", "2.0.0.10", 8);
		var service = new ArtNetNodeService(() -> List.of(node("192.168.0.150"), node("2.0.0.21"), node("10.0.0.5")),
				() -> List.of(wifi, lighting), Clock.fixed(NOW, ZoneOffset.UTC));

		assertThat(service.nodes()).extracting(status -> status.node().ipAddress(), status -> status.networkInterface())
				.containsExactly(
						org.assertj.core.groups.Tuple.tuple("10.0.0.5", Optional.empty()),
						org.assertj.core.groups.Tuple.tuple("192.168.0.150", Optional.of("en0")),
						org.assertj.core.groups.Tuple.tuple("2.0.0.21", Optional.of("en7")));
		assertThat(service.networks()).containsExactly(wifi, lighting);
	}

	private static ArtNetNode node(String ip) {
		return new ArtNetNode(ip, 1, "Node", "Node", "", List.of(0), "", NOW);
	}
}
