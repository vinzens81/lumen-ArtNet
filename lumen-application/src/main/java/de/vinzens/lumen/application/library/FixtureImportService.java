package de.vinzens.lumen.application.library;

import de.vinzens.lumen.application.ConflictException;
import de.vinzens.lumen.application.library.port.in.ImportFixtureTypeUseCase;
import de.vinzens.lumen.application.library.port.in.ReloadFixtureLibraryUseCase;
import de.vinzens.lumen.application.library.port.out.FixtureTypeFiles;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.library.FixtureTypeValidator;
import de.vinzens.lumen.domain.library.LibraryIssue;

import java.time.Clock;
import java.time.LocalDate;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;

public class FixtureImportService implements ImportFixtureTypeUseCase {

	private final FixtureTypeFiles files;
	private final FixtureLibraryService library;
	private final ReloadFixtureLibraryUseCase reloader;
	private final Clock clock;

	public FixtureImportService(FixtureTypeFiles files, FixtureLibraryService library,
			ReloadFixtureLibraryUseCase reloader, Clock clock) {
		this.files = Objects.requireNonNull(files, "files");
		this.library = Objects.requireNonNull(library, "library");
		this.reloader = Objects.requireNonNull(reloader, "reloader");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Override
	public ImportResult importOfl(String json, String manufacturer, boolean replace) {
		var converted = files.convertOfl(json, manufacturer);
		var type = converted.fixtureType();
		var issues = FixtureTypeValidator.validate(type, "import");
		var errors = issues.stream().filter(LibraryIssue::isError).map(LibraryIssue::message).toList();
		if (!errors.isEmpty()) {
			throw new DomainValidationException(errors);
		}
		var source = library.sourceOf(type.id())
				.orElse(folder(type.manufacturer()) + "/" + type.id() + ".yml");
		if (!replace && (library.fixtureType(type.id()).isPresent() || files.exists(source))) {
			throw new ConflictException("The library already has a profile with id '" + type.id() + "' (" + source
					+ ") – replace it or rename the fixture");
		}
		files.save(type, source, "Imported from the Open Fixture Library on " + LocalDate.now(clock)
				+ "\nhttps://open-fixture-library.org – check the channels before using it on stage");
		reloader.reload();
		var warnings = new ArrayList<>(converted.warnings());
		issues.stream().map(LibraryIssue::message).forEach(warnings::add);
		return new ImportResult(type, source, warnings);
	}

	/** Folder of a manufacturer in the library: "Robe Lighting" → "robe-lighting". */
	private static String folder(String manufacturer) {
		var ascii = Normalizer.normalize(manufacturer, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		var folder = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
		return folder.isEmpty() ? "imported" : folder;
	}
}
