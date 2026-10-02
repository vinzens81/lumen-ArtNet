package de.vinzens.lumen.domain.library;

import de.vinzens.lumen.domain.IssueSeverity;

import java.util.Objects;
import java.util.Optional;

/**
 * Issue while loading the fixture library.
 *
 * @param source        origin, e.g. the file name
 * @param fixtureTypeId affected fixture type, if known
 */
public record LibraryIssue(IssueSeverity severity, String source, Optional<String> fixtureTypeId, String message) {

	public LibraryIssue {
		Objects.requireNonNull(severity, "severity");
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(fixtureTypeId, "fixtureTypeId");
		Objects.requireNonNull(message, "message");
	}

	public static LibraryIssue error(String source, String fixtureTypeId, String message) {
		return new LibraryIssue(IssueSeverity.ERROR, source, Optional.ofNullable(fixtureTypeId), message);
	}

	public static LibraryIssue warning(String source, String fixtureTypeId, String message) {
		return new LibraryIssue(IssueSeverity.WARNING, source, Optional.ofNullable(fixtureTypeId), message);
	}

	public boolean isError() {
		return severity == IssueSeverity.ERROR;
	}
}
