package de.vinzens.lumen.domain.project;

import java.util.regex.Pattern;

/** Dotted IPv4 addresses as typed for manual targets, e.g. {@code 10.0.0.5}. */
final class Ipv4 {

	private static final Pattern PATTERN = Pattern.compile(
			"((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)");

	private Ipv4() {
	}

	static boolean isValid(String text) {
		return PATTERN.matcher(text).matches();
	}
}
