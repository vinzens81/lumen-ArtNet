package de.vinzens.lumen.domain.party;

import de.vinzens.lumen.domain.Validation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * A page of the party matrix, shown as a tab: a grid of {@link PartyCell fields} that switch scenes and compositions
 * on and off. Fields left out are empty. A field can cover several columns and rows. Lines can separate the rows.
 *
 * @param name       shown on the tab
 * @param separators rows a line is drawn above (1 … rows − 1)
 */
public record PartyPage(String name, int rows, int columns, Set<Integer> separators, List<PartyCell> cells) {

	public static final int MAX_SIZE = 12;
	public static final int MAX_NAME = 40;
	public static final String DEFAULT_NAME = "Party";
	/** A new page: four by four, empty. */
	public static final PartyPage EMPTY = new PartyPage(DEFAULT_NAME, 4, 4, Set.of(), List.of());

	public PartyPage {
		Objects.requireNonNull(separators, "separators");
		name = name == null ? "" : name.strip();
		separators = Set.copyOf(new TreeSet<>(separators));
		cells = List.copyOf(cells);
		var validation = new Validation()
				.require(!name.isEmpty(), "a page needs a name")
				.require(name.length() <= MAX_NAME, "a page name has at most " + MAX_NAME + " characters")
				.requireRange(rows, 1, MAX_SIZE, "rows")
				.requireRange(columns, 1, MAX_SIZE, "columns");
		for (var separator : separators) {
			validation.require(separator >= 1 && separator < rows,
					"a line between rows must be between 1 and " + (rows - 1) + ", was " + separator);
		}
		var used = new HashSet<PartyCell.Position>();
		for (var cell : cells) {
			var field = "field " + (cell.row() + 1) + "/" + (cell.column() + 1);
			validation.require(cell.row() + cell.rowSpan() <= rows && cell.column() + cell.span() <= columns,
					field + " is outside the matrix");
			var free = true;
			for (int row = cell.row(); row < cell.row() + cell.rowSpan(); row++) {
				for (int column = cell.column(); column < cell.column() + cell.span(); column++) {
					free &= used.add(new PartyCell.Position(row, column));
				}
			}
			validation.require(free, field + " overlaps another field");
		}
		validation.throwIfInvalid();
	}

	/** A page with the default name. */
	public PartyPage(int rows, int columns, Set<Integer> separators, List<PartyCell> cells) {
		this(DEFAULT_NAME, rows, columns, separators, cells);
	}

	/** The field starting at the place. */
	public Optional<PartyCell> cell(int row, int column) {
		return cells.stream().filter(cell -> cell.row() == row && cell.column() == column).findFirst();
	}

	/** Whether no field reaches into the row. */
	public boolean isRowEmpty(int row) {
		return cells.stream().noneMatch(cell -> row >= cell.row() && row < cell.row() + cell.rowSpan());
	}

	/** The rows below the last field: from here on the page is empty. */
	public int firstFreeRow() {
		return cells.stream().mapToInt(cell -> cell.row() + cell.rowSpan()).max().orElse(0);
	}

	public PartyPage withName(String newName) {
		return new PartyPage(newName, rows, columns, separators, cells);
	}
}
