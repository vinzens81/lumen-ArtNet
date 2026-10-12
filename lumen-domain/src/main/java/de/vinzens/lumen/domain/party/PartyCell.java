package de.vinzens.lumen.domain.party;

import de.vinzens.lumen.domain.Validation;
import de.vinzens.lumen.domain.composition.KeyMode;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A field of the party matrix: a button that plays a scene, a composition or a move, a gradient that sets intensity
 * or color, or just a text. A field can reach over several columns to the right.
 *
 * @param row           0-based
 * @param column        0-based
 * @param label         shown on the field, empty = the name of what it plays
 * @param sceneId       the scene it plays (all its compositions)
 * @param compositionId the composition it plays; at most one of scene, composition, move and gradient, none = a text
 *                      field
 * @param moveId        the move it plays
 * @param color         fill color {@code #rrggbb}
 * @param borderColor   border color {@code #rrggbb}
 * @param mode          toggle (on until pressed again) or flash (on while held)
 * @param group         cells with the same group name exclude each other: only one of them is on
 * @param span          number of columns the field covers, from {@code column} to the right
 * @param fader         the gradient it shows
 */
public record PartyCell(int row, int column, Optional<String> label, Optional<Long> sceneId,
		Optional<Long> compositionId, Optional<Long> moveId, String color, String borderColor, KeyMode mode,
		Optional<String> group, int span, Optional<PartyFader> fader) {

	public static final String DEFAULT_COLOR = "#1c2230";
	public static final String DEFAULT_BORDER = "#3cf0ff";

	public PartyCell {
		Objects.requireNonNull(label, "label");
		Objects.requireNonNull(sceneId, "sceneId");
		Objects.requireNonNull(compositionId, "compositionId");
		Objects.requireNonNull(moveId, "moveId");
		Objects.requireNonNull(mode, "mode");
		Objects.requireNonNull(group, "group");
		Objects.requireNonNull(fader, "fader");
		label = label.map(String::strip).filter(text -> !text.isEmpty());
		group = group.map(String::strip).filter(text -> !text.isEmpty());
		new Validation()
				.require(row >= 0 && column >= 0, "row and column must not be negative")
				.require(span >= 1 && span <= PartyMatrix.MAX_SIZE,
						"a field covers 1 to " + PartyMatrix.MAX_SIZE + " columns, was " + span)
				.require(Stream.of(sceneId, compositionId, moveId, fader).filter(Optional::isPresent).count() <= 1,
						"a field plays a scene, a composition, a move or a gradient, not several")
				.require(isColor(color), "color must look like #ff8800, was '" + color + "'")
				.require(isColor(borderColor), "borderColor must look like #ff8800, was '" + borderColor + "'")
				.require(label.map(text -> text.length() <= 60).orElse(true), "label must have at most 60 characters")
				.throwIfInvalid();
		color = color.toLowerCase(Locale.ROOT);
		borderColor = borderColor.toLowerCase(Locale.ROOT);
	}

	/** A field of one column without a gradient. */
	public PartyCell(int row, int column, Optional<String> label, Optional<Long> sceneId, Optional<Long> compositionId,
			Optional<Long> moveId, String color, String borderColor, KeyMode mode, Optional<String> group) {
		this(row, column, label, sceneId, compositionId, moveId, color, borderColor, mode, group, 1, Optional.empty());
	}

	/** A field of one column without a move. */
	public PartyCell(int row, int column, Optional<String> label, Optional<Long> sceneId, Optional<Long> compositionId,
			String color, String borderColor, KeyMode mode, Optional<String> group) {
		this(row, column, label, sceneId, compositionId, Optional.empty(), color, borderColor, mode, group);
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

	/** A toggle field playing a move. */
	public static PartyCell move(int row, int column, long moveId) {
		return new PartyCell(row, column, Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(moveId),
				DEFAULT_COLOR, DEFAULT_BORDER, KeyMode.TOGGLE, Optional.empty());
	}

	/** A gradient field over {@code span} columns. */
	public static PartyCell fader(int row, int column, int span, PartyFader fader) {
		return new PartyCell(row, column, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
				DEFAULT_COLOR, DEFAULT_BORDER, KeyMode.TOGGLE, Optional.empty(), span, Optional.of(fader));
	}

	public PartyCell inGroup(String name) {
		return new PartyCell(row, column, label, sceneId, compositionId, moveId, color, borderColor, mode,
				Optional.of(name), span, fader);
	}

	public PartyCell withMode(KeyMode newMode) {
		return new PartyCell(row, column, label, sceneId, compositionId, moveId, color, borderColor, newMode, group,
				span, fader);
	}

	public PartyCell withLabel(String text) {
		return new PartyCell(row, column, Optional.of(text), sceneId, compositionId, moveId, color, borderColor, mode,
				group, span, fader);
	}

	public PartyCell withColors(String fill, String border) {
		return new PartyCell(row, column, label, sceneId, compositionId, moveId, fill, border, mode, group, span,
				fader);
	}

	/** The same field with another gradient (the gradient without fixtures: the field shows only its text). */
	public PartyCell withFader(Optional<PartyFader> newFader) {
		return new PartyCell(row, column, label, sceneId, compositionId, moveId, color, borderColor, mode, group, span,
				newFader);
	}

	/** Whether the field switches something (otherwise it only shows a text). */
	public boolean playsSomething() {
		return sceneId.isPresent() || compositionId.isPresent() || moveId.isPresent() || fader.isPresent();
	}

	/** Whether the field covers the given column of its row. */
	public boolean covers(int columnIndex) {
		return columnIndex >= column && columnIndex < column + span;
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
