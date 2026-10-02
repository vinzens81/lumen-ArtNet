package de.vinzens.lumen.adapter.web.playback;

import de.vinzens.lumen.adapter.web.access.ConfigLock;
import de.vinzens.lumen.adapter.web.playback.PlaybackDtos.PlaybackResponse;
import de.vinzens.lumen.application.output.port.in.ControlModeUseCase;
import de.vinzens.lumen.application.playback.port.in.ControlPlaybackUseCase;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.output.OperatingMode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
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
	private final ConfigLock lock;

	PlaybackController(ControlPlaybackUseCase playback, ControlModeUseCase mode, ConfigLock lock) {
		this.playback = playback;
		this.mode = mode;
		this.lock = lock;
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

	/** Puts the scene from before a restart back on stage at once. */
	@PostMapping("/api/playback/resume")
	PlaybackResponse resume() {
		return PlaybackResponse.from(playback.resume());
	}

	/** Starts from the beginning instead of resuming. */
	@DeleteMapping("/api/playback/resume")
	PlaybackResponse dismissResume() {
		return PlaybackResponse.from(playback.dismissResume());
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

	/**
	 * Switching to show mode clears and locks the programmer. Leaving show mode needs an unlocked client if a PIN
	 * is set.
	 */
	@PutMapping("/api/mode")
	ModeResponse setMode(@RequestBody ModeResponse body, HttpServletRequest request) {
		var value = body.mode() == null ? "" : body.mode().toUpperCase(Locale.ROOT);
		OperatingMode target;
		try {
			target = OperatingMode.valueOf(value);
		} catch (IllegalArgumentException e) {
			throw new DomainValidationException(List.of("mode must be setup or show, was '" + body.mode() + "'"));
		}
		if (target != OperatingMode.SHOW && mode.mode() == OperatingMode.SHOW) {
			lock.requireUnlocked(request.getSession(false));
		}
		return new ModeResponse(mode.setMode(target).name().toLowerCase(Locale.ROOT));
	}

	record NextRequest(Long sceneId) {
	}

	record ModeResponse(String mode) {
	}
}
