package de.vinzens.lumen.adapter.web.composition;

import de.vinzens.lumen.adapter.web.composition.CompositionDtos.CompositionResponse;
import de.vinzens.lumen.application.composition.port.in.ManageExtraKeysUseCase;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.composition.ExtraKey;
import de.vinzens.lumen.domain.composition.KeyMode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Setting up the extra keys 1–8 of show mode. Pressing them is part of the playback ({@code /api/playback/keys}).
 * The composition on a key is edited with {@code PUT /api/projects/{id}/compositions/{compositionId}}.
 */
@RestController
@RequestMapping("/api/projects/{projectId}")
class ExtraKeyController {

	private final ManageExtraKeysUseCase keys;

	ExtraKeyController(ManageExtraKeysUseCase keys) {
		this.keys = keys;
	}

	@GetMapping("/extra-keys")
	List<KeyResponse> keys(@PathVariable long projectId) {
		return keys.keys(projectId).stream().map(KeyResponse::from).toList();
	}

	/**
	 * With {@code libraryId}: puts a copy of that library composition on the key. Without: changes mode and fade of an
	 * assigned key.
	 */
	@PutMapping("/extra-keys/{slot}")
	KeyResponse setKey(@PathVariable long projectId, @PathVariable int slot, @RequestBody KeyRequest request) {
		var mode = parseMode(request.mode());
		var fade = Duration.ofMillis(Objects.requireNonNullElse(request.fadeMs(), 0L));
		var key = request.libraryId() != null
				? keys.assignKey(projectId, slot, request.libraryId(), mode, fade)
				: keys.updateKey(projectId, slot, mode, fade);
		return KeyResponse.from(key);
	}

	@DeleteMapping("/extra-keys/{slot}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void clearKey(@PathVariable long projectId, @PathVariable int slot) {
		keys.clearKey(projectId, slot);
	}

	@GetMapping("/scenes/{sceneId}/extra-keys")
	SceneKeysResponse sceneKeys(@PathVariable long projectId, @PathVariable long sceneId) {
		return new SceneKeysResponse(keys.sceneKeys(projectId, sceneId).stream().sorted().toList());
	}

	@PutMapping("/scenes/{sceneId}/extra-keys")
	SceneKeysResponse setSceneKeys(@PathVariable long projectId, @PathVariable long sceneId,
			@RequestBody SceneKeysResponse request) {
		var slots = new HashSet<>(request.slots() == null ? List.<Integer>of() : request.slots());
		return new SceneKeysResponse(keys.setSceneKeys(projectId, sceneId, Set.copyOf(slots)).stream().sorted()
				.toList());
	}

	/**
	 * @param libraryId composition to copy onto the key; omit to only change mode and fade
	 * @param mode      toggle (default) or flash
	 */
	record KeyRequest(Long libraryId, String mode, Long fadeMs) {
	}

	record KeyResponse(int slot, String mode, long fadeMs, CompositionResponse composition) {

		static KeyResponse from(ExtraKey key) {
			return new KeyResponse(key.slot(), key.mode().id(), key.fade().toMillis(),
					CompositionResponse.from(key.composition()));
		}
	}

	record SceneKeysResponse(List<Integer> slots) {
	}

	static KeyMode parseMode(String mode) {
		if (mode == null) {
			return KeyMode.TOGGLE;
		}
		try {
			return KeyMode.valueOf(mode.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new DomainValidationException(List.of("mode must be toggle or flash, was '" + mode + "'"));
		}
	}
}
