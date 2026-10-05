package de.vinzens.lumen.domain.project;

import de.vinzens.lumen.domain.Validation;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * An IPv4 network of a network interface of this computer, e.g. the Wi-Fi ({@code en0}, 192.168.0.23/24) or a USB
 * Ethernet adapter on the lighting network ({@code en7}, 2.0.0.10/8). A universe can be bound to one of them.
 *
 * @param interfaceName name of the interface, e.g. {@code en0}; stable while the adapter stays plugged in
 * @param address       IPv4 address of this computer in the network
 * @param prefixLength  network prefix, e.g. 24 for 255.255.255.0
 */
public record LocalNetwork(String interfaceName, String address, int prefixLength) {

	private static final Pattern IPV4 = Pattern.compile(
			"((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)");

	public LocalNetwork {
		Objects.requireNonNull(interfaceName, "interfaceName");
		Objects.requireNonNull(address, "address");
		new Validation()
				.requireText(interfaceName, "interfaceName")
				.require(IPV4.matcher(address).matches(), "'" + address + "' is not a valid IPv4 address")
				.require(prefixLength >= 0 && prefixLength <= 32, "prefixLength must be between 0 and 32")
				.throwIfInvalid();
	}

	/** Directed broadcast of the network, e.g. 192.168.0.255 for 192.168.0.23/24 – it leaves via this interface. */
	public String broadcastAddress() {
		return format(toInt(address) | ~mask());
	}

	/** Whether the IPv4 address is in this network, i.e. reached directly via this interface. */
	public boolean contains(String ip) {
		return parse(ip).map(value -> (value & mask()) == (toInt(address) & mask())).orElse(false);
	}

	private int mask() {
		return prefixLength == 0 ? 0 : -1 << (32 - prefixLength);
	}

	private static Optional<Integer> parse(String ip) {
		return ip != null && IPV4.matcher(ip).matches() ? Optional.of(toInt(ip)) : Optional.empty();
	}

	private static int toInt(String ip) {
		var value = 0;
		for (var part : ip.split("\\.")) {
			value = value << 8 | Integer.parseInt(part);
		}
		return value;
	}

	private static String format(int value) {
		return (value >>> 24) + "." + (value >>> 16 & 0xff) + "." + (value >>> 8 & 0xff) + "." + (value & 0xff);
	}
}
