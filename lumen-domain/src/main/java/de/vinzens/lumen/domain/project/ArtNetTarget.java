package de.vinzens.lumen.domain.project;

import de.vinzens.lumen.domain.DomainValidationException;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Where the Art-Net packets of a universe are sent: by broadcast or by unicast to a node.
 */
public sealed interface ArtNetTarget {

	String BROADCAST_TEXT = "broadcast";

	/** Text form for configuration and storage: {@code broadcast} or the IPv4 address. */
	String asText();

	static ArtNetTarget parse(String text) {
		if (text == null || text.isBlank()) {
			throw new DomainValidationException(List.of("target is missing (IPv4 address or 'broadcast')"));
		}
		var value = text.strip();
		return BROADCAST_TEXT.equalsIgnoreCase(value) ? new Broadcast() : new Unicast(value);
	}

	record Broadcast() implements ArtNetTarget {

		@Override
		public String asText() {
			return BROADCAST_TEXT;
		}
	}

	record Unicast(String ipAddress) implements ArtNetTarget {

		private static final Pattern IPV4 = Pattern.compile(
				"((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)");

		public Unicast {
			if (ipAddress == null || !IPV4.matcher(ipAddress).matches()) {
				throw new DomainValidationException(List.of(
						"target '" + ipAddress + "' is not a valid IPv4 address (or 'broadcast')"));
			}
		}

		@Override
		public String asText() {
			return ipAddress;
		}
	}
}
