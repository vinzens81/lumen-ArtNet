package de.vinzens.lumen.adapter.web.playback;

import de.vinzens.lumen.application.playback.port.in.ManageFadersUseCase;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.composition.Fader;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * Which scene each fader of the show plays, e.g. the house lights. The levels are set through the playback.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/faders")
class FaderController {

	private final ManageFadersUseCase faders;

	FaderController(ManageFadersUseCase faders) {
		this.faders = faders;
	}

	@GetMapping
	List<FaderResponse> faders(@PathVariable long projectId) {
		return faders.faders(projectId).stream().map(FaderResponse::from).toList();
	}

	@PutMapping("/{slot}")
	FaderResponse save(@PathVariable long projectId, @PathVariable int slot, @RequestBody FaderRequest request) {
		if (request.sceneId() == null) {
			throw new DomainValidationException(List.of("sceneId is missing"));
		}
		return FaderResponse.from(faders.save(projectId, new Fader(slot, Optional.ofNullable(request.name()),
				request.sceneId())));
	}

	@DeleteMapping("/{slot}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable long projectId, @PathVariable int slot) {
		faders.delete(projectId, slot);
	}

	/** @param name shown on the fader, {@code null} = the scene's name */
	record FaderRequest(String name, Long sceneId) {
	}

	record FaderResponse(int slot, String name, long sceneId) {

		static FaderResponse from(Fader fader) {
			return new FaderResponse(fader.slot(), fader.name().orElse(null), fader.sceneId());
		}
	}
}
