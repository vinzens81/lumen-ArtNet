package de.vinzens.lumen.adapter.artnet;

import de.vinzens.lumen.application.output.port.out.ArtNetNodeDirectory;
import de.vinzens.lumen.application.output.port.out.DmxOutputPort;
import de.vinzens.lumen.domain.output.DmxFrame;
import de.vinzens.lumen.domain.project.ArtNetTarget;
import de.vinzens.lumen.domain.project.Universe;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Sends DMX frames as ArtDmx packets via UDP: by unicast to discovered and/or manually configured nodes, or by
 * broadcast. Send errors are logged (rate limited) but never thrown, so a missing network does not stop the
 * render loop.
 */
public class ArtNetDmxOutput implements DmxOutputPort, AutoCloseable {

	private static final System.Logger LOG = System.getLogger(ArtNetDmxOutput.class.getName());
	private static final Duration ERROR_LOG_INTERVAL = Duration.ofSeconds(10);

	private final ArtNetSettings settings;
	private final ArtNetNodeDirectory nodes;
	private final Clock clock;
	private final DatagramSocket socket;
	private final Map<Integer, Integer> sequences = new HashMap<>();
	private final Map<String, InetAddress> resolved = new HashMap<>();
	private long lastErrorLog;

	public ArtNetDmxOutput(ArtNetSettings settings) {
		this(settings, ArtNetNodeDirectory.NONE, Clock.systemUTC());
	}

	public ArtNetDmxOutput(ArtNetSettings settings, ArtNetNodeDirectory nodes, Clock clock) {
		this.settings = settings;
		this.nodes = nodes;
		this.clock = clock;
		try {
			this.socket = new DatagramSocket(new InetSocketAddress(InetAddress.getByName(settings.bindAddress()), 0));
			this.socket.setBroadcast(true);
		} catch (IOException e) {
			throw new UncheckedIOException("Cannot open Art-Net socket on " + settings.bindAddress(), e);
		}
	}

	@Override
	public synchronized void send(Universe universe, DmxFrame frame) {
		var address = universe.spec().artNet();
		var destinations = destinations(universe.spec().target(), address.portAddress());
		if (destinations.isEmpty()) {
			return;
		}
		var data = ArtDmxPacket.encode(nextSequence(address.portAddress()), address, frame);
		for (var destination : destinations) {
			try {
				socket.send(new DatagramPacket(data, data.length, resolve(destination), settings.port()));
			} catch (IOException e) {
				logError("Sending Art-Net to " + destination + " failed: " + e.getMessage());
			}
		}
	}

	/** IP addresses (or the broadcast address) a universe is sent to. */
	Set<String> destinations(ArtNetTarget target, int portAddress) {
		return switch (target) {
			case ArtNetTarget.Broadcast broadcast -> Set.of(settings.broadcastAddress());
			case ArtNetTarget.Unicast unicast -> {
				var result = new LinkedHashSet<String>();
				if (unicast.discovered()) {
					var now = clock.instant();
					nodes.nodes().stream()
							.filter(node -> node.isOnline(now) && node.outputs(portAddress))
							.forEach(node -> result.add(node.ipAddress()));
				}
				result.addAll(unicast.ipAddresses());
				yield result;
			}
		};
	}

	private int nextSequence(int portAddress) {
		// 1–255, 0 would disable sequencing on the receiver
		return sequences.merge(portAddress, 1, (current, one) -> current % 255 + 1);
	}

	private InetAddress resolve(String ip) throws UnknownHostException {
		var cached = resolved.get(ip);
		if (cached == null) {
			cached = InetAddress.getByName(ip);
			resolved.put(ip, cached);
		}
		return cached;
	}

	private void logError(String message) {
		var now = System.nanoTime();
		if (now - lastErrorLog > ERROR_LOG_INTERVAL.toNanos()) {
			lastErrorLog = now;
			LOG.log(Level.WARNING, message);
		}
	}

	@Override
	public void close() {
		socket.close();
	}
}
