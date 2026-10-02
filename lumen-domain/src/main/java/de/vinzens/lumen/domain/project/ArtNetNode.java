package de.vinzens.lumen.domain.project;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * An Art-Net node found on the network via ArtPoll/ArtPollReply.
 *
 * @param portAddresses universes (15-bit port addresses) the node outputs as DMX
 * @param nodeReport    free-text status reported by the node
 * @param lastSeen      time of the last ArtPollReply
 */
public record ArtNetNode(String ipAddress, int bindIndex, String shortName, String longName, String macAddress,
		List<Integer> portAddresses, String nodeReport, Instant lastSeen) {

	/** A node that has not answered for this long is considered offline. */
	public static final Duration ONLINE_TIMEOUT = Duration.ofSeconds(10);

	public ArtNetNode {
		Objects.requireNonNull(ipAddress, "ipAddress");
		Objects.requireNonNull(shortName, "shortName");
		Objects.requireNonNull(longName, "longName");
		Objects.requireNonNull(macAddress, "macAddress");
		Objects.requireNonNull(nodeReport, "nodeReport");
		Objects.requireNonNull(lastSeen, "lastSeen");
		portAddresses = List.copyOf(portAddresses);
	}

	public boolean isOnline(Instant now) {
		return Duration.between(lastSeen, now).compareTo(ONLINE_TIMEOUT) < 0;
	}

	public boolean outputs(int portAddress) {
		return portAddresses.contains(portAddress);
	}

	/** Same node and same reported configuration, ignoring when it was seen. */
	public boolean sameAs(ArtNetNode other) {
		return ipAddress.equals(other.ipAddress) && bindIndex == other.bindIndex && shortName.equals(other.shortName)
				&& longName.equals(other.longName) && portAddresses.equals(other.portAddresses)
				&& nodeReport.equals(other.nodeReport);
	}
}
