package de.vinzens.lumen.adapter.web.library;

import de.vinzens.lumen.domain.library.LibraryIssue;

import java.util.Locale;

record LibraryIssueDto(String severity, String source, String fixtureTypeId, String message) {

	static LibraryIssueDto from(LibraryIssue issue) {
		return new LibraryIssueDto(
				issue.severity().name().toLowerCase(Locale.ROOT),
				issue.source(),
				issue.fixtureTypeId().orElse(null),
				issue.message());
	}
}
