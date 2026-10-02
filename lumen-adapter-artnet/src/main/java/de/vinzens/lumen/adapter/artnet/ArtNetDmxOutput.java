package de.vinzens.lumen.adapter.artnet;

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
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Sends DMX frames as ArtDmx packets via UDP, by unicast to a node or by broadcast. Send errors are logged
 * (rate limited) but never thrown, so a missing network does not stop the render loop.
 */
public class ArtNetDmxOutput implements DmxOutputPort, AutoCloseable {

	private static final System.Logger LOG = System.getLogger(ArtNetDmxOutput.class.getName());
	private static final Duration ERROR_LOG_INTERVAL = Duration.ofSeconds(10);

	private final ArtNetSettings settings;
	private final DatagramSocket socket;
	private final InetAddress broadcastAddress;
	private final Map<Integer, Integer> sequences = new HashMap<>();
	private final Map<String, InetAddress> resolved = new HashMap<>();
	private long lastErrorLog;

	public ArtNetDmxOutput(ArtNetSettings settings) {
		this.settings = settings;
		try {
			this.broadcastAddress = InetAddress.getByName(settings.broadcastAddress());
			this.socket = new DatagramSocket(new InetSocketAddress(InetAddress.getByName(settings.bindAddress()), 0));
			this.socket.setBroadcast(true);
		} catch (IOException e) {
			throw new UncheckedIOException("Cannot open Art-Net socket on " + settings.bindAddress(), e);
		}
	}

	@Override
	public synchronized void send(Universe universe, DmxFrame frame) {
		var address = universe.spec().artNet();
		var data = ArtDmxPacket.encode(nextSequence(address.portAddress()), address, frame);
		try {
			var destination = destination(universe.spec().target());
			socket.send(new DatagramPacket(data, data.length, destination, settings.port()));
		} catch (IOException e) {
			logError("Sending Art-Net to " + universe.spec().target().asText() + " failed: " + e.getMessage());
		}
	}

	private int nextSequence(int portAddress) {
		// 1–255, 0 would disable sequencing on the receiver
		return sequences.merge(portAddress, 1, (current, one) -> current % 255 + 1);
	}

	private InetAddress destination(ArtNetTarget target) throws UnknownHostException {
		return switch (target) {
			case ArtNetTarget.Broadcast broadcast -> broadcastAddress;
			case ArtNetTarget.Unicast unicast -> {
				var cached = resolved.get(unicast.ipAddress());
				if (cached == null) {
					cached = InetAddress.getByName(unicast.ipAddress());
					resolved.put(unicast.ipAddress(), cached);
				}
				yield cached;
			}
		};
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
