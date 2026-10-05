package de.vinzens.lumen.adapter.web.library;

import de.vinzens.lumen.application.library.port.in.ImportFixtureTypeUseCase;
import de.vinzens.lumen.application.library.port.in.QueryFixtureLibraryUseCase;
import de.vinzens.lumen.application.library.port.in.ReloadFixtureLibraryUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/library")
class FixtureLibraryController {

	private final QueryFixtureLibraryUseCase library;
	private final ReloadFixtureLibraryUseCase reloader;
	private final ImportFixtureTypeUseCase importer;

	FixtureLibraryController(QueryFixtureLibraryUseCase library, ReloadFixtureLibraryUseCase reloader,
			ImportFixtureTypeUseCase importer) {
		this.library = library;
		this.reloader = reloader;
		this.importer = importer;
	}

	/**
	 * Imports a fixture of the Open Fixture Library: the body is its "OFL JSON" download. The profile is written into
	 * the library as YAML.
	 *
	 * @param manufacturer manufacturer name (the OFL file does not contain it)
	 * @param replace      replace a profile with the same id
	 */
	@PostMapping("/import/ofl")
	@ResponseStatus(HttpStatus.CREATED)
	ImportResponse importOfl(@RequestParam String manufacturer, @RequestParam(defaultValue = "false") boolean replace,
			@RequestBody String json) {
		var result = importer.importOfl(json, manufacturer, replace);
		return new ImportResponse(FixtureTypeDto.from(result.fixtureType()), result.source(), result.warnings());
	}

	/**
	 * @param source   the new profile file, relative to the library
	 * @param warnings what got lost or simplified – check the profile before using it on stage
	 */
	record ImportResponse(FixtureTypeDto fixtureType, String source, List<String> warnings) {
	}

	/** Reads the YAML files again (also happens automatically a few seconds after a file changed). */
	@PostMapping("/reload")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void reload() {
		reloader.reload();
	}

	@GetMapping("/fixture-types")
	List<FixtureTypeDto> fixtureTypes() {
		return library.fixtureTypes().stream().map(FixtureTypeDto::from).toList();
	}

	@GetMapping("/fixture-types/{id}")
	ResponseEntity<FixtureTypeDto> fixtureType(@PathVariable String id) {
		return ResponseEntity.of(library.fixtureType(id).map(FixtureTypeDto::from));
	}

	@GetMapping("/issues")
	List<LibraryIssueDto> issues() {
		return library.issues().stream().map(LibraryIssueDto::from).toList();
	}
}
