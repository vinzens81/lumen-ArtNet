package de.vinzens.lumen.adapter.artnet;

import de.vinzens.lumen.application.output.port.out.LocalNetworks;
import de.vinzens.lumen.domain.project.LocalNetwork;

import java.lang.System.Logger.Level;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The IPv4 networks of this computer's interfaces that are up – without loopback, VPN tunnels and other
 * point-to-point links. Read again at most every {@link #REFRESH}, so a plugged-in USB adapter shows up within
 * seconds while the render loop can ask every frame.
 */
public class SystemNetworks implements LocalNetworks {

	static final Duration REFRESH = Duration.ofSeconds(3);

	private static final System.Logger LOG = System.getLogger(SystemNetworks.class.getName());

	private final Clock clock;
	private List<LocalNetwork> networks = List.of();
	private Instant scanned;

	public SystemNetworks(Clock clock) {
		this.clock = clock;
	}

	@Override
	public synchronized List<LocalNetwork> networks() {
		var now = clock.instant();
		if (scanned == null || Duration.between(scanned, now).compareTo(REFRESH) >= 0) {
			networks = scan();
			scanned = now;
		}
		return networks;
	}

	private static List<LocalNetwork> scan() {
		var result = new ArrayList<LocalNetwork>();
		try {
			for (var nic : NetworkInterface.networkInterfaces().toList()) {
				if (!nic.isUp() || nic.isLoopback() || nic.isPointToPoint() || nic.isVirtual()) {
					continue;
				}
				for (var address : nic.getInterfaceAddresses()) {
					if (address.getAddress() instanceof Inet4Address ipv4) {
						result.add(new LocalNetwork(nic.getName(), ipv4.getHostAddress(),
								address.getNetworkPrefixLength()));
					}
				}
			}
		} catch (SocketException | RuntimeException e) {
			LOG.log(Level.WARNING, "Cannot read the network interfaces: " + e.getMessage());
		}
		return List.copyOf(result);
	}
}
