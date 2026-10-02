package de.vinzens.lumen.application.access;

import de.vinzens.lumen.application.access.port.in.ManageAccessUseCase;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.output.port.in.ControlModeUseCase;
import de.vinzens.lumen.application.settings.port.out.SettingsRepository;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.output.OperatingMode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public class AccessService implements ManageAccessUseCase {

	static final String PIN_KEY = "config_pin_hash";
	static final String NETWORK_KEY = "network_access";
	static final int MAX_FAILURES = 5;
	static final Duration BLOCK_TIME = Duration.ofSeconds(30);
	private static final Pattern PIN = Pattern.compile("\\d{4,12}");

	private final SettingsRepository settings;
	private final ControlModeUseCase mode;
	private final Clock clock;
	private final StateEvents events;
	private final boolean networkAccessForced;
	private final boolean networkAccessActive;

	private volatile long epoch = 1;
	private OperatingMode lastMode;
	private int failures;
	private Instant blockedUntil = Instant.MIN;

	/**
	 * @param networkAccessForced network access is switched on by configuration, regardless of the setting
	 */
	public AccessService(SettingsRepository settings, ControlModeUseCase mode, Clock clock, StateEvents events,
			boolean networkAccessForced) {
		this.settings = Objects.requireNonNull(settings, "settings");
		this.mode = Objects.requireNonNull(mode, "mode");
		this.clock = Objects.requireNonNull(clock, "clock");
		this.events = Objects.requireNonNull(events, "events");
		this.networkAccessForced = networkAccessForced;
		this.networkAccessActive = networkAccessForced || networkAccess();
		this.lastMode = mode.mode();
		events.subscribe(topic -> {
			if (topic == StateTopic.MODE) {
				modeChanged();
			}
		});
	}

	private synchronized void modeChanged() {
		var current = mode.mode();
		if (current == OperatingMode.SHOW && lastMode != OperatingMode.SHOW) {
			lockAll();
		}
		lastMode = current;
	}

	@Override
	public boolean pinSet() {
		return settings.get(PIN_KEY).isPresent();
	}

	@Override
	public synchronized void setPin(Optional<String> pin) {
		if (pin.isPresent()) {
			if (!PIN.matcher(pin.get()).matches()) {
				throw new DomainValidationException(List.of("The PIN must have 4 to 12 digits"));
			}
			settings.put(PIN_KEY, PinHash.hash(pin.get()));
		} else {
			settings.remove(PIN_KEY);
		}
		lockAll();
	}

	@Override
	public synchronized UnlockResult unlock(String pin) {
		var now = clock.instant();
		if (now.isBefore(blockedUntil)) {
			return new UnlockResult.Blocked(Duration.between(now, blockedUntil));
		}
		var stored = settings.get(PIN_KEY);
		if (stored.isEmpty() || (pin != null && PinHash.matches(pin, stored.get()))) {
			failures = 0;
			return new UnlockResult.Unlocked(epoch);
		}
		if (++failures >= MAX_FAILURES) {
			failures = 0;
			blockedUntil = now.plus(BLOCK_TIME);
			return new UnlockResult.Blocked(BLOCK_TIME);
		}
		return new UnlockResult.WrongPin();
	}

	@Override
	public long lockEpoch() {
		return epoch;
	}

	@Override
	public boolean networkAccess() {
		return settings.get(NETWORK_KEY).map(Boolean::parseBoolean).orElse(false);
	}

	@Override
	public void setNetworkAccess(boolean enabled) {
		settings.put(NETWORK_KEY, String.valueOf(enabled));
		events.publish(StateTopic.ACCESS);
	}

	@Override
	public boolean networkAccessActive() {
		return networkAccessActive;
	}

	@Override
	public boolean networkAccessForced() {
		return networkAccessForced;
	}

	private void lockAll() {
		epoch++;
		events.publish(StateTopic.ACCESS);
	}
}
