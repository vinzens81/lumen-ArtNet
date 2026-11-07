package de.vinzens.lumen.adapter.web.composition;

import de.vinzens.lumen.application.composition.port.in.PreviewCompositionUseCase;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Trying out on the real stage in setup mode: a composition, a move, a scene or one part of it after a light marker
 * (with {@code ?marker=}), or a rehearsed fade into one of them runs as a layer above the playback until stopped. Only one at a time; starting show
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
	StagePreviewResponse showScene(@PathVariable long projectId, @PathVariable long sceneId,
			@RequestParam Optional<String> marker) {
		preview.showSceneOnStage(projectId, sceneId, marker);
		return StagePreviewResponse.of(preview);
	}

	/**
	 * Fades from the light on stage (whatever the stage preview shows, or dark) into the scene with the times of the
	 * GO into it, then the scene stays on stage. With {@code ?marker=} into the part after that light marker, with the
	 * times of passing it.
	 */
	@PostMapping("/api/projects/{projectId}/scenes/{sceneId}/fade-in")
	StagePreviewResponse fadeIn(@PathVariable long projectId, @PathVariable long sceneId,
			@RequestParam Optional<String> marker) {
		preview.fadeIn(projectId, sceneId, marker);
		return StagePreviewResponse.of(preview);
	}

	/**
	 * @param compositionId the composition on stage, null if none
	 * @param moveId        the move on stage, null if none
	 * @param sceneId       the scene on stage, null if none
	 * @param marker        the light marker of the part of the scene on stage, null for its start
	 * @param fade          the running fade into {@code sceneId}, null if none or ended; clients
	 *                      interpolate its progress from the moment they received it
	 */
	record StagePreviewResponse(Long compositionId, Long moveId, Long sceneId, String marker, FadeResponse fade) {

		static StagePreviewResponse of(PreviewCompositionUseCase preview) {
			return new StagePreviewResponse(preview.compositionOnStage().orElse(null),
					preview.moveOnStage().orElse(null),
					preview.sceneOnStage().orElse(null),
					preview.markerOnStage().orElse(null),
					preview.fadeOnStage().map(f -> new FadeResponse(f.elapsed().toMillis(), f.fadeIn().toMillis(),
							f.fadeOut().toMillis(), f.delay().toMillis())).orElse(null));
		}
	}

	/** A fade on stage: the fade-in starts {@code delayMs} after its start, the fade-out at once. */
	record FadeResponse(long elapsedMs, long fadeInMs, long fadeOutMs, long delayMs) {
	}
}
