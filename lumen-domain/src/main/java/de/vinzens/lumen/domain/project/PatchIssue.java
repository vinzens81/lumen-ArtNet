package de.vinzens.lumen.domain.project;

import de.vinzens.lumen.domain.IssueSeverity;

import java.util.List;
import java.util.Objects;

/**
 * Issue in the patch, e.g. an address overlap.
 *
 * @param fixtureIds  affected fixtures (both in case of an overlap)
 * @param universeIds affected universes
 */
public record PatchIssue(IssueSeverity severity, List<Long> fixtureIds, List<Long> universeIds, String message) {

	public PatchIssue {
		Objects.requireNonNull(severity, "severity");
		Objects.requireNonNull(message, "message");
		fixtureIds = List.copyOf(fixtureIds);
		universeIds = List.copyOf(universeIds);
	}

	static PatchIssue fixtureError(String message, Long... fixtureIds) {
		return new PatchIssue(IssueSeverity.ERROR, List.of(fixtureIds), List.of(), message);
	}

	static PatchIssue universeWarning(String message, Long... universeIds) {
		return new PatchIssue(IssueSeverity.WARNING, List.of(), List.of(universeIds), message);
	}
}
