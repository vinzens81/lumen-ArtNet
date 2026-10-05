package de.vinzens.lumen.application.library;

import de.vinzens.lumen.application.library.port.in.QueryFixtureLibraryUseCase;
import de.vinzens.lumen.application.library.port.out.FixtureTypeSource;
import de.vinzens.lumen.application.library.port.out.SourcedFixtureType;
import de.vinzens.lumen.domain.library.FixtureType;
import de.vinzens.lumen.domain.library.FixtureTypeValidator;
import de.vinzens.lumen.domain.library.LibraryIssue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Loads the fixture library, validates it and keeps the latest valid state in memory.
 */
public class FixtureLibraryService implements QueryFixtureLibraryUseCase {

	private static final Comparator<FixtureType> BY_NAME = Comparator
			.comparing(FixtureType::manufacturer, String.CASE_INSENSITIVE_ORDER)
			.thenComparing(FixtureType::model, String.CASE_INSENSITIVE_ORDER);

	private final FixtureTypeSource source;
	private volatile Snapshot snapshot = new Snapshot(Map.of(), Map.of(), List.of());

	public FixtureLibraryService(FixtureTypeSource source) {
		this.source = Objects.requireNonNull(source, "source");
	}

	/**
	 * Reloads the library. Fixture types with errors are skipped, warnings are only reported.
	 */
	public void reload() {
		var result = source.loadAll();
		var issues = new ArrayList<>(result.issues());
		var valid = new HashMap<String, SourcedFixtureType>();

		for (var sourced : result.fixtureTypes()) {
			var type = sourced.fixtureType();
			var typeIssues = FixtureTypeValidator.validate(type, sourced.source());
			issues.addAll(typeIssues);
			if (typeIssues.stream().anyMatch(LibraryIssue::isError)) {
				continue;
			}
			var existing = valid.putIfAbsent(type.id(), sourced);
			if (existing != null) {
				issues.add(LibraryIssue.error(sourced.source(), type.id(), "id '" + type.id()
						+ "' is already defined in " + existing.source() + ", entry ignored"));
			}
		}

		var types = new HashMap<String, FixtureType>();
		var sources = new HashMap<String, String>();
		valid.forEach((id, sourced) -> {
			types.put(id, sourced.fixtureType());
			sources.put(id, sourced.source());
		});
		snapshot = new Snapshot(Map.copyOf(types), Map.copyOf(sources), List.copyOf(issues));
	}

	@Override
	public List<FixtureType> fixtureTypes() {
		return snapshot.types().values().stream().sorted(BY_NAME).toList();
	}

	@Override
	public Optional<FixtureType> fixtureType(String id) {
		return Optional.ofNullable(snapshot.types().get(id));
	}

	@Override
	public List<LibraryIssue> issues() {
		return snapshot.issues();
	}

	/** File of a loaded fixture type, relative to the library. */
	public Optional<String> sourceOf(String id) {
		return Optional.ofNullable(snapshot.sources().get(id));
	}

	private record Snapshot(Map<String, FixtureType> types, Map<String, String> sources, List<LibraryIssue> issues) {
	}
}
