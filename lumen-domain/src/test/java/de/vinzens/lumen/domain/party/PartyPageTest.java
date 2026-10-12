package de.vinzens.lumen.domain.party;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PartyPageTest {

	private static final PartyFader DIMMER = new PartyFader(PartyFader.Kind.DIMMER, List.of(1L));

	@Test
	void aFieldCanCoverSeveralColumns() {
		var matrix = new PartyPage(2, 4, Set.of(), List.of(PartyCell.fader(0, 0, 3, DIMMER),
				PartyCell.composition(0, 3, 7)));

		assertThat(matrix.cells()).hasSize(2);
		assertThat(matrix.isRowEmpty(1)).isTrue();
		assertThat(matrix.cell(0, 0).orElseThrow().covers(2)).isTrue();
	}

	@Test
	void fieldsMustNotOverlapOrReachOutOfTheMatrix() {
		assertThatThrownBy(() -> new PartyPage(1, 4, Set.of(), List.of(PartyCell.fader(0, 0, 3, DIMMER),
				PartyCell.composition(0, 2, 7))))
				.hasMessageContaining("field 1/3 overlaps another field");
		assertThatThrownBy(() -> new PartyPage(1, 4, Set.of(), List.of(PartyCell.fader(0, 2, 3, DIMMER))))
				.hasMessageContaining("outside the matrix");
	}

	@Test
	void aFieldCanCoverSeveralRowsAndNothingMayReachIntoThem() {
		var pad = PartyCell.fader(1, 2, 2, 2, new PartyFader(PartyFader.Kind.PAN_TILT, List.of(1L)));
		var page = new PartyPage("Movers", 3, 4, Set.of(2), List.of(pad));

		assertThat(page.isRowEmpty(2)).isFalse();
		assertThat(page.isRowEmpty(0)).isTrue();
		assertThat(page.firstFreeRow()).isEqualTo(3);
		assertThatThrownBy(() -> new PartyPage(3, 4, Set.of(), List.of(pad, PartyCell.composition(2, 3, 7))))
				.hasMessageContaining("field 3/4 overlaps another field");
		assertThatThrownBy(() -> new PartyPage(2, 4, Set.of(), List.of(pad)))
				.hasMessageContaining("outside the matrix");
	}

	@Test
	void aMatrixHasOneToTwelveNamedPages() {
		var matrix = PartyMatrix.of(PartyPage.EMPTY, new PartyPage("Movers", 2, 2, Set.of(),
				List.of(PartyCell.composition(1, 1, 7))));

		assertThat(matrix.placedCells().map(PartyMatrix.Placed::describe)).containsExactly("Field 2/2 on page 2");
		assertThatThrownBy(() -> new PartyMatrix(List.of())).hasMessageContaining("at least one page");
		assertThatThrownBy(() -> new PartyPage(" ", 2, 2, Set.of(), List.of())).hasMessageContaining("needs a name");
		assertThatThrownBy(() -> new PartyMatrix(java.util.Collections.nCopies(13, PartyPage.EMPTY)))
				.hasMessageContaining("at most 12 pages");
	}

	@Test
	void aFieldPlaysAGradientOrSomethingElse() {
		assertThatThrownBy(() -> PartyCell.composition(0, 0, 7).withFader(java.util.Optional.of(DIMMER)))
				.hasMessageContaining("not several");
		assertThatThrownBy(() -> new PartyFader(PartyFader.Kind.COLOR, List.of()))
				.hasMessageContaining("at least one fixture");
	}
}
