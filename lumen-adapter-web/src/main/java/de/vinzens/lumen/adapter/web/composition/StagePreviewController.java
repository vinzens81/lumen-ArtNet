package de.vinzens.lumen.adapter.web.composition;

import de.vinzens.lumen.application.composition.port.in.PreviewCompositionUseCase;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trying out on the real stage in setup mode: a composition or a whole scene (its values and compositions) runs as a
 * layer above the playback until stopped. Only one at a time; starting show mode stops it.
 */
@RestController
class StagePreviewController {

	private final PreviewCompositionUseCase preview;

	StagePreviewController(PreviewCompositionUseCase preview) {
		this.preview = preview;
	}

	@GetMapping("/api/stage-preview")
	StagePreviewResponse status() {
		return StagePreviewResponse.of(preview);
	}

	@DeleteMapping("/api/stage-preview")
	StagePreviewResponse stop() {
		preview.stopOnStage();
		return StagePreviewResponse.of(preview);
	}

	@PostMapping("/api/projects/{projectId}/compositions/{compositionId}/stage-preview")
	StagePreviewResponse showComposition(@PathVariable long projectId, @PathVariable long compositionId) {
		preview.showOnStage(projectId, compositionId);
		return StagePreviewResponse.of(preview);
	}

	@PostMapping("/api/projects/{projectId}/scenes/{sceneId}/stage-preview")
	StagePreviewResponse showScene(@PathVariable long projectId, @PathVariable long sceneId) {
		preview.showSceneOnStage(projectId, sceneId);
		return StagePreviewResponse.of(preview);
	}

	/**
	 * @param compositionId the composition on stage, null if none
	 * @param sceneId       the scene on stage, null if none
	 */
	record StagePreviewResponse(Long compositionId, Long sceneId) {

		static StagePreviewResponse of(PreviewCompositionUseCase preview) {
			return new StagePreviewResponse(preview.compositionOnStage().orElse(null),
					preview.sceneOnStage().orElse(null));
		}
	}
}
