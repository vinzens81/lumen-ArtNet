package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.Folders;
import de.vinzens.lumen.domain.Validation;

import java.util.Objects;
import java.util.Optional;

/**
 * Editable properties of a composition.
 *
 * @param fadeSeconds   default fade into a step
 * @param holdSeconds   default hold of a step
 * @param intensityMode absolute: sets the intensity (HTP with others); relative: scales what lies below in the scene
 * @param generator     the effect the steps were generated from, if any
 * @param folder        the folder the composition is listed in, e.g. "Manual" or "Auto generated"
 */
public record CompositionSpec(String name, double fadeSeconds, double holdSeconds, PlayMode playMode,
		IntensityMode intensityMode, Optional<GeneratorSettings> generator, String folder) {

	public static final double MAX_SECONDS = 3600;
	/** Fade plus hold of a step: shorter steps would run faster than the output. */
	public static final double MIN_STEP_SECONDS = 0.02;
	/** Where compositions go unless told otherwise. */
	public static final String DEFAULT_FOLDER = Folders.MANUAL;
	/** Where quick setup puts what it makes. */
	public static final String GENERATED_FOLDER = Folders.GENERATED;

	public CompositionSpec {
		Objects.requireNonNull(playMode, "playMode");
		Objects.requireNonNull(intensityMode, "intensityMode");
		Objects.requireNonNull(generator, "generator");
		folder = Folders.normalize(folder);
		new Validation()
				.requireText(name, "name")
				.require(validTime(fadeSeconds) && validTime(holdSeconds),
						"fade and hold must be between 0 and " + (int) MAX_SECONDS + " s")
				.require(fadeSeconds + holdSeconds >= MIN_STEP_SECONDS,
						"fade and hold must together be at least " + MIN_STEP_SECONDS + " s")
				.require(folder.length() <= Folders.MAX_LENGTH,
						"folder must have at most " + Folders.MAX_LENGTH + " characters")
				.throwIfInvalid();
		name = name.strip();
	}

	/** In the default folder. */
	public CompositionSpec(String name, double fadeSeconds, double holdSeconds, PlayMode playMode,
			IntensityMode intensityMode, Optional<GeneratorSettings> generator) {
		this(name, fadeSeconds, holdSeconds, playMode, intensityMode, generator, DEFAULT_FOLDER);
	}

	/** A new composition: 1 s fade, 2 s hold, looping, absolute intensity. */
	public static CompositionSpec of(String name) {
		return new CompositionSpec(name, 1, 2, PlayMode.LOOP, IntensityMode.ABSOLUTE, Optional.empty());
	}

	public CompositionSpec withName(String newName) {
		return new CompositionSpec(newName, fadeSeconds, holdSeconds, playMode, intensityMode, generator, folder);
	}

	public CompositionSpec withFolder(String newFolder) {
		return new CompositionSpec(name, fadeSeconds, holdSeconds, playMode, intensityMode, generator, newFolder);
	}

	static boolean validTime(double seconds) {
		return seconds >= 0 && seconds <= MAX_SECONDS;
	}
}
