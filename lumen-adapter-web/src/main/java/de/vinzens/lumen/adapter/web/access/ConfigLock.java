package de.vinzens.lumen.adapter.web.access;

import de.vinzens.lumen.application.access.port.in.ManageAccessUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

/**
 * Remembers in the HTTP session whether a client unlocked setup with the PIN. An unlock is valid until all clients
 * are locked again (see {@link ManageAccessUseCase#lockEpoch()}).
 */
@Component
public class ConfigLock {

	private static final String ATTRIBUTE = "lumen.unlockedEpoch";

	private final ManageAccessUseCase access;

	ConfigLock(ManageAccessUseCase access) {
		this.access = access;
	}

	public boolean isUnlocked(HttpSession session) {
		if (!access.pinSet()) {
			return true;
		}
		try {
			return session != null && session.getAttribute(ATTRIBUTE) instanceof Long epoch
					&& epoch == access.lockEpoch();
		} catch (IllegalStateException e) { // session invalidated
			return false;
		}
	}

	public void requireUnlocked(HttpSession session) {
		if (!isUnlocked(session)) {
			throw new LockedException();
		}
	}

	void unlock(HttpServletRequest request, long epoch) {
		request.getSession(true).setAttribute(ATTRIBUTE, epoch);
	}

	void lock(HttpServletRequest request) {
		var session = request.getSession(false);
		if (session != null) {
			session.removeAttribute(ATTRIBUTE);
		}
	}

	/** Setup is locked for this client; it has to enter the PIN first. */
	public static class LockedException extends RuntimeException {

		LockedException() {
			super("Setup is locked – enter the PIN to make changes");
		}
	}
}
