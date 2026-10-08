package de.vinzens.lumen.adapter.web.composition;

import de.vinzens.lumen.application.composition.port.in.PreviewCompositionUseCase;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trying out on the real stage in setup mode: a composition, a move, a whole scene (its values and compositions) or a
 * rehearsed fade into a scene runs as a layer above the playback until stopped. Only one at a time; starting show
 * mode stops it.
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

	@PostMapping("/api/projects/{projectId}/moves/{moveId}/stage-preview")
	StagePreviewResponse showMove(@PathVariable long projectId, @PathVariable long moveId) {
		preview.showMoveOnStage(projectId, moveId);
		return StagePreviewResponse.of(preview);
	}

	@PostMapping("/api/projects/{projectId}/scenes/{sceneId}/stage-preview")
	StagePreviewResponse showScene(@PathVariable long projectId, @PathVariable long sceneId) {
		preview.showSceneOnStage(projectId, sceneId);
		return StagePreviewResponse.of(preview);
	}

	/**
	 * Rehearses the GO into the scene: the previous scene of the cue list fades into it with the times of the show,
	 * then the scene stays on stage.
	 */
	@PostMapping("/api/projects/{projectId}/scenes/{sceneId}/rehearse-fade")
	StagePreviewResponse rehearseFade(@PathVariable long projectId, @PathVariable long sceneId) {
		preview.rehearseFade(projectId, sceneId);
		return StagePreviewResponse.of(preview);
	}

	/**
	 * @param compositionId the composition on stage, null if none
	 * @param moveId        the move on stage, null if none
	 * @param sceneId       the scene on stage, null if none
	 * @param fade          the running rehearsed fade into {@code sceneId}, null if none or ended; clients
	 *                      interpolate its progress from the moment they received it
	 */
	record StagePreviewResponse(Long compositionId, Long moveId, Long sceneId, FadeResponse fade) {

		static StagePreviewResponse of(PreviewCompositionUseCase preview) {
			return new StagePreviewResponse(preview.compositionOnStage().orElse(null),
					preview.moveOnStage().orElse(null),
					preview.sceneOnStage().orElse(null),
					preview.fadeOnStage().map(f -> new FadeResponse(f.fromSceneId().orElse(null),
							f.elapsed().toMillis(), f.fadeIn().toMillis(), f.fadeOut().toMillis(),
							f.delay().toMillis())).orElse(null));
		}
	}

	/** @param fromSceneId the scene fading out, null for the dark stage */
	record FadeResponse(Long fromSceneId, long elapsedMs, long fadeInMs, long fadeOutMs, long delayMs) {
	}
}
