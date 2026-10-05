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
 * @param keyOnly    the scene is only played by extra keys: GO skips it, it is not part of the cue list
 * @param timing     fade times of the GO into this scene
 */
public record SceneSpec(SceneNumber number, String name, Optional<String> cueNote, Optional<Duration> autoFollow,
		boolean keyOnly, SceneTiming timing) {

	public static final Duration MAX_AUTO_FOLLOW = Duration.ofHours(1);

	public SceneSpec {
		Objects.requireNonNull(cueNote, "cueNote");
		Objects.requireNonNull(autoFollow, "autoFollow");
		Objects.requireNonNull(timing, "timing");
		new Validation()
				.require(number != null, "number is missing")
				.requireText(name, "name")
				.require(autoFollow.map(d -> d.isPositive() && d.compareTo(MAX_AUTO_FOLLOW) <= 0).orElse(true),
						"autoFollow must be between 0 and " + MAX_AUTO_FOLLOW.toMinutes() + " minutes")
				.throwIfInvalid();
		name = name.strip();
		cueNote = cueNote.map(String::strip).filter(n -> !n.isEmpty());
	}

	/** A scene with the project's fade times. */
	public SceneSpec(SceneNumber number, String name, Optional<String> cueNote, Optional<Duration> autoFollow,
			boolean keyOnly) {
		this(number, name, cueNote, autoFollow, keyOnly, SceneTiming.DEFAULT);
	}

	/** A scene of the cue list with the project's fade times. */
	public SceneSpec(SceneNumber number, String name, Optional<String> cueNote, Optional<Duration> autoFollow) {
		this(number, name, cueNote, autoFollow, false);
	}
}
