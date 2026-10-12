package de.vinzens.lumen.domain.party;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PartyMatrixTest {

	private static final PartyFader DIMMER = new PartyFader(PartyFader.Kind.DIMMER, List.of(1L));

	@Test
	void aFieldCanCoverSeveralColumns() {
		var matrix = new PartyMatrix(2, 4, Set.of(), List.of(PartyCell.fader(0, 0, 3, DIMMER),
				PartyCell.composition(0, 3, 7)));

		assertThat(matrix.cells()).hasSize(2);
		assertThat(matrix.isRowEmpty(1)).isTrue();
		assertThat(matrix.cell(0, 0).orElseThrow().covers(2)).isTrue();
	}

	@Test
	void fieldsMustNotOverlapOrReachOutOfTheMatrix() {
		assertThatThrownBy(() -> new PartyMatrix(1, 4, Set.of(), List.of(PartyCell.fader(0, 0, 3, DIMMER),
				PartyCell.composition(0, 2, 7))))
				.hasMessageContaining("field 1/3 overlaps another field");
		assertThatThrownBy(() -> new PartyMatrix(1, 4, Set.of(), List.of(PartyCell.fader(0, 2, 3, DIMMER))))
				.hasMessageContaining("outside the matrix");
	}

	@Test
	void aFieldPlaysAGradientOrSomethingElse() {
		assertThatThrownBy(() -> PartyCell.composition(0, 0, 7).withFader(java.util.Optional.of(DIMMER)))
				.hasMessageContaining("not several");
		assertThatThrownBy(() -> new PartyFader(PartyFader.Kind.COLOR, List.of()))
				.hasMessageContaining("at least one fixture");
	}
}
