package de.vinzens.lumen.adapter.web.scene;

import de.vinzens.lumen.adapter.web.look.LookController.RecordRequest;
import de.vinzens.lumen.application.look.port.in.ManageLooksUseCase.RecordOptions;
import de.vinzens.lumen.application.scene.port.in.ManageScenesUseCase;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.composition.KeyMode;
import de.vinzens.lumen.domain.scene.Scene;
import de.vinzens.lumen.domain.scene.SceneComposition;
import de.vinzens.lumen.domain.scene.SceneKey;
import de.vinzens.lumen.domain.scene.SceneNumber;
import de.vinzens.lumen.domain.scene.SceneTiming;
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

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping("/api/projects/{projectId}/scenes")
class SceneController {

	private final ManageScenesUseCase scenes;

	SceneController(ManageScenesUseCase scenes) {
		this.scenes = scenes;
	}

	@GetMapping
	List<SceneResponse> scenes(@PathVariable long projectId) {
		return scenes.scenes(projectId).stream().map(SceneResponse::from).toList();
	}

	private static SceneResponse response(Scene scene) {
		return SceneResponse.from(scene);
	}

	/**
	 * Creates a scene. Without {@code number} the next whole number is used; with {@code record: true} the
	 * programmer becomes the scene's first composition (one step with a look of its own).
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	SceneResponse create(@PathVariable long projectId, @RequestBody SceneRequest request) {
		return response(scenes.create(projectId,
				Optional.ofNullable(request.number()).filter(n -> !n.isBlank()).map(SceneNumber::parse),
				request.name(), Optional.ofNullable(request.cueNote()), autoFollow(request), timing(request),
				script(request), Boolean.TRUE.equals(request.record())));
	}

	@PutMapping("/{sceneId}")
	SceneResponse update(@PathVariable long projectId, @PathVariable long sceneId, @RequestBody SceneRequest request) {
		if (request.number() == null) {
			throw new DomainValidationException(List.of("number is missing"));
		}
		return response(scenes.update(projectId, sceneId, SceneNumber.parse(request.number()),
				request.name(), Optional.ofNullable(request.cueNote()), autoFollow(request), timing(request),
				script(request)));
	}

	/** Copies the scene with its compositions and keys; without {@code number} it goes to the end. */
	@PostMapping("/{sceneId}/duplicate")
	@ResponseStatus(HttpStatus.CREATED)
	SceneResponse duplicate(@PathVariable long projectId, @PathVariable long sceneId,
			@RequestBody(required = false) DuplicateRequest request) {
		var number = Optional.ofNullable(request).map(DuplicateRequest::number).filter(n -> !n.isBlank())
				.map(SceneNumber::parse);
		return response(scenes.duplicate(projectId, sceneId, number));
	}

	/**
	 * The quick way for a simple scene: takes the programmer into the look of its first composition (one step), or
	 * records a first composition if the scene has none. Without a body the programmer replaces the whole look,
	 * with {@code merge: true} only what it sets is taken.
	 */
	@PostMapping("/{sceneId}/record")
	SceneResponse record(@PathVariable long projectId, @PathVariable long sceneId,
			@RequestBody(required = false) RecordRequest request) {
		return response(scenes.record(projectId, sceneId,
				request == null ? RecordOptions.REPLACE : request.toOptions()));
	}

	/** The compositions of the scene in their order (later ones win on the same attribute). */
	@PutMapping("/{sceneId}/compositions")
	SceneResponse setCompositions(@PathVariable long projectId, @PathVariable long sceneId,
			@RequestBody List<SceneCompositionDto> compositions) {
		return response(scenes.setCompositions(projectId, sceneId,
				compositions.stream().map(SceneCompositionDto::toUse).toList()));
	}

	/** The extra keys of the scene, each playing a composition. */
	@PutMapping("/{sceneId}/keys")
	SceneResponse setKeys(@PathVariable long projectId, @PathVariable long sceneId,
			@RequestBody List<SceneKeyDto> keys) {
		return response(scenes.setKeys(projectId, sceneId, keys.stream().map(SceneKeyDto::toKey).toList()));
	}

	/** Loads the scene into the programmer as it shows right after GO. */
	@PostMapping("/{sceneId}/load")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void load(@PathVariable long projectId, @PathVariable long sceneId) {
		scenes.loadIntoProgrammer(projectId, sceneId);
	}

	@DeleteMapping("/{sceneId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable long projectId, @PathVariable long sceneId) {
		scenes.delete(projectId, sceneId);
	}

	private static Optional<Duration> autoFollow(SceneRequest request) {
		return Optional.ofNullable(request.autoFollowMs()).map(Duration::ofMillis);
	}

	private static String script(SceneRequest request) {
		return Objects.requireNonNullElse(request.script(), "");
	}

	private static SceneTiming timing(SceneRequest request) {
		return TimingJson.fromJson(request.fadeInMs(), request.fadeOutMs(), request.delayMs(), request.fadeCurve());
	}

	/**
	 * @param number       cue number as text, e.g. "2.5"
	 * @param autoFollowMs stay time before the next scene starts automatically, {@code null} = wait for GO
	 * @param fadeInMs     fade-in of the GO into this scene, {@code null} = the project's
	 * @param fadeOutMs    fade-out of the look before at the GO into this scene, {@code null} = the project's
	 * @param delayMs      the fade-in starts this long after GO, {@code null} = 0
	 * @param fadeCurve    "linear", "s-curve" or "square", {@code null} = the project's
	 * @param script       text spoken on stage during the scene, {@code **…**} marks stage directions;
	 *                     {@code null} = no text
	 */
	record SceneRequest(String number, String name, String cueNote, Long autoFollowMs, Long fadeInMs, Long fadeOutMs, Long delayMs, String fadeCurve, String script, Boolean record) {
	}

	/** @param number cue number of the copy, {@code null} = after the last scene */
	record DuplicateRequest(String number) {
	}

	/**
	 * @param restart start from the beginning at GO, even if the composition already runs in the scene before
	 */
	record SceneCompositionDto(Long compositionId, Double level, Double speedFactor, Boolean restart) {

		SceneComposition toUse() {
			if (compositionId == null) {
				throw new DomainValidationException(List.of("compositionId is missing"));
			}
			return new SceneComposition(compositionId, Objects.requireNonNullElse(level, 1.0),
					Objects.requireNonNullElse(speedFactor, 1.0), Boolean.TRUE.equals(restart));
		}

		static SceneCompositionDto from(SceneComposition use) {
			return new SceneCompositionDto(use.compositionId(), use.level(), use.speedFactor(), use.restart());
		}
	}

	/** @param mode toggle or flash */
	record SceneKeyDto(Integer slot, Long compositionId, String mode, Long fadeMs) {

		SceneKey toKey() {
			if (slot == null || compositionId == null) {
				throw new DomainValidationException(List.of("slot and compositionId of a key are required"));
			}
			var keyMode = mode == null ? KeyMode.TOGGLE : KeyMode.parse(mode);
			return new SceneKey(slot, compositionId, keyMode, Duration.ofMillis(Objects.requireNonNullElse(fadeMs, 0L)));
		}

		static SceneKeyDto from(SceneKey key) {
			return new SceneKeyDto(key.slot(), key.compositionId(), key.mode().id(), key.fade().toMillis());
		}
	}

	/**
	 * @param fadeInMs       own fade-in time, {@code null} = the project's
	 * @param fadeOutMs      own fade-out time, {@code null} = the project's
	 * @param fadeCurve      own fade curve, {@code null} = the project's
	 * @param script         text spoken on stage during the scene, empty if none
	 * @param compositions   compositions running in the scene, in their order
	 * @param keys           extra keys usable while the scene is on stage
	 */
	record SceneResponse(long id, String number, String name, String cueNote, Long autoFollowMs,
			Long fadeInMs, Long fadeOutMs, long delayMs, String fadeCurve, String script,
			List<SceneCompositionDto> compositions, List<SceneKeyDto> keys) {

		static SceneResponse from(Scene scene) {
			var spec = scene.spec();
			var timing = spec.timing();
			return new SceneResponse(scene.id(), spec.number().toString(), spec.name(), spec.cueNote().orElse(null),
					spec.autoFollow().map(Duration::toMillis).orElse(null),
					TimingJson.fadeInMs(timing), TimingJson.fadeOutMs(timing), timing.delay().toMillis(),
					TimingJson.fadeCurve(timing), spec.script(),
					scene.compositions().stream().map(SceneCompositionDto::from).toList(),
					scene.keys().stream().map(SceneKeyDto::from).toList());
		}
	}
}
