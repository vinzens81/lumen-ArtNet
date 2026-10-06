package de.vinzens.lumen.domain.scene;

import de.vinzens.lumen.domain.Validation;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Editable properties of a scene.
 *
 * @param cueNote    note for the operator, e.g. "GO on: '…and then the sun rose'"
 * @param autoFollow if set, the next scene starts automatically this long after the fade-in has finished
 * @param outsideCueList GO skips the scene; it is played by a fader (e.g. house lights)
 * @param timing     fade times of the GO into this scene
 * @param script     text spoken on stage during this scene, empty if none; {@code **…**} marks stage directions
 */
public record SceneSpec(SceneNumber number, String name, Optional<String> cueNote, Optional<Duration> autoFollow,
		boolean outsideCueList, SceneTiming timing, String script) {

	public static final Duration MAX_AUTO_FOLLOW = Duration.ofHours(1);
	/** About 50 printed pages – far more than one scene needs. */
	public static final int MAX_SCRIPT_LENGTH = 100_000;

	public SceneSpec {
		Objects.requireNonNull(cueNote, "cueNote");
		Objects.requireNonNull(autoFollow, "autoFollow");
		Objects.requireNonNull(timing, "timing");
		Objects.requireNonNull(script, "script");
		new Validation()
				.require(number != null, "number is missing")
				.requireText(name, "name")
				.require(autoFollow.map(d -> d.isPositive() && d.compareTo(MAX_AUTO_FOLLOW) <= 0).orElse(true),
						"autoFollow must be between 0 and " + MAX_AUTO_FOLLOW.toMinutes() + " minutes")
				.require(script.length() <= MAX_SCRIPT_LENGTH,
						"script must have at most " + MAX_SCRIPT_LENGTH + " characters")
				.throwIfInvalid();
		name = name.strip();
		cueNote = cueNote.map(String::strip).filter(n -> !n.isEmpty());
		script = script.replace("\r\n", "\n").replace('\r', '\n').strip();
	}

	/** A scene without text. */
	public SceneSpec(SceneNumber number, String name, Optional<String> cueNote, Optional<Duration> autoFollow,
			boolean outsideCueList, SceneTiming timing) {
		this(number, name, cueNote, autoFollow, outsideCueList, timing, "");
	}

	/** A scene with the project's fade times. */
	public SceneSpec(SceneNumber number, String name, Optional<String> cueNote, Optional<Duration> autoFollow,
			boolean outsideCueList) {
		this(number, name, cueNote, autoFollow, outsideCueList, SceneTiming.DEFAULT);
	}

	/** A scene of the cue list with the project's fade times. */
	public SceneSpec(SceneNumber number, String name, Optional<String> cueNote, Optional<Duration> autoFollow) {
		this(number, name, cueNote, autoFollow, false);
	}
}
