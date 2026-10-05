package de.vinzens.lumen.application.output.port.in;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The Art-Net nodes the active project needs, and whether they answer.
 */
public interface WatchNodesUseCase {

	/** Result of the last check, see {@link NodeCheck}. */
	List<NodeCheck> nodes();

	/** Stops expecting a node, e.g. one that was removed from the rig. It is learned again when it answers. */
	void forget(String ipAddress);

	enum NodeState {
		/** answers ArtPoll */
		ONLINE,
		/** answered before, but not any more */
		OFFLINE,
		/** a manual IP address that never answered ArtPoll – whether it works cannot be checked */
		UNCHECKED
	}

	/**
	 * @param name      short name the node reported, empty for manual addresses
	 * @param universes numbers of the project's universes the node receives, as far as known
	 * @param lastSeen  last ArtPollReply, if still known
	 */
	record NodeCheck(String ipAddress, String name, NodeState state, List<Integer> universes,
			Optional<Instant> lastSeen) {

		public NodeCheck {
			universes = List.copyOf(universes);
		}
	}
}
