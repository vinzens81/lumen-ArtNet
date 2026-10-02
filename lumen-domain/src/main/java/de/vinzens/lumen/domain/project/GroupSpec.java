package de.vinzens.lumen.domain.project;

import de.vinzens.lumen.domain.Validation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Bearbeitbare Eigenschaften einer Gruppe.
 *
 * @param color      Anzeigefarbe im Format {@code #rrggbb}
 * @param fixtureIds Mitglieder in Anzeigereihenfolge
 */
public record GroupSpec(String name, Optional<String> color, int sortOrder, List<Long> fixtureIds) {

	private static final Pattern COLOR_PATTERN = Pattern.compile("#[0-9a-fA-F]{6}");

	public GroupSpec {
		Objects.requireNonNull(color, "color");
		Objects.requireNonNull(fixtureIds, "fixtureIds");
		new Validation()
				.requireText(name, "name")
				.require(color.map(c -> COLOR_PATTERN.matcher(c).matches()).orElse(true),
						"color muss das Format #rrggbb haben")
				.require(fixtureIds.stream().distinct().count() == fixtureIds.size(),
						"fixtureIds enthält doppelte Einträge")
				.throwIfInvalid();
		name = name.strip();
		fixtureIds = List.copyOf(fixtureIds);
	}
}
