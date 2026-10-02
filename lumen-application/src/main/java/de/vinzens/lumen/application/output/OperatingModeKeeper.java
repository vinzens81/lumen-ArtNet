package de.vinzens.lumen.application.output;

import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.output.port.in.ControlModeUseCase;
import de.vinzens.lumen.application.settings.port.out.SettingsRepository;
import de.vinzens.lumen.domain.output.OperatingMode;

import java.lang.System.Logger.Level;
import java.util.Objects;
import java.util.Optional;

/**
 * Saves the operating mode and restores it on start: a server that restarts during a performance comes back in
 * show mode with the programmer locked.
 */
public class OperatingModeKeeper {

	static final String KEY = "operating_mode";
	private static final System.Logger LOG = System.getLogger(OperatingModeKeeper.class.getName());

	private final ControlModeUseCase mode;
	private final SettingsRepository settings;

	public OperatingModeKeeper(ControlModeUseCase mode, SettingsRepository settings, StateEvents events) {
		this.mode = Objects.requireNonNull(mode, "mode");
		this.settings = Objects.requireNonNull(settings, "settings");
		settings.get(KEY).flatMap(OperatingModeKeeper::parse).ifPresent(mode::setMode);
		events.subscribe(topic -> {
			if (topic == StateTopic.MODE) {
				save();
			}
		});
	}

	private void save() {
		settings.put(KEY, mode.mode().name());
	}

	private static Optional<OperatingMode> parse(String value) {
		try {
			return Optional.of(OperatingMode.valueOf(value));
		} catch (IllegalArgumentException e) {
			LOG.log(Level.WARNING, "Ignoring unknown saved operating mode ''{0}''", value);
			return Optional.empty();
		}
	}
}
