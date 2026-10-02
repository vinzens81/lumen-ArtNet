package de.vinzens.lumen.adapter.web.library;

import de.vinzens.lumen.application.library.port.in.QueryFixtureLibraryUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/library")
class FixtureLibraryController {

	private final QueryFixtureLibraryUseCase library;

	FixtureLibraryController(QueryFixtureLibraryUseCase library) {
		this.library = library;
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
