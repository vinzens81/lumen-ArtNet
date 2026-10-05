package de.vinzens.lumen.adapter.web.scene;

import de.vinzens.lumen.adapter.web.output.ValuesJson;
import de.vinzens.lumen.application.composition.port.in.ManageCompositionsUseCase;
import de.vinzens.lumen.application.scene.port.in.ManageScenesUseCase;
import de.vinzens.lumen.domain.composition.Composition;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.scene.Scene;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping("/api/projects/{projectId}/scenes")
class SceneController {

	private final ManageScenesUseCase scenes;

	private final ManageCompositionsUseCase compositions;

	SceneController(ManageScenesUseCase scenes, ManageCompositionsUseCase compositions) {
		this.scenes = scenes;
		this.compositions = compositions;
	}

	@GetMapping
	List<SceneResponse> scenes(@PathVariable long projectId) {
		var copies = compositions.sceneCompositions(projectId);
		return scenes.scenes(projectId).stream()
				.map(scene -> SceneResponse.from(scene, copies.getOrDefault(scene.id(), List.of())))
				.toList();
	}

	private SceneResponse response(Scene scene) {
		return SceneResponse.from(scene, compositions.sceneCompositions(scene.projectId(), scene.id()));
	}

	/**
	 * Creates a scene. Without {@code number} the next whole number is used; with {@code record: true} the
	 * scene takes the current programmer values.
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	SceneResponse create(@PathVariable long projectId, @RequestBody SceneRequest request) {
		return response(scenes.create(projectId,
				Optional.ofNullable(request.number()).filter(n -> !n.isBlank()).map(SceneNumber::parse),
				request.name(), Optional.ofNullable(request.cueNote()), autoFollow(request),
				Boolean.TRUE.equals(request.keyOnly()), timing(request), script(request),
				Boolean.TRUE.equals(request.record())));
	}

	@PutMapping("/{sceneId}")
	SceneResponse update(@PathVariable long projectId, @PathVariable long sceneId, @RequestBody SceneRequest request) {
		if (request.number() == null) {
			throw new DomainValidationException(List.of("number is missing"));
		}
		return response(scenes.update(projectId, sceneId, SceneNumber.parse(request.number()),
				request.name(), Optional.ofNullable(request.cueNote()), autoFollow(request),
				Boolean.TRUE.equals(request.keyOnly()), timing(request), script(request)));
	}

	/** Overwrites the scene with the current programmer values. */
	@PostMapping("/{sceneId}/record")
	SceneResponse record(@PathVariable long projectId, @PathVariable long sceneId) {
		return response(scenes.record(projectId, sceneId));
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

	private static String script(SceneRequest request) {
		return Objects.requireNonNullElse(request.script(), "");
	}

	private static SceneTiming timing(SceneRequest request) {
		return TimingJson.fromJson(request.fadeInMs(), request.fadeOutMs(), request.delayMs());
	}

	/**
	 * @param number       cue number as text, e.g. "2.5"
	 * @param autoFollowMs stay time before the next scene starts automatically, {@code null} = wait for GO
	 * @param keyOnly      only played by extra keys, GO skips the scene
	 * @param fadeInMs     fade-in of the GO into this scene, {@code null} = the project's
	 * @param fadeOutMs    fade-out of the look before at the GO into this scene, {@code null} = the project's
	 * @param delayMs      the fade-in starts this long after GO, {@code null} = 0
	 * @param script       text spoken on stage during the scene, {@code **…**} marks stage directions;
	 *                     {@code null} = no text
	 */
	record SceneRequest(String number, String name, String cueNote, Long autoFollowMs, Boolean keyOnly,
			Long fadeInMs, Long fadeOutMs, Long delayMs, String script, Boolean record) {
	}

	/**
	 * @param keyOnly      only played by extra keys, not part of the cue list
	 * @param fadeInMs     own fade-in time, {@code null} = the project's
	 * @param fadeOutMs    own fade-out time, {@code null} = the project's
	 * @param script       text spoken on stage during the scene, empty if none
	 * @param values       programmer-style values per fixture id
	 * @param compositions compositions running in the scene, in their order
	 */
	record SceneResponse(long id, String number, String name, String cueNote, Long autoFollowMs, boolean keyOnly,
			Long fadeInMs, Long fadeOutMs, long delayMs, String script, int fixtureCount,
			Map<Long, Map<String, Double>> values,
			List<CompositionSummary> compositions) {

		static SceneResponse from(Scene scene, List<Composition> compositions) {
			var spec = scene.spec();
			var timing = spec.timing();
			return new SceneResponse(scene.id(), spec.number().toString(), spec.name(), spec.cueNote().orElse(null),
					spec.autoFollow().map(Duration::toMillis).orElse(null), spec.keyOnly(),
					TimingJson.fadeInMs(timing), TimingJson.fadeOutMs(timing), timing.delay().toMillis(),
					spec.script(), scene.values().size(),
					ValuesJson.toJson(scene.values()), compositions.stream()
							.map(c -> new CompositionSummary(c.id(), c.spec().name(), c.spec().pattern().kind().id()))
							.toList());
		}
	}

	record CompositionSummary(long id, String name, String kind) {
	}
}
