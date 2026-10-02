package de.vinzens.lumen.application.access;

import de.vinzens.lumen.application.access.port.in.ManageAccessUseCase.UnlockResult;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.output.port.in.ControlModeUseCase;
import de.vinzens.lumen.application.settings.port.out.SettingsRepository;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.output.OperatingMode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessServiceTest {

	private final Map<String, String> stored = new HashMap<>();
	private final StateEvents events = new StateEvents();
	private OperatingMode currentMode = OperatingMode.SETUP;
	private Instant now = Instant.parse("2026-10-02T20:00:00Z");
	private final AccessService access = new AccessService(settings(), mode(), clock(), events, false);

	@Test
	void withoutPinEveryoneIsUnlocked() {
		assertThat(access.pinSet()).isFalse();
		assertThat(access.unlock(null)).isInstanceOf(UnlockResult.Unlocked.class);
	}

	@Test
	void pinIsStoredHashedAndChecked() {
		access.setPin(Optional.of("4711"));

		assertThat(access.pinSet()).isTrue();
		assertThat(stored.get(AccessService.PIN_KEY)).startsWith("pbkdf2-sha256$").doesNotContain("4711");
		assertThat(access.unlock("4711")).isEqualTo(new UnlockResult.Unlocked(access.lockEpoch()));
		assertThat(access.unlock("1234")).isInstanceOf(UnlockResult.WrongPin.class);
		assertThat(access.unlock(null)).isInstanceOf(UnlockResult.WrongPin.class);
		assertThatThrownBy(() -> access.setPin(Optional.of("12a4"))).isInstanceOf(DomainValidationException.class);
		assertThatThrownBy(() -> access.setPin(Optional.of("123"))).isInstanceOf(DomainValidationException.class);

		access.setPin(Optional.empty());
		assertThat(access.pinSet()).isFalse();
	}

	@Test
	void showModeAndPinChangesLockAllClients() {
		var epoch = access.lockEpoch();
		var published = new ArrayList<StateTopic>();
		events.subscribe(published::add);

		currentMode = OperatingMode.SHOW;
		events.publish(StateTopic.MODE);
		assertThat(access.lockEpoch()).isGreaterThan(epoch);
		epoch = access.lockEpoch();

		events.publish(StateTopic.MODE); // still show: no new lock
		assertThat(access.lockEpoch()).isEqualTo(epoch);

		access.setPin(Optional.of("0000"));
		assertThat(access.lockEpoch()).isGreaterThan(epoch);
		assertThat(published).contains(StateTopic.ACCESS);
	}

	@Test
	void blocksAfterTooManyWrongPins() {
		access.setPin(Optional.of("4711"));
		for (int i = 1; i < AccessService.MAX_FAILURES; i++) {
			assertThat(access.unlock("0000")).isInstanceOf(UnlockResult.WrongPin.class);
		}
		assertThat(access.unlock("0000")).isEqualTo(new UnlockResult.Blocked(AccessService.BLOCK_TIME));

		now = now.plusSeconds(10);
		assertThat(access.unlock("4711")).as("even the right PIN").isEqualTo(new UnlockResult.Blocked(Duration.ofSeconds(20)));

		now = now.plusSeconds(20);
		assertThat(access.unlock("4711")).isInstanceOf(UnlockResult.Unlocked.class);
	}

	@Test
	void networkAccessIsASetting() {
		assertThat(access.networkAccess()).isFalse();
		access.setNetworkAccess(true);
		assertThat(access.networkAccess()).isTrue();
		assertThat(stored).containsEntry(AccessService.NETWORK_KEY, "true");
		assertThat(access.networkAccessActive()).as("after a restart").isFalse();
		assertThat(new AccessService(settings(), mode(), clock(), events, false).networkAccessActive()).isTrue();
	}

	private SettingsRepository settings() {
		return new SettingsRepository() {
			@Override
			public Optional<String> get(String key) {
				return Optional.ofNullable(stored.get(key));
			}

			@Override
			public void put(String key, String value) {
				stored.put(key, value);
			}

			@Override
			public void remove(String key) {
				stored.remove(key);
			}
		};
	}

	private ControlModeUseCase mode() {
		return new ControlModeUseCase() {
			@Override
			public OperatingMode mode() {
				return currentMode;
			}

			@Override
			public OperatingMode setMode(OperatingMode mode) {
				currentMode = mode;
				return mode;
			}
		};
	}

	private Clock clock() {
		return new Clock() {
			@Override
			public ZoneId getZone() {
				return ZoneOffset.UTC;
			}

			@Override
			public Clock withZone(ZoneId zone) {
				return this;
			}

			@Override
			public Instant instant() {
				return now;
			}
		};
	}
}
