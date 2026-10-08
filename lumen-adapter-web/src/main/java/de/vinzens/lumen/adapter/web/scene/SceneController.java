package de.vinzens.lumen.adapter.web.scene;

import de.vinzens.lumen.adapter.web.look.LookController.RecordRequest;
import de.vinzens.lumen.application.look.port.in.ManageLooksUseCase.RecordOptions;
import de.vinzens.lumen.application.scene.port.in.ManageScenesUseCase;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.composition.KeyMode;
import de.vinzens.lumen.domain.scene.Scene;
import de.vinzens.lumen.domain.scene.SceneComposition;
import de.vinzens.lumen.domain.scene.SceneKey;
import de.vinzens.lumen.domain.scene.SceneLook;
import de.vinzens.lumen.domain.scene.SceneMove;
import de.vinzens.lumen.domain.scene.SceneNumber;
import de.vinzens.lumen.domain.scene.SceneSection;
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

	/**
	 * The light of a part of the scene: after a light marker of the script ({@code //## Marker (info)##}), or the
	 * start of the scene for an empty {@code marker}. Each part stands on its own.
	 */
	@PutMapping("/{sceneId}/sections")
	SceneResponse setSection(@PathVariable long projectId, @PathVariable long sceneId,
			@RequestBody SectionDto section) {
		return response(scenes.setSection(projectId, sceneId, section.marker() == null ? "" : section.marker(),
				list(section.compositions()).stream().map(SceneCompositionDto::toUse).toList(),
				list(section.looks()).stream().map(SceneLookDto::toUse).toList(),
				list(section.moves()).stream().map(SceneMoveDto::toUse).toList()));
	}

	private static <T> List<T> list(List<T> items) {
		return items == null ? List.of() : items;
	}

	/** The extra keys of the scene, each playing a composition or a move. */
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

	/** A look used directly in the scene, at a level 0–1 ({@code null} = full). */
	record SceneLookDto(Long lookId, Double level) {

		SceneLook toUse() {
			if (lookId == null) {
				throw new DomainValidationException(List.of("lookId is missing"));
			}
			return new SceneLook(lookId, Objects.requireNonNullElse(level, 1.0));
		}

		static SceneLookDto from(SceneLook use) {
			return new SceneLookDto(use.lookId(), use.level());
		}
	}

	/** A move used in the scene: where its fixtures point. */
	record SceneMoveDto(Long moveId) {

		SceneMove toUse() {
			if (moveId == null) {
				throw new DomainValidationException(List.of("moveId is missing"));
			}
			return new SceneMove(moveId);
		}

		static SceneMoveDto from(SceneMove use) {
			return new SceneMoveDto(use.moveId());
		}
	}

	/**
	 * The light of a part of the scene.
	 *
	 * @param marker name of the light marker, {@code null} or empty = the start of the scene
	 * @param moves  where the pan/tilt fixtures point; on the same fixture a later move wins
	 */
	record SectionDto(String marker, List<SceneCompositionDto> compositions, List<SceneLookDto> looks,
			List<SceneMoveDto> moves) {

		static SectionDto from(SceneSection section) {
			return new SectionDto(section.marker(), section.compositions().stream().map(SceneCompositionDto::from)
					.toList(), section.looks().stream().map(SceneLookDto::from).toList(),
					section.moves().stream().map(SceneMoveDto::from).toList());
		}
	}

	/**
	 * @param compositionId the composition the key plays; exactly one of composition and move
	 * @param moveId        the move the key plays
	 * @param mode          toggle or flash
	 */
	record SceneKeyDto(Integer slot, Long compositionId, Long moveId, String mode, Long fadeMs) {

		SceneKey toKey() {
			if (slot == null || (compositionId == null && moveId == null)) {
				throw new DomainValidationException(List.of("slot and compositionId or moveId of a key are required"));
			}
			var keyMode = mode == null ? KeyMode.TOGGLE : KeyMode.parse(mode);
			return new SceneKey(slot, Optional.ofNullable(compositionId), Optional.ofNullable(moveId), keyMode,
					Duration.ofMillis(Objects.requireNonNullElse(fadeMs, 0L)));
		}

		static SceneKeyDto from(SceneKey key) {
			return new SceneKeyDto(key.slot(), key.compositionId().orElse(null), key.moveId().orElse(null),
					key.mode().id(), key.fade().toMillis());
		}
	}

	/**
	 * @param fadeInMs       own fade-in time, {@code null} = the project's
	 * @param fadeOutMs      own fade-out time, {@code null} = the project's
	 * @param fadeCurve      own fade curve, {@code null} = the project's
	 * @param script         text spoken on stage during the scene, empty if none
	 * @param compositions   compositions running in the scene, in their order
	 * @param keys           extra keys usable while the scene is on stage
	 * @param looks          looks used directly at the start of the scene, applied before the compositions
	 * @param moves          moves at the start of the scene
	 * @param sections       the light of the parts after the light markers of the script, by marker name
	 */
	record SceneResponse(long id, String number, String name, String cueNote, Long autoFollowMs,
			Long fadeInMs, Long fadeOutMs, long delayMs, String fadeCurve, String script,
			List<SceneCompositionDto> compositions, List<SceneKeyDto> keys, List<SceneLookDto> looks,
			List<SceneMoveDto> moves, List<SectionDto> sections) {

		static SceneResponse from(Scene scene) {
			var spec = scene.spec();
			var timing = spec.timing();
			return new SceneResponse(scene.id(), spec.number().toString(), spec.name(), spec.cueNote().orElse(null),
					spec.autoFollow().map(Duration::toMillis).orElse(null),
					TimingJson.fadeInMs(timing), TimingJson.fadeOutMs(timing), timing.delay().toMillis(),
					TimingJson.fadeCurve(timing), spec.script(),
					scene.compositions().stream().map(SceneCompositionDto::from).toList(),
					scene.keys().stream().map(SceneKeyDto::from).toList(),
					scene.looks().stream().map(SceneLookDto::from).toList(),
					scene.moves().stream().map(SceneMoveDto::from).toList(),
					scene.sections().stream().map(SectionDto::from).toList());
		}
	}
}
