package de.vinzens.lumen.domain.library;

import java.util.Objects;
import java.util.Optional;

/**
 * Problem beim Laden der Gerätebibliothek.
 *
 * @param source        Herkunft, z.B. der Dateiname
 * @param fixtureTypeId betroffener Gerätetyp, falls bekannt
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
