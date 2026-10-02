package de.vinzens.lumen.domain.project;

import de.vinzens.lumen.domain.DomainValidationException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Where the Art-Net packets of a universe are sent.
 *
 * <ul>
 * <li>{@link Unicast} with {@code discovered = true} ("auto", the default): to every node that reported this
 * universe in its ArtPollReply, plus optional manual IP addresses for nodes that do not answer ArtPoll.</li>
 * <li>{@link Unicast} with {@code discovered = false}: only to the manual IP addresses.</li>
 * <li>{@link Broadcast}: to everybody on the network – every node receives every universe.</li>
 * </ul>
 */
public sealed interface ArtNetTarget {

	String BROADCAST_TEXT = "broadcast";
	String AUTO_TEXT = "auto";

	/** Text form for storage: {@code broadcast}, {@code auto}, {@code auto,10.0.0.5} or {@code 10.0.0.5,10.0.0.6}. */
	String asText();

	static ArtNetTarget auto() {
		return new Unicast(true, List.of());
	}

	static ArtNetTarget parse(String text) {
		if (text == null || text.isBlank()) {
			throw new DomainValidationException(List.of("target is missing ('auto', 'broadcast' or IPv4 addresses)"));
		}
		var tokens = Arrays.stream(text.split(",")).map(String::strip).filter(t -> !t.isEmpty()).toList();
		if (tokens.size() == 1 && BROADCAST_TEXT.equalsIgnoreCase(tokens.getFirst())) {
			return new Broadcast();
		}
		var discovered = tokens.stream().anyMatch(AUTO_TEXT::equalsIgnoreCase);
		var ips = tokens.stream().filter(t -> !AUTO_TEXT.equalsIgnoreCase(t)).toList();
		return new Unicast(discovered, ips);
	}

	record Broadcast() implements ArtNetTarget {

		@Override
		public String asText() {
			return BROADCAST_TEXT;
		}
	}

	/**
	 * @param discovered  also send to all nodes found via ArtPoll that output this universe
	 * @param ipAddresses manual IPv4 addresses
	 */
	record Unicast(boolean discovered, List<String> ipAddresses) implements ArtNetTarget {

		private static final Pattern IPV4 = Pattern.compile(
				"((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)");

		public Unicast {
			Objects.requireNonNull(ipAddresses, "ipAddresses");
			var errors = new ArrayList<String>();
			ipAddresses = List.copyOf(new LinkedHashSet<>(ipAddresses.stream().map(String::strip).toList()));
			for (var ip : ipAddresses) {
				if (!IPV4.matcher(ip).matches()) {
					errors.add("'" + ip + "' is not a valid IPv4 address");
				}
			}
			if (!discovered && ipAddresses.isEmpty()) {
				errors.add("unicast needs at least one IP address (or use auto/broadcast)");
			}
			if (!errors.isEmpty()) {
				throw new DomainValidationException(errors);
			}
		}

		@Override
		public String asText() {
			var parts = new ArrayList<String>();
			if (discovered) {
				parts.add(AUTO_TEXT);
			}
			parts.addAll(ipAddresses);
			return String.join(",", parts);
		}
	}
}
