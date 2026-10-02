package de.vinzens.lumen.domain.project;

import de.vinzens.lumen.domain.Validation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Editable properties of a group.
 *
 * @param color      display color in the format {@code #rrggbb}
 * @param fixtureIds members in display order
 */
public record GroupSpec(String name, Optional<String> color, int sortOrder, List<Long> fixtureIds) {

	private static final Pattern COLOR_PATTERN = Pattern.compile("#[0-9a-fA-F]{6}");

	public GroupSpec {
		Objects.requireNonNull(color, "color");
		Objects.requireNonNull(fixtureIds, "fixtureIds");
		new Validation()
				.requireText(name, "name")
				.require(color.map(c -> COLOR_PATTERN.matcher(c).matches()).orElse(true),
						"color must have the format #rrggbb")
				.require(fixtureIds.stream().distinct().count() == fixtureIds.size(),
						"fixtureIds contains duplicates")
				.throwIfInvalid();
		name = name.strip();
		fixtureIds = List.copyOf(fixtureIds);
	}
}
