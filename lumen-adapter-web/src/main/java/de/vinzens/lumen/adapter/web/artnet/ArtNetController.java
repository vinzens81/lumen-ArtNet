package de.vinzens.lumen.adapter.web.artnet;

import de.vinzens.lumen.application.output.port.in.QueryArtNetNodesUseCase;
import de.vinzens.lumen.application.output.port.in.QueryArtNetNodesUseCase.NodeStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/artnet")
class ArtNetController {

	private final QueryArtNetNodesUseCase nodes;

	ArtNetController(QueryArtNetNodesUseCase nodes) {
		this.nodes = nodes;
	}

	/** Art-Net nodes found on the network via ArtPoll. */
	@GetMapping("/nodes")
	List<NodeResponse> nodes() {
		return nodes.nodes().stream().map(NodeResponse::from).toList();
	}

	/**
	 * @param portAddresses universes (15-bit port addresses) the node outputs
	 */
	record NodeResponse(String ipAddress, int bindIndex, String shortName, String longName, String macAddress,
			List<Integer> portAddresses, String nodeReport, Instant lastSeen, boolean online) {

		static NodeResponse from(NodeStatus status) {
			var node = status.node();
			return new NodeResponse(node.ipAddress(), node.bindIndex(), node.shortName(), node.longName(),
					node.macAddress(), node.portAddresses(), node.nodeReport(), node.lastSeen(), status.online());
		}
	}
}
