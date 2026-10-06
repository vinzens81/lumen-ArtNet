package de.vinzens.lumen.domain.party;

import de.vinzens.lumen.domain.composition.ActiveComposition;
import de.vinzens.lumen.domain.composition.FixtureAttributes;
import de.vinzens.lumen.domain.composition.KeyMode;
import de.vinzens.lumen.domain.composition.StepSequence;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.party.PartySwitchboard.PlayableCell;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PartySwitchboardTest {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	private static final Attribute RED = Attribute.of(ChannelFunction.RED);
	private static final Attribute BLUE = Attribute.of(ChannelFunction.BLUE);
	private static final Instant NOW = Instant.parse("2026-10-06T22:00:00Z");

	private final PartySwitchboard board = new PartySwitchboard();

	@Test
	void fieldsInAGroupExcludeEachOtherOthersRunInParallel() {
		var red = playable(PartyCell.composition(0, 0, 1).inGroup("Colors"), 1, Map.of(RED, 1.0));
		var blue = playable(PartyCell.composition(0, 1, 2).inGroup("Colors"), 2, Map.of(BLUE, 1.0));
		var strobe = playable(PartyCell.composition(1, 0, 3), 3, Map.of(DIMMER, 1.0));
		board.configure(List.of(red, blue, strobe));

		board.press(0, 0, NOW);
		board.press(1, 0, NOW);
		board.press(0, 1, NOW);

		assertThat(board.onPositions()).containsExactlyInAnyOrder(new PartyCell.Position(0, 1),
				new PartyCell.Position(1, 0));
		board.press(1, 0, NOW);
		assertThat(board.isOn(1, 0)).as("a toggle goes off again").isFalse();
	}

	@Test
	void collidingFieldsTakeTheHigherValueOfEveryAttribute() {
		var warm = playable(PartyCell.composition(0, 0, 1), 1, Map.of(DIMMER, 0.4, RED, 1.0, BLUE, 0.2));
		var cold = playable(PartyCell.composition(0, 1, 2), 2, Map.of(DIMMER, 0.8, RED, 0.1, BLUE, 0.9));
		board.configure(List.of(warm, cold));
		board.press(0, 0, NOW);
		board.press(0, 1, NOW);

		var values = board.output(NOW, FixtureAttributes.ALL).get(5L);

		assertThat(values.get(DIMMER)).contains(0.8);
		assertThat(values.get(RED)).contains(1.0);
		assertThat(values.get(BLUE)).contains(0.9);
	}

	@Test
	void aFlashFieldIsOnWhileHeld() {
		board.configure(List.of(playable(PartyCell.composition(0, 0, 1).withMode(KeyMode.FLASH), 1,
				Map.of(DIMMER, 1.0))));

		board.press(0, 0, NOW);
		assertThat(board.output(NOW, FixtureAttributes.ALL)).containsKey(5L);
		board.release(0, 0);

		assertThat(board.output(NOW, FixtureAttributes.ALL)).isEmpty();
	}

	@Test
	void aFieldStaysOnWhileItPlaysTheSameAfterAChange() {
		var a = playable(PartyCell.composition(0, 0, 1), 1, Map.of(DIMMER, 1.0));
		var b = playable(PartyCell.composition(0, 1, 2), 2, Map.of(DIMMER, 1.0));
		board.configure(List.of(a, b));
		board.press(0, 0, NOW);
		board.press(0, 1, NOW);

		board.configure(List.of(a, playable(PartyCell.composition(0, 1, 7), 7, Map.of(DIMMER, 1.0))));

		assertThat(board.onPositions()).containsExactly(new PartyCell.Position(0, 0));
	}

	@Test
	void textFieldsAndEmptyPlacesCannotBePressed() {
		board.configure(List.of(new PlayableCell(PartyCell.text(0, 0, "Colors"), List.of())));

		assertThatThrownBy(() -> board.press(0, 0, NOW)).hasMessageContaining("plays nothing");
		assertThatThrownBy(() -> board.press(3, 3, NOW)).hasMessageContaining("plays nothing");
	}

	@Test
	void theMatrixKeepsItsFieldsInsideAndApart() {
		assertThatThrownBy(() -> new PartyMatrix(2, 2, Set.of(), List.of(PartyCell.text(2, 0, "x"))))
				.hasMessageContaining("outside");
		assertThatThrownBy(() -> new PartyMatrix(2, 2, Set.of(), List.of(PartyCell.text(0, 0, "x"),
				PartyCell.composition(0, 0, 1)))).hasMessageContaining("twice");
		assertThatThrownBy(() -> new PartyMatrix(2, 2, Set.of(2), List.of())).hasMessageContaining("line");
		assertThatThrownBy(() -> new PartyCell(0, 0, Optional.empty(), Optional.of(1L), Optional.of(2L), "#000000",
				"#ffffff", KeyMode.TOGGLE, Optional.empty())).hasMessageContaining("either");
		assertThat(new PartyMatrix(3, 2, Set.of(1, 2), List.of()).separators()).containsExactlyInAnyOrder(1, 2);
	}

	private static PlayableCell playable(PartyCell cell, long compositionId, Map<Attribute, Double> values) {
		var look = new StepSequence(List.of(new StepSequence.Step(1, 0, Map.of(5L, new FixtureValues(values)))),
				StepSequence.PlayMode.LOOP);
		return new PlayableCell(cell, List.of(new ActiveComposition(compositionId, Optional.of(compositionId), look,
				List.of(5L), 1, 1, false)));
	}
}
