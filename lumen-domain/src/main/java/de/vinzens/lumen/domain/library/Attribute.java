package de.vinzens.lumen.domain.library;

import de.vinzens.lumen.domain.DomainValidationException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A controllable attribute of a fixture, e.g. {@code dimmer} or {@code red}. Generic channels are distinguished
 * by their name ({@code generic:Program speed}). The {@link #id()} is used in the API and for storing scenes.
 */
public record Attribute(ChannelFunction function, Optional<String> name) {

	private static final String GENERIC_PREFIX = ChannelFunction.GENERIC.id() + ":";

	public Attribute {
		Objects.requireNonNull(function, "function");
		Objects.requireNonNull(name, "name");
		if (function == ChannelFunction.GENERIC && name.isEmpty()) {
			throw new IllegalArgumentException("generic attributes need a name");
		}
		if (function != ChannelFunction.GENERIC) {
			name = Optional.empty();
		}
	}

	public static Attribute of(ChannelFunction function) {
		return new Attribute(function, Optional.empty());
	}

	public static Attribute generic(String name) {
		return new Attribute(ChannelFunction.GENERIC, Optional.of(name));
	}

	public static Attribute parse(String id) {
		if (id != null && id.startsWith(GENERIC_PREFIX) && id.length() > GENERIC_PREFIX.length()) {
			return generic(id.substring(GENERIC_PREFIX.length()));
		}
		return ChannelFunction.fromId(id)
				.filter(function -> function != ChannelFunction.GENERIC)
				.map(Attribute::of)
				.orElseThrow(() -> new DomainValidationException(List.of("Unknown attribute '" + id + "'")));
	}

	public String id() {
		return name.map(n -> GENERIC_PREFIX + n).orElse(function.id());
	}

	@Override
	public String toString() {
		return id();
	}
}
