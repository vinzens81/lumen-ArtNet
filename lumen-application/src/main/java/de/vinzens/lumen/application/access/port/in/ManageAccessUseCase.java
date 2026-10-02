package de.vinzens.lumen.application.access.port.in;

import java.time.Duration;
import java.util.Optional;

/**
 * Protects setup against changes during a performance: with a PIN, a client has to unlock before it may change
 * the configuration, and entering show mode locks all clients again. Show operation (GO, master, blackout) is
 * always allowed. Also decides whether other devices in the network may connect.
 */
public interface ManageAccessUseCase {

	boolean pinSet();

	/** @param pin 4–12 digits, or empty to remove the PIN */
	void setPin(Optional<String> pin);

	/** Checks the PIN. After several wrong PINs, checking is blocked for a while. */
	UnlockResult unlock(String pin);

	/**
	 * Increases whenever all clients are locked (show mode started, PIN changed). A client is unlocked as long as
	 * it unlocked during the current epoch.
	 */
	long lockEpoch();

	/** Whether the server should accept connections from other devices (takes effect after a restart). */
	boolean networkAccess();

	void setNetworkAccess(boolean enabled);

	/** Whether the running server accepts connections from other devices. */
	boolean networkAccessActive();

	/** Network access is switched on by configuration ({@code lumen.network-access}) and cannot be changed here. */
	boolean networkAccessForced();

	sealed interface UnlockResult {

		record Unlocked(long epoch) implements UnlockResult {
		}

		record WrongPin() implements UnlockResult {
		}

		/** Too many wrong PINs; try again after {@code retryIn}. */
		record Blocked(Duration retryIn) implements UnlockResult {
		}
	}
}
