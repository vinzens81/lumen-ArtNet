package de.vinzens.lumen.domain.library;

import de.vinzens.lumen.domain.DomainValidationException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A controllable attribute of a fixture, e.g. {@code dimmer} or {@code red}. Generic channels are distinguished
 * by their name ({@code generic:Program speed}). The {@link #id()} is used in the API and for storing scenes.
 *
 * <p>Multi-pixel fixtures (a LED bar with 6 segments) have attributes per head: {@code red@3} is the red of head 3.
 * The attribute without head ({@code red}) sets all heads that have no value of their own.
 *
 * @param head 0 = the whole fixture, 1… = one head (segment, pixel) of a multi-pixel fixture
 */
public record Attribute(ChannelFunction function, Optional<String> name, int head) {

	private static final String GENERIC_PREFIX = ChannelFunction.GENERIC.id() + ":";
	private static final Pattern HEAD_SUFFIX = Pattern.compile("^(.+)@([1-9][0-9]{0,2})$");

	public Attribute {
		Objects.requireNonNull(function, "function");
		Objects.requireNonNull(name, "name");
		if (function == ChannelFunction.GENERIC && name.isEmpty()) {
			throw new IllegalArgumentException("generic attributes need a name");
		}
		if (function != ChannelFunction.GENERIC) {
			name = Optional.empty();
		}
		if (head < 0) {
			throw new IllegalArgumentException("head must not be negative");
		}
	}

	public Attribute(ChannelFunction function, Optional<String> name) {
		this(function, name, 0);
	}

	public static Attribute of(ChannelFunction function) {
		return new Attribute(function, Optional.empty());
	}

	public static Attribute generic(String name) {
		return new Attribute(ChannelFunction.GENERIC, Optional.of(name));
	}

	public static Attribute parse(String id) {
		var headMatch = id == null ? null : HEAD_SUFFIX.matcher(id);
		if (headMatch != null && headMatch.matches()) {
			return parse(headMatch.group(1)).withHead(Integer.parseInt(headMatch.group(2)));
		}
		if (id != null && id.startsWith(GENERIC_PREFIX) && id.length() > GENERIC_PREFIX.length()) {
			return generic(id.substring(GENERIC_PREFIX.length()));
		}
		return ChannelFunction.fromId(id)
				.filter(function -> function != ChannelFunction.GENERIC)
				.map(Attribute::of)
				.orElseThrow(() -> new DomainValidationException(List.of("Unknown attribute '" + id + "'")));
	}

	public String id() {
		var base = name.map(n -> GENERIC_PREFIX + n).orElse(function.id());
		return head == 0 ? base : base + "@" + head;
	}

	/** The same attribute for one head (0 = the whole fixture). */
	public Attribute withHead(int newHead) {
		return new Attribute(function, name, newHead);
	}

	/** The attribute of the whole fixture, e.g. {@code red} for {@code red@3}. */
	public Attribute withoutHead() {
		return head == 0 ? this : withHead(0);
	}

	@Override
	public String toString() {
		return id();
	}
}
