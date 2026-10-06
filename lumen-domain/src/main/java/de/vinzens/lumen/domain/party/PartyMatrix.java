package de.vinzens.lumen.domain.party;

import de.vinzens.lumen.domain.Validation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * The party mode of a project: a grid of {@link PartyCell fields} that switch scenes and compositions on and off.
 * Fields left out are empty. Lines can separate the rows.
 *
 * @param separators rows a line is drawn above (1 … rows − 1)
 */
public record PartyMatrix(int rows, int columns, Set<Integer> separators, List<PartyCell> cells) {

	public static final int MAX_SIZE = 12;
	/** A new project: four by four, empty. */
	public static final PartyMatrix EMPTY = new PartyMatrix(4, 4, Set.of(), List.of());

	public PartyMatrix {
		Objects.requireNonNull(separators, "separators");
		separators = Set.copyOf(new TreeSet<>(separators));
		cells = List.copyOf(cells);
		var validation = new Validation()
				.requireRange(rows, 1, MAX_SIZE, "rows")
				.requireRange(columns, 1, MAX_SIZE, "columns");
		for (var separator : separators) {
			validation.require(separator >= 1 && separator < rows,
					"a line between rows must be between 1 and " + (rows - 1) + ", was " + separator);
		}
		var used = new HashSet<PartyCell.Position>();
		for (var cell : cells) {
			validation.require(cell.row() < rows && cell.column() < columns,
					"field " + (cell.row() + 1) + "/" + (cell.column() + 1) + " is outside the matrix");
			validation.require(used.add(cell.position()),
					"field " + (cell.row() + 1) + "/" + (cell.column() + 1) + " is used twice");
		}
		validation.throwIfInvalid();
	}

	public Optional<PartyCell> cell(int row, int column) {
		return cells.stream().filter(cell -> cell.row() == row && cell.column() == column).findFirst();
	}
}
