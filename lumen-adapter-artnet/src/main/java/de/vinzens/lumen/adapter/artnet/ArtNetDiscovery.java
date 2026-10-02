package de.vinzens.lumen.adapter.artnet;

import de.vinzens.lumen.application.output.port.out.ArtNetNodeDirectory;
import de.vinzens.lumen.domain.project.ArtNetNode;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Finds Art-Net nodes: sends ArtPoll periodically and collects the ArtPollReply packets. Nodes answer to UDP
 * port 6454 of the polling controller, so this listens there (shared with other Art-Net software on the same
 * machine via SO_REUSEADDR/SO_REUSEPORT). If the port cannot be opened, discovery stays empty and the rest of
 * Lumen keeps working.
 */
public class ArtNetDiscovery implements ArtNetNodeDirectory, AutoCloseable {

	private static final System.Logger LOG = System.getLogger(ArtNetDiscovery.class.getName());
	/** Offline nodes are forgotten after this time. */
	private static final Duration FORGET_AFTER = Duration.ofMinutes(5);

	private final ArtNetSettings settings;
	private final Clock clock;
	private final Runnable onChange;
	private final Map<String, ArtNetNode> nodes = new ConcurrentHashMap<>();
	private final Set<String> online = ConcurrentHashMap.newKeySet();
	private DatagramChannel channel;
	private ScheduledExecutorService poller;
	private Thread receiver;

	/**
	 * @param onChange called when a node appears, changes or goes offline
	 */
	public ArtNetDiscovery(ArtNetSettings settings, Clock clock, Runnable onChange) {
		this.settings = settings;
		this.clock = clock;
		this.onChange = onChange;
	}

	public synchronized void start() {
		if (channel != null) {
			return;
		}
		try {
			channel = DatagramChannel.open(StandardProtocolFamily.INET);
			channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
			if (channel.supportedOptions().contains(StandardSocketOptions.SO_REUSEPORT)) {
				channel.setOption(StandardSocketOptions.SO_REUSEPORT, true);
			}
			channel.setOption(StandardSocketOptions.SO_BROADCAST, true);
			channel.bind(new InetSocketAddress(InetAddress.getByName(settings.bindAddress()), settings.listenPort()));
		} catch (IOException e) {
			LOG.log(Level.WARNING, "Art-Net discovery disabled, cannot listen on port " + settings.listenPort()
					+ ": " + e.getMessage());
			closeQuietly();
			return;
		}
		receiver = Thread.ofPlatform().daemon().name("lumen-artnet-receive").start(this::receiveLoop);
		poller = Executors.newSingleThreadScheduledExecutor(runnable -> Thread.ofPlatform().daemon()
				.name("lumen-artnet-poll").unstarted(runnable));
		poller.scheduleWithFixedDelay(this::pollTick, 0, settings.pollInterval().toMillis(), TimeUnit.MILLISECONDS);
	}

	@Override
	public List<ArtNetNode> nodes() {
		return List.copyOf(nodes.values());
	}

	private void pollTick() {
		try {
			var target = new InetSocketAddress(InetAddress.getByName(settings.broadcastAddress()), settings.port());
			channel.send(ByteBuffer.wrap(ArtPollPackets.poll()), target);
		} catch (IOException e) {
			LOG.log(Level.DEBUG, "Sending ArtPoll failed: " + e.getMessage());
		} catch (RuntimeException e) {
			LOG.log(Level.WARNING, "Sending ArtPoll failed", e);
		}
		expire();
	}

	/** Marks nodes offline that stopped answering and forgets long gone ones. */
	private void expire() {
		var now = clock.instant();
		var changed = false;
		var stillOnline = new HashSet<String>();
		for (var entry : nodes.entrySet()) {
			var node = entry.getValue();
			if (node.isOnline(now)) {
				stillOnline.add(entry.getKey());
			} else if (now.isAfter(node.lastSeen().plus(FORGET_AFTER))) {
				nodes.remove(entry.getKey());
				changed = true;
			}
		}
		changed |= online.retainAll(stillOnline);
		if (changed) {
			notifyChange();
		}
	}

	private void receiveLoop() {
		var buffer = ByteBuffer.allocate(1024);
		while (!Thread.currentThread().isInterrupted()) {
			try {
				buffer.clear();
				SocketAddress from = channel.receive(buffer);
				if (from instanceof InetSocketAddress sender) {
					ArtPollPackets.parseReply(buffer.array(), buffer.position(), sender.getAddress(), clock.instant())
							.ifPresent(this::register);
				}
			} catch (ClosedChannelException e) {
				return;
			} catch (IOException | RuntimeException e) {
				LOG.log(Level.DEBUG, "Ignoring Art-Net packet: " + e.getMessage());
			}
		}
	}

	private void register(ArtNetNode node) {
		var key = node.ipAddress() + "#" + node.bindIndex();
		var previous = nodes.put(key, node);
		var cameOnline = online.add(key);
		if (previous == null || cameOnline || !previous.sameAs(node)) {
			if (previous == null) {
				LOG.log(Level.INFO, "Found Art-Net node " + node.shortName() + " at " + node.ipAddress()
						+ ", universes " + node.portAddresses());
			}
			notifyChange();
		}
	}

	private void notifyChange() {
		try {
			onChange.run();
		} catch (RuntimeException e) {
			LOG.log(Level.WARNING, "Node change listener failed", e);
		}
	}

	@Override
	public synchronized void close() {
		if (poller != null) {
			poller.shutdownNow();
		}
		if (receiver != null) {
			receiver.interrupt();
		}
		closeQuietly();
	}

	private void closeQuietly() {
		if (channel != null) {
			try {
				channel.close();
			} catch (IOException ignored) {
				// closing anyway
			}
			channel = null;
		}
	}
}
