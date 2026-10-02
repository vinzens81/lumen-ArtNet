package de.vinzens.lumen.adapter.web.access;

import de.vinzens.lumen.application.access.port.in.ManageAccessUseCase;
import de.vinzens.lumen.application.access.port.in.ManageAccessUseCase.UnlockResult;
import de.vinzens.lumen.domain.DomainValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.List;
import java.util.Optional;

/**
 * Configuration PIN and network access. Changing them needs an unlocked client (see {@link ConfigLockInterceptor}).
 */
@RestController
@RequestMapping("/api/access")
class AccessController {

	private final ManageAccessUseCase access;
	private final ConfigLock lock;

	AccessController(ManageAccessUseCase access, ConfigLock lock) {
		this.access = access;
		this.lock = lock;
	}

	@GetMapping
	AccessResponse status(HttpServletRequest request) {
		return new AccessResponse(access.pinSet(), lock.isUnlocked(request.getSession(false)), access.networkAccess(),
				access.networkAccessActive(), access.networkAccessForced(),
				access.networkAccessActive() ? networkUrls(request.getLocalPort()) : List.of());
	}

	@PostMapping("/unlock")
	ResponseEntity<?> unlock(@RequestBody PinRequest body, HttpServletRequest request) {
		return switch (access.unlock(body.pin())) {
			case UnlockResult.Unlocked unlocked -> {
				lock.unlock(request, unlocked.epoch());
				yield ResponseEntity.ok(status(request));
			}
			case UnlockResult.WrongPin wrong ->
					ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem(HttpStatus.FORBIDDEN, "Wrong PIN"));
			case UnlockResult.Blocked blocked -> {
				var seconds = Math.max(1, (blocked.retryIn().toMillis() + 999) / 1000);
				yield ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
						.header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds))
						.body(problem(HttpStatus.TOO_MANY_REQUESTS,
								"Too many wrong PINs – try again in " + seconds + " s"));
			}
		};
	}

	@PostMapping("/lock")
	ResponseEntity<Void> lock(HttpServletRequest request) {
		lock.lock(request);
		return ResponseEntity.noContent().build();
	}

	/** Sets, changes ({@code pin}) or removes ({@code pin: null}) the PIN; this client stays unlocked. */
	@PutMapping("/pin")
	AccessResponse setPin(@RequestBody PinRequest body, HttpServletRequest request) {
		access.setPin(Optional.ofNullable(body.pin()).filter(pin -> !pin.isEmpty()));
		lock.unlock(request, access.lockEpoch());
		return status(request);
	}

	@PutMapping("/network")
	AccessResponse setNetworkAccess(@RequestBody NetworkRequest body, HttpServletRequest request) {
		if (body.enabled() == null) {
			throw new DomainValidationException(List.of("enabled is missing"));
		}
		access.setNetworkAccess(body.enabled());
		return status(request);
	}

	private static ProblemDetail problem(HttpStatus status, String detail) {
		return ProblemDetail.forStatusAndDetail(status, detail);
	}

	/** Addresses other devices can use to reach this server. */
	private static List<String> networkUrls(int port) {
		try {
			return NetworkInterface.networkInterfaces()
					.filter(AccessController::isUsable)
					.flatMap(NetworkInterface::inetAddresses)
					.filter(address -> address instanceof Inet4Address && !address.isLoopbackAddress()
							&& !address.isLinkLocalAddress())
					.map(address -> "http://" + address.getHostAddress() + ":" + port)
					.sorted()
					.toList();
		} catch (SocketException e) {
			return List.of();
		}
	}

	private static boolean isUsable(NetworkInterface network) {
		try {
			return network.isUp() && !network.isLoopback() && !network.isVirtual();
		} catch (SocketException e) {
			return false;
		}
	}

	record PinRequest(String pin) {
	}

	record NetworkRequest(Boolean enabled) {
	}

	/**
	 * @param unlocked             this client may change the configuration
	 * @param networkAccess        setting: accept connections from other devices (after a restart)
	 * @param networkAccessActive  the running server accepts connections from other devices
	 * @param networkAccessForced  switched on by configuration, the setting has no effect
	 * @param urls                 addresses other devices can use, while network access is active
	 */
	record AccessResponse(boolean pinSet, boolean unlocked, boolean networkAccess, boolean networkAccessActive,
			boolean networkAccessForced, List<String> urls) {
	}
}
