package de.vinzens.lumen.domain.party;

import de.vinzens.lumen.domain.Validation;
import de.vinzens.lumen.domain.composition.KeyMode;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * A field of the party matrix: a button that plays a scene or a composition, or just a text.
 *
 * @param row           0-based
 * @param column        0-based
 * @param label         shown on the field, empty = the name of what it plays
 * @param sceneId       the scene it plays (all its compositions)
 * @param compositionId the composition it plays; at most one of scene and composition, none = a text field
 * @param color         fill color {@code #rrggbb}
 * @param borderColor   border color {@code #rrggbb}
 * @param mode          toggle (on until pressed again) or flash (on while held)
 * @param group         cells with the same group name exclude each other: only one of them is on
 */
public record PartyCell(int row, int column, Optional<String> label, Optional<Long> sceneId,
		Optional<Long> compositionId, String color, String borderColor, KeyMode mode, Optional<String> group) {

	public static final String DEFAULT_COLOR = "#1c2230";
	public static final String DEFAULT_BORDER = "#3cf0ff";

	public PartyCell {
		Objects.requireNonNull(label, "label");
		Objects.requireNonNull(sceneId, "sceneId");
		Objects.requireNonNull(compositionId, "compositionId");
		Objects.requireNonNull(mode, "mode");
		Objects.requireNonNull(group, "group");
		label = label.map(String::strip).filter(text -> !text.isEmpty());
		group = group.map(String::strip).filter(text -> !text.isEmpty());
		new Validation()
				.require(row >= 0 && column >= 0, "row and column must not be negative")
				.require(sceneId.isEmpty() || compositionId.isEmpty(),
						"a field plays either a scene or a composition, not both")
				.require(isColor(color), "color must look like #ff8800, was '" + color + "'")
				.require(isColor(borderColor), "borderColor must look like #ff8800, was '" + borderColor + "'")
				.require(label.map(text -> text.length() <= 60).orElse(true), "label must have at most 60 characters")
				.throwIfInvalid();
		color = color.toLowerCase(Locale.ROOT);
		borderColor = borderColor.toLowerCase(Locale.ROOT);
	}

	/** A field that only shows a text. */
	public static PartyCell text(int row, int column, String text) {
		return new PartyCell(row, column, Optional.of(text), Optional.empty(), Optional.empty(), DEFAULT_COLOR,
				DEFAULT_BORDER, KeyMode.TOGGLE, Optional.empty());
	}

	/** A toggle field playing a composition. */
	public static PartyCell composition(int row, int column, long compositionId) {
		return new PartyCell(row, column, Optional.empty(), Optional.empty(), Optional.of(compositionId),
				DEFAULT_COLOR, DEFAULT_BORDER, KeyMode.TOGGLE, Optional.empty());
	}

	/** A toggle field playing a scene. */
	public static PartyCell scene(int row, int column, long sceneId) {
		return new PartyCell(row, column, Optional.empty(), Optional.of(sceneId), Optional.empty(), DEFAULT_COLOR,
				DEFAULT_BORDER, KeyMode.TOGGLE, Optional.empty());
	}

	public PartyCell inGroup(String name) {
		return new PartyCell(row, column, label, sceneId, compositionId, color, borderColor, mode, Optional.of(name));
	}

	public PartyCell withMode(KeyMode newMode) {
		return new PartyCell(row, column, label, sceneId, compositionId, color, borderColor, newMode, group);
	}

	/** Whether the field switches something (otherwise it only shows a text). */
	public boolean playsSomething() {
		return sceneId.isPresent() || compositionId.isPresent();
	}

	public Position position() {
		return new Position(row, column);
	}

	private static boolean isColor(String value) {
		return value != null && value.matches("#[0-9a-fA-F]{6}");
	}

	/** Where a field sits in the matrix. */
	public record Position(int row, int column) {
	}
}
