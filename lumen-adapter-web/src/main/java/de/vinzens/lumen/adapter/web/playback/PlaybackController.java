package de.vinzens.lumen.adapter.web.playback;

import de.vinzens.lumen.adapter.web.playback.PlaybackDtos.PlaybackResponse;
import de.vinzens.lumen.application.output.port.in.ControlModeUseCase;
import de.vinzens.lumen.application.playback.port.in.ControlPlaybackUseCase;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.output.OperatingMode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
class PlaybackController {

	private final ControlPlaybackUseCase playback;
	private final ControlModeUseCase mode;

	PlaybackController(ControlPlaybackUseCase playback, ControlModeUseCase mode) {
		this.playback = playback;
		this.mode = mode;
	}

	@GetMapping("/api/playback")
	PlaybackResponse status() {
		return PlaybackResponse.from(playback.status());
	}

	@PostMapping("/api/playback/go")
	PlaybackResponse go() {
		return PlaybackResponse.from(playback.go());
	}

	@PostMapping("/api/playback/back")
	PlaybackResponse back() {
		return PlaybackResponse.from(playback.back());
	}

	@PostMapping("/api/playback/pause")
	PlaybackResponse pause() {
		return PlaybackResponse.from(playback.togglePause());
	}

	@PostMapping("/api/playback/finish")
	PlaybackResponse finish() {
		return PlaybackResponse.from(playback.finish());
	}

	@PutMapping("/api/playback/next")
	PlaybackResponse next(@RequestBody NextRequest request) {
		if (request.sceneId() == null) {
			throw new DomainValidationException(List.of("sceneId is missing"));
		}
		return PlaybackResponse.from(playback.setNext(request.sceneId()));
	}

	@GetMapping("/api/mode")
	ModeResponse mode() {
		return new ModeResponse(mode.mode().name().toLowerCase(Locale.ROOT));
	}

	/** Switching to show mode clears and locks the programmer. */
	@PutMapping("/api/mode")
	ModeResponse setMode(@RequestBody ModeResponse request) {
		var value = request.mode() == null ? "" : request.mode().toUpperCase(Locale.ROOT);
		try {
			return new ModeResponse(mode.setMode(OperatingMode.valueOf(value)).name().toLowerCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new DomainValidationException(List.of("mode must be setup or show, was '" + request.mode() + "'"));
		}
	}

	record NextRequest(Long sceneId) {
	}

	record ModeResponse(String mode) {
	}
}
