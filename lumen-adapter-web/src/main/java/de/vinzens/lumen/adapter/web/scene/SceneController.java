package de.vinzens.lumen.adapter.web.scene;

import de.vinzens.lumen.adapter.web.output.ValuesJson;
import de.vinzens.lumen.application.scene.port.in.ManageScenesUseCase;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.scene.Scene;
import de.vinzens.lumen.domain.scene.SceneNumber;
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
import java.util.Map;
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

	/**
	 * Creates a scene. Without {@code number} the next whole number is used; with {@code record: true} the
	 * scene takes the current programmer values.
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	SceneResponse create(@PathVariable long projectId, @RequestBody SceneRequest request) {
		return SceneResponse.from(scenes.create(projectId,
				Optional.ofNullable(request.number()).filter(n -> !n.isBlank()).map(SceneNumber::parse),
				request.name(), Optional.ofNullable(request.cueNote()), autoFollow(request),
				Boolean.TRUE.equals(request.record())));
	}

	@PutMapping("/{sceneId}")
	SceneResponse update(@PathVariable long projectId, @PathVariable long sceneId, @RequestBody SceneRequest request) {
		if (request.number() == null) {
			throw new DomainValidationException(List.of("number is missing"));
		}
		return SceneResponse.from(scenes.update(projectId, sceneId, SceneNumber.parse(request.number()),
				request.name(), Optional.ofNullable(request.cueNote()), autoFollow(request)));
	}

	/** Overwrites the scene with the current programmer values. */
	@PostMapping("/{sceneId}/record")
	SceneResponse record(@PathVariable long projectId, @PathVariable long sceneId) {
		return SceneResponse.from(scenes.record(projectId, sceneId));
	}

	/** Loads the scene into the programmer to preview and edit it. */
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

	/**
	 * @param number       cue number as text, e.g. "2.5"
	 * @param autoFollowMs stay time before the next scene starts automatically, {@code null} = wait for GO
	 */
	record SceneRequest(String number, String name, String cueNote, Long autoFollowMs, Boolean record) {
	}

	/**
	 * @param values programmer-style values per fixture id
	 */
	record SceneResponse(long id, String number, String name, String cueNote, Long autoFollowMs, int fixtureCount,
			Map<Long, Map<String, Double>> values) {

		static SceneResponse from(Scene scene) {
			var spec = scene.spec();
			return new SceneResponse(scene.id(), spec.number().toString(), spec.name(), spec.cueNote().orElse(null),
					spec.autoFollow().map(Duration::toMillis).orElse(null), scene.values().size(),
					ValuesJson.toJson(scene.values()));
		}
	}
}
