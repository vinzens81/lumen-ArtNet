package de.vinzens.lumen.adapter.sacn;

import de.vinzens.lumen.application.output.port.out.DmxOutputPort;
import de.vinzens.lumen.application.output.port.out.LocalNetworks;
import de.vinzens.lumen.domain.output.DmxFrame;
import de.vinzens.lumen.domain.project.LocalNetwork;
import de.vinzens.lumen.domain.project.SacnOutput;
import de.vinzens.lumen.domain.project.Universe;
import de.vinzens.lumen.domain.project.UniverseSpec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sends DMX frames as sACN (E1.31) data packets via UDP: by multicast to the group of the universe, or by unicast to
 * manual addresses. Multicast goes out on the network interface of the universe – or not at all while it is missing –
 * and for universes on all networks on every interface with an IPv4 network: the operating system would otherwise
 * pick only the one of its default route, which a lighting network without a router is not. Every few seconds the universes sent are announced by universe discovery; on
 * close the receivers are told that the streams end. Send errors are logged (rate limited) but never thrown.
 */
public class SacnDmxOutput implements DmxOutputPort, AutoCloseable {

	private static final System.Logger LOG = System.getLogger(SacnDmxOutput.class.getName());
	private static final Duration ERROR_LOG_INTERVAL = Duration.ofSeconds(10);
	/** a universe not sent for this long is no longer announced or terminated (receivers drop it after 2.5 s) */
	private static final Duration STREAM_TIMEOUT = Duration.ofMillis(2_500);
	/** E1.31 6.2.6: a terminated stream is announced three times */
	private static final int TERMINATE_PACKETS = 3;
	/** key of the socket that leaves the interface choice to the operating system */
	private static final String ANY_INTERFACE = "";

	private final SacnSettings settings;
	private final LocalNetworks networks;
	private final Clock clock;
	private final InetAddress bindAddress;
	/** by interface name, {@link #ANY_INTERFACE} for universes on all networks */
	private final Map<String, MulticastSocket> sockets = new HashMap<>();
	private final Map<String, InetAddress> resolved = new HashMap<>();
	/** by sACN universe */
	private final Map<Integer, Stream> streams = new TreeMap<>();
	private final AtomicLong packetsSent = new AtomicLong();
	private final AtomicLong sendErrors = new AtomicLong();
	private Instant nextDiscovery = Instant.MIN;
	private long lastErrorLog;

	public SacnDmxOutput(SacnSettings settings, LocalNetworks networks, Clock clock) {
		this.settings = settings;
		this.networks = networks;
		this.clock = clock;
		try {
			this.bindAddress = InetAddress.getByName(settings.bindAddress());
			socket(ANY_INTERFACE); // fail early if the address cannot be bound
		} catch (IOException e) {
			throw new UncheckedIOException("Cannot open sACN socket on " + settings.bindAddress(), e);
		}
	}

	@Override
	public synchronized void send(Universe universe, DmxFrame frame) {
		var spec = universe.spec();
		if (!spec.protocol().sendsSacn()) {
			return;
		}
		var networkInterface = networkInterface(spec);
		if (networkInterface.isEmpty()) {
			return;
		}
		var sacn = spec.sacn();
		var now = clock.instant();
		var stream = streams.compute(sacn.universe(), (number, previous) -> new Stream(sacn,
				networkInterface.get(), frame, previous == null ? 0 : (previous.sequence() + 1) & 0xFF, now));
		transmit(stream, 0);
		if (!now.isBefore(nextDiscovery)) {
			nextDiscovery = now.plus(settings.discoveryInterval());
			announce(now);
		}
	}

	@Override
	public Counters counters() {
		return new Counters(true, packetsSent.get(), sendErrors.get());
	}

	/**
	 * The interface to send on, {@link #ANY_INTERFACE} for all networks; empty if the universe's interface is missing,
	 * e.g. the USB adapter was unplugged – the packets would otherwise end up in another network.
	 */
	private Optional<String> networkInterface(UniverseSpec spec) {
		if (spec.networkInterface().isEmpty()) {
			return Optional.of(ANY_INTERFACE);
		}
		var name = spec.networkInterface().get();
		if (networks.networks().stream().noneMatch(n -> n.interfaceName().equals(name))) {
			logError("Universe " + spec.number() + " is not sent by sACN: network interface " + name
					+ " is not available");
			return Optional.empty();
		}
		return Optional.of(name);
	}

	private void transmit(Stream stream, int options) {
		var sacn = stream.sacn();
		var data = E131Packet.data(settings.cid(), settings.sourceName(), sacn.priority(), stream.sequence(), options,
				sacn.universe(), stream.frame());
		if (!sacn.multicast()) {
			// unicast is routed by the operating system
			sacn.ipAddresses().forEach(ip -> sendPacket(ANY_INTERFACE, ip, data));
			return;
		}
		for (var networkInterface : multicastInterfaces(stream.networkInterface())) {
			sendPacket(networkInterface, sacn.multicastAddress(), data);
		}
	}

	/** Universe discovery: per interface, the universes sent there recently. */
	private void announce(Instant now) {
		var byInterface = new TreeMap<String, List<Integer>>();
		streams.values().stream().filter(stream -> stream.isRecent(now)).forEach(stream -> {
			for (var networkInterface : multicastInterfaces(stream.networkInterface())) {
				byInterface.computeIfAbsent(networkInterface, key -> new ArrayList<>()).add(stream.sacn().universe());
			}
		});
		byInterface.forEach((networkInterface, universes) -> {
			for (var packet : E131Packet.discovery(settings.cid(), settings.sourceName(), universes)) {
				sendPacket(networkInterface, E131Packet.DISCOVERY_ADDRESS, packet);
			}
		});
	}

	/** The interface of a universe, or for one on all networks every interface with an IPv4 network (if any). */
	private List<String> multicastInterfaces(String networkInterface) {
		if (!networkInterface.equals(ANY_INTERFACE)) {
			return List.of(networkInterface);
		}
		var all = networks.networks().stream().map(LocalNetwork::interfaceName).distinct().toList();
		return all.isEmpty() ? List.of(ANY_INTERFACE) : all;
	}

	private void sendPacket(String networkInterface, String destination, byte[] data) {
		try {
			socket(networkInterface)
					.send(new DatagramPacket(data, data.length, resolve(destination), settings.port()));
			packetsSent.incrementAndGet();
		} catch (IOException e) {
			sendErrors.incrementAndGet();
			dropSocket(networkInterface); // e.g. the adapter was unplugged and is back with a new index
			logError("Sending sACN to " + destination
					+ (networkInterface.isEmpty() ? "" : " on " + networkInterface) + " failed: " + e.getMessage());
		}
	}

	private MulticastSocket socket(String networkInterface) throws IOException {
		var socket = sockets.get(networkInterface);
		if (socket == null) {
			socket = new MulticastSocket(new InetSocketAddress(bindAddress, 0));
			if (!networkInterface.equals(ANY_INTERFACE)) {
				var nif = NetworkInterface.getByName(networkInterface);
				if (nif == null) {
					socket.close();
					throw new SocketException("network interface " + networkInterface + " not found");
				}
				socket.setNetworkInterface(nif);
			}
			sockets.put(networkInterface, socket);
		}
		return socket;
	}

	private void dropSocket(String networkInterface) {
		if (!networkInterface.equals(ANY_INTERFACE)) {
			var socket = sockets.remove(networkInterface);
			if (socket != null) {
				socket.close();
			}
		}
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

	/** Tells the receivers of the universes sent recently that this source stops, then closes the sockets. */
	@Override
	public synchronized void close() {
		var now = clock.instant();
		for (var stream : streams.values()) {
			if (!stream.isRecent(now)) {
				continue;
			}
			var next = stream;
			for (int i = 0; i < TERMINATE_PACKETS; i++) {
				next = next.next();
				transmit(next, E131Packet.OPTION_TERMINATED);
			}
		}
		streams.clear();
		sockets.values().forEach(MulticastSocket::close);
		sockets.clear();
	}

	/** The last packet sent for an sACN universe. */
	private record Stream(SacnOutput sacn, String networkInterface, DmxFrame frame, int sequence, Instant sentAt) {

		boolean isRecent(Instant now) {
			return Duration.between(sentAt, now).compareTo(STREAM_TIMEOUT) < 0;
		}

		Stream next() {
			return new Stream(sacn, networkInterface, frame, (sequence + 1) & 0xFF, sentAt);
		}
	}
}
