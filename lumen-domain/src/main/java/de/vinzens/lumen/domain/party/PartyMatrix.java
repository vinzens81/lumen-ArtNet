package de.vinzens.lumen.domain.party;

import de.vinzens.lumen.domain.Validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * The party mode of a project: one or more {@link PartyPage pages} of fields, shown as tabs. Groups reach over all
 * pages: a field switched on ends the others of its group on every page.
 */
public record PartyMatrix(List<PartyPage> pages) {

	public static final int MAX_PAGES = 12;
	/** A new project: one empty page. */
	public static final PartyMatrix EMPTY = new PartyMatrix(List.of(PartyPage.EMPTY));

	public PartyMatrix {
		Objects.requireNonNull(pages, "pages");
		pages = List.copyOf(pages);
		new Validation()
				.require(!pages.isEmpty(), "the party matrix needs at least one page")
				.require(pages.size() <= MAX_PAGES, "the party matrix has at most " + MAX_PAGES + " pages")
				.throwIfInvalid();
	}

	public static PartyMatrix of(PartyPage... pages) {
		return new PartyMatrix(List.of(pages));
	}

	/** One page of the default name. */
	public PartyMatrix(int rows, int columns, Set<Integer> separators, List<PartyCell> cells) {
		this(List.of(new PartyPage(rows, columns, separators, cells)));
	}

	public Optional<PartyPage> page(int index) {
		return index >= 0 && index < pages.size() ? Optional.of(pages.get(index)) : Optional.empty();
	}

	/** Every field with the page it is on. */
	public Stream<Placed> placedCells() {
		return IntStream.range(0, pages.size()).boxed()
				.flatMap(index -> pages.get(index).cells().stream().map(cell -> new Placed(index, cell)));
	}

	/** The same matrix with one page replaced. */
	public PartyMatrix withPage(int index, PartyPage page) {
		var next = new ArrayList<>(pages);
		next.set(index, page);
		return new PartyMatrix(next);
	}

	/** The same matrix with a page added at the end. */
	public PartyMatrix withNewPage(PartyPage page) {
		var next = new ArrayList<>(pages);
		next.add(page);
		return new PartyMatrix(next);
	}

	/**
	 * A field and the page it is on.
	 *
	 * @param page index of the page, from 0
	 */
	public record Placed(int page, PartyCell cell) {

		public PartyPlace place() {
			return new PartyPlace(page, cell.row(), cell.column());
		}

		/** "Field 2/3" on the first page, "Field 2/3 on page 2" on the others. */
		public String describe() {
			return place().describe();
		}
	}
}
