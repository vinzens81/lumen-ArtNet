package de.vinzens.lumen.adapter.web.composition;

import de.vinzens.lumen.application.composition.port.in.PreviewCompositionUseCase;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The composition shown on stage for the editor (started with
 * {@code POST /api/projects/{id}/compositions/{cid}/stage-preview}).
 */
@RestController
@RequestMapping("/api/compositions/stage-preview")
class StagePreviewController {

	private final PreviewCompositionUseCase preview;

	StagePreviewController(PreviewCompositionUseCase preview) {
		this.preview = preview;
	}

	@GetMapping
	StagePreviewResponse status() {
		return StagePreviewResponse.of(preview);
	}

	@DeleteMapping
	StagePreviewResponse stop() {
		preview.stopOnStage();
		return StagePreviewResponse.of(preview);
	}

	/** @param compositionId the composition on stage, null if none */
	record StagePreviewResponse(Long compositionId) {

		static StagePreviewResponse of(PreviewCompositionUseCase preview) {
			return new StagePreviewResponse(preview.compositionOnStage().orElse(null));
		}
	}
}
