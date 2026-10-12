package de.vinzens.lumen.domain.party;

/**
 * Where a field sits in the party matrix, all from 0.
 *
 * @param page index of the page
 */
public record PartyPlace(int page, int row, int column) {

	/** "Field 2/3" on the first page, "Field 2/3 on page 2" on the others – counted from 1. */
	public String describe() {
		return "Field " + (row + 1) + "/" + (column + 1) + (page == 0 ? "" : " on page " + (page + 1));
	}
}
