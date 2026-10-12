package de.vinzens.lumen.adapter.web.composition;

import de.vinzens.lumen.adapter.web.composition.CompositionDtos.CompositionRequest;
import de.vinzens.lumen.adapter.web.composition.CompositionDtos.CompositionResponse;
import de.vinzens.lumen.adapter.web.composition.CompositionDtos.GeneratorDto;
import de.vinzens.lumen.application.composition.port.in.ManageCompositionsUseCase;
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
import java.util.Optional;

/**
 * Compositions of a project: steps that play looks. Scenes refer to them; a change applies to every scene.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/compositions")
class CompositionController {

	private final ManageCompositionsUseCase compositions;
	private final JsonMapper json;

	CompositionController(ManageCompositionsUseCase compositions, JsonMapper json) {
		this.compositions = compositions;
		this.json = json;
	}

	@GetMapping
	List<CompositionResponse> compositions(@PathVariable long projectId) {
		return compositions.compositions(projectId).stream().map(CompositionResponse::from).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	CompositionResponse create(@PathVariable long projectId, @RequestBody CompositionRequest request) {
		return CompositionResponse.from(compositions.create(projectId, request.toSpec(Optional.empty()),
				request.toSteps()));
	}

	@GetMapping("/{compositionId}")
	CompositionResponse composition(@PathVariable long projectId, @PathVariable long compositionId) {
		return CompositionResponse.from(compositions.composition(projectId, compositionId));
	}

	/** Replaces properties and steps; the generator settings stay (they change by generating again). */
	@PutMapping("/{compositionId}")
	CompositionResponse update(@PathVariable long projectId, @PathVariable long compositionId,
			@RequestBody CompositionRequest request) {
		var stored = compositions.composition(projectId, compositionId).spec();
		return CompositionResponse.from(compositions.update(projectId, compositionId,
				request.toSpec(stored.generator(), stored.folder()), request.toSteps()));
	}

	@PostMapping("/{compositionId}/duplicate")
	@ResponseStatus(HttpStatus.CREATED)
	CompositionResponse duplicate(@PathVariable long projectId, @PathVariable long compositionId) {
		return CompositionResponse.from(compositions.duplicate(projectId, compositionId));
	}

	/** Records the programmer as a new step at the end, with a look of its own. */
	@PostMapping("/{compositionId}/record-step")
	CompositionResponse recordStep(@PathVariable long projectId, @PathVariable long compositionId) {
		return CompositionResponse.from(compositions.recordStep(projectId, compositionId));
	}

	/** Replaces the steps with steps generated from an effect (chase, color cycle, rainbow, wave, flicker). */
	@PostMapping("/{compositionId}/generate")
	CompositionResponse generate(@PathVariable long projectId, @PathVariable long compositionId,
			@RequestBody GeneratorDto request) {
		return CompositionResponse.from(compositions.generate(projectId, compositionId, request.toSettings(json)));
	}

	@DeleteMapping("/{compositionId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable long projectId, @PathVariable long compositionId) {
		compositions.delete(projectId, compositionId);
	}
}
