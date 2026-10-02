package de.vinzens.lumen.adapter.web.composition;

import de.vinzens.lumen.adapter.web.composition.CompositionDtos.CompositionRequest;
import de.vinzens.lumen.adapter.web.composition.CompositionDtos.CompositionResponse;
import de.vinzens.lumen.application.composition.port.in.ManageCompositionsUseCase;
import de.vinzens.lumen.domain.DomainValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Composition library of a project and the copies in scenes. A copy is changed with the same
 * {@code PUT /compositions/{id}} as a library composition.
 */
@RestController
@RequestMapping("/api/projects/{projectId}")
class CompositionController {

	private final ManageCompositionsUseCase compositions;
	private final JsonMapper json;

	CompositionController(ManageCompositionsUseCase compositions, JsonMapper json) {
		this.compositions = compositions;
		this.json = json;
	}

	@GetMapping("/compositions")
	List<CompositionResponse> library(@PathVariable long projectId) {
		return compositions.library(projectId).stream().map(CompositionResponse::from).toList();
	}

	@PostMapping("/compositions")
	@ResponseStatus(HttpStatus.CREATED)
	CompositionResponse create(@PathVariable long projectId, @RequestBody CompositionRequest request) {
		return CompositionResponse.from(compositions.createInLibrary(projectId, request.toSpec(json)));
	}

	@GetMapping("/compositions/{compositionId}")
	CompositionResponse composition(@PathVariable long projectId, @PathVariable long compositionId) {
		return CompositionResponse.from(compositions.composition(projectId, compositionId));
	}

	@PutMapping("/compositions/{compositionId}")
	CompositionResponse update(@PathVariable long projectId, @PathVariable long compositionId,
			@RequestBody CompositionRequest request) {
		return CompositionResponse.from(compositions.update(projectId, compositionId, request.toSpec(json)));
	}

	@DeleteMapping("/compositions/{compositionId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable long projectId, @PathVariable long compositionId) {
		compositions.delete(projectId, compositionId);
	}

	/** Takes pattern and target of the library origin again (level, speed and restart stay). */
	@PostMapping("/compositions/{compositionId}/reload-from-library")
	CompositionResponse reloadFromLibrary(@PathVariable long projectId, @PathVariable long compositionId) {
		return CompositionResponse.from(compositions.reloadFromLibrary(projectId, compositionId));
	}

	/** Writes pattern and target of a copy back to the library. */
	@PostMapping("/compositions/{compositionId}/save-to-library")
	CompositionResponse saveToLibrary(@PathVariable long projectId, @PathVariable long compositionId) {
		return CompositionResponse.from(compositions.saveToLibrary(projectId, compositionId));
	}

	@GetMapping("/scenes/{sceneId}/compositions")
	List<CompositionResponse> sceneCompositions(@PathVariable long projectId, @PathVariable long sceneId) {
		return compositions.sceneCompositions(projectId, sceneId).stream().map(CompositionResponse::from).toList();
	}

	/** Inserts a copy of a library composition at the end of the scene. */
	@PostMapping("/scenes/{sceneId}/compositions")
	@ResponseStatus(HttpStatus.CREATED)
	CompositionResponse insert(@PathVariable long projectId, @PathVariable long sceneId,
			@RequestBody InsertRequest request) {
		if (request.libraryId() == null) {
			throw new DomainValidationException(List.of("libraryId is missing"));
		}
		return CompositionResponse.from(compositions.insertIntoScene(projectId, sceneId, request.libraryId()));
	}

	@PutMapping("/scenes/{sceneId}/compositions/order")
	List<CompositionResponse> reorder(@PathVariable long projectId, @PathVariable long sceneId,
			@RequestBody OrderRequest request) {
		return compositions.reorderScene(projectId, sceneId, request.ids() == null ? List.of() : request.ids())
				.stream().map(CompositionResponse::from).toList();
	}

	record InsertRequest(Long libraryId) {
	}

	record OrderRequest(List<Long> ids) {
	}
}
