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
	private static final Attribute PAN = Attribute.of(ChannelFunction.PAN);
	private static final Attribute TILT = Attribute.of(ChannelFunction.TILT);
	private static final Instant NOW = Instant.parse("2026-10-06T22:00:00Z");

	private final PartySwitchboard board = new PartySwitchboard();

	@Test
	void fieldsInAGroupExcludeEachOtherOthersRunInParallel() {
		var red = playable(PartyCell.composition(0, 0, 1).inGroup("Colors"), 1, Map.of(RED, 1.0));
		var blue = playable(PartyCell.composition(0, 1, 2).inGroup("Colors"), 2, Map.of(BLUE, 1.0));
		var strobe = playable(PartyCell.composition(1, 0, 3), 3, Map.of(DIMMER, 1.0));
		board.configure(List.of(red, blue, strobe));

		board.press(at(0, 0), NOW);
		board.press(at(1, 0), NOW);
		board.press(at(0, 1), NOW);

		assertThat(board.onPlaces()).containsExactlyInAnyOrder(at(0, 1),
				at(1, 0));
		board.press(at(1, 0), NOW);
		assertThat(board.isOn(at(1, 0))).as("a toggle goes off again").isFalse();
	}

	@Test
	void collidingFieldsTakeTheHigherValueOfEveryAttribute() {
		var warm = playable(PartyCell.composition(0, 0, 1), 1, Map.of(DIMMER, 0.4, RED, 1.0, BLUE, 0.2));
		var cold = playable(PartyCell.composition(0, 1, 2), 2, Map.of(DIMMER, 0.8, RED, 0.1, BLUE, 0.9));
		board.configure(List.of(warm, cold));
		board.press(at(0, 0), NOW);
		board.press(at(0, 1), NOW);

		var values = board.output(NOW, FixtureAttributes.ALL).get(5L);

		assertThat(values.get(DIMMER)).contains(0.8);
		assertThat(values.get(RED)).contains(1.0);
		assertThat(values.get(BLUE)).contains(0.9);
	}

	@Test
	void aFlashFieldIsOnWhileHeld() {
		board.configure(List.of(playable(PartyCell.composition(0, 0, 1).withMode(KeyMode.FLASH), 1,
				Map.of(DIMMER, 1.0))));

		board.press(at(0, 0), NOW);
		assertThat(board.output(NOW, FixtureAttributes.ALL)).containsKey(5L);
		board.release(at(0, 0));

		assertThat(board.output(NOW, FixtureAttributes.ALL)).isEmpty();
	}

	@Test
	void aFieldStaysOnWhileItPlaysTheSameAfterAChange() {
		var a = playable(PartyCell.composition(0, 0, 1), 1, Map.of(DIMMER, 1.0));
		var b = playable(PartyCell.composition(0, 1, 2), 2, Map.of(DIMMER, 1.0));
		board.configure(List.of(a, b));
		board.press(at(0, 0), NOW);
		board.press(at(0, 1), NOW);

		board.configure(List.of(a, playable(PartyCell.composition(0, 1, 7), 7, Map.of(DIMMER, 1.0))));

		assertThat(board.onPlaces()).containsExactly(at(0, 0));
	}

	@Test
	void textFieldsAndEmptyPlacesCannotBePressed() {
		board.configure(List.of(new PlayableCell(PartyCell.text(0, 0, "Colors"), List.of())));

		assertThatThrownBy(() -> board.press(at(0, 0), NOW)).hasMessageContaining("plays nothing");
		assertThatThrownBy(() -> board.press(at(3, 3), NOW)).hasMessageContaining("plays nothing");
	}

	@Test
	void theMatrixKeepsItsFieldsInsideAndApart() {
		assertThatThrownBy(() -> new PartyPage(2, 2, Set.of(), List.of(PartyCell.text(2, 0, "x"))))
				.hasMessageContaining("outside");
		assertThatThrownBy(() -> new PartyPage(2, 2, Set.of(), List.of(PartyCell.text(0, 0, "x"),
				PartyCell.composition(0, 0, 1)))).hasMessageContaining("overlaps another field");
		assertThatThrownBy(() -> new PartyPage(2, 2, Set.of(2), List.of())).hasMessageContaining("line");
		assertThatThrownBy(() -> new PartyCell(0, 0, Optional.empty(), Optional.of(1L), Optional.of(2L), "#000000",
				"#ffffff", KeyMode.TOGGLE, Optional.empty())).hasMessageContaining("not several");
		assertThat(new PartyPage(3, 2, Set.of(1, 2), List.of()).separators()).containsExactlyInAnyOrder(1, 2);
	}

	@Test
	void aGradientSetsTheValuesOfThePointTouchedAndEndsTheOthersOfItsGroup() {
		var red = playable(PartyCell.composition(0, 0, 1).inGroup("Color"), 1, Map.of(RED, 1.0));
		var gradient = new PlayableCell(PartyCell.fader(1, 0, 3,
				new PartyFader(PartyFader.Kind.DIMMER, List.of(5L, 6L))).inGroup("Color"), List.of());
		board.configure(List.of(red, gradient));
		board.press(at(0, 0), NOW);

		board.slide(at(1, 0), PartyPoint.along(0.25), NOW);

		assertThat(board.onPlaces()).containsExactly(at(1, 0));
		var output = board.output(NOW, FixtureAttributes.ALL);
		assertThat(output.get(5L).values()).isEqualTo(Map.of(DIMMER, 0.25));
		assertThat(output.get(6L).values()).isEqualTo(Map.of(DIMMER, 0.25));
		board.slide(at(1, 0), PartyPoint.along(0.75), NOW);
		assertThat(board.output(NOW, FixtureAttributes.ALL).get(5L).get(DIMMER)).contains(0.75);

		board.press(at(1, 0), NOW);
		assertThat(board.isOn(at(1, 0))).isFalse();
		assertThat(board.faderPoints()).as("the point is kept").containsEntry(at(1, 0), PartyPoint.along(0.75));
		board.press(at(1, 0), NOW);
		assertThat(board.output(NOW, FixtureAttributes.ALL).get(5L).get(DIMMER)).contains(0.75);
	}

	@Test
	void aColorGradientRunsFromRedToVioletAndSetsOnlyWhatAFixtureHas() {
		board.configure(List.of(new PlayableCell(PartyCell.fader(0, 0, 5,
				new PartyFader(PartyFader.Kind.COLOR, List.of(5L))), List.of())));
		FixtureAttributes noGreen = (fixtureId, attribute) -> attribute.function() != ChannelFunction.GREEN;

		board.slide(at(0, 0), PartyPoint.along(0), NOW);
		assertThat(board.output(NOW, noGreen).get(5L).values()).isEqualTo(Map.of(RED, 1.0, BLUE, 0.0));
		board.slide(at(0, 0), PartyPoint.along(1), NOW);
		var violet = board.output(NOW, noGreen).get(5L);
		assertThat(violet.get(BLUE)).contains(1.0);
		assertThat(violet.get(RED).orElseThrow()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
	}

	@Test
	void aColorGradientSubtractsOnFixturesWithCyanMagentaAndYellow() {
		board.configure(List.of(new PlayableCell(PartyCell.fader(0, 0, 5,
				new PartyFader(PartyFader.Kind.COLOR, List.of(5L))), List.of())));
		FixtureAttributes cmy = (fixtureId, attribute) -> switch (attribute.function()) {
			case CYAN, MAGENTA, YELLOW -> true;
			default -> false;
		};

		board.slide(at(0, 0), PartyPoint.along(0), NOW);

		assertThat(board.output(NOW, cmy).get(5L).values()).isEqualTo(Map.of(
				Attribute.of(ChannelFunction.CYAN), 0.0, Attribute.of(ChannelFunction.MAGENTA), 1.0,
				Attribute.of(ChannelFunction.YELLOW), 1.0));
	}

	@Test
	void onlyAGradientCanBeSlidAndOnlyWithinItsEnds() {
		board.configure(List.of(playable(PartyCell.composition(0, 0, 1), 1, Map.of(DIMMER, 1.0)),
				new PlayableCell(PartyCell.fader(1, 0, 2, new PartyFader(PartyFader.Kind.DIMMER, List.of(5L))),
						List.of())));

		assertThatThrownBy(() -> board.slide(at(0, 0), PartyPoint.along(0.5), NOW))
				.hasMessageContaining("has no gradient");
		assertThatThrownBy(() -> board.slide(at(1, 0), PartyPoint.along(1.5), NOW))
				.hasMessageContaining("between 0 and 1");
	}

	@Test
	void aGradientGoesOffWhenItsFixturesChange() {
		var before = PartyCell.fader(0, 0, 2, new PartyFader(PartyFader.Kind.DIMMER, List.of(5L)));
		board.configure(List.of(new PlayableCell(before, List.of())));
		board.slide(at(0, 0), PartyPoint.along(0.5), NOW);

		board.configure(List.of(new PlayableCell(PartyCell.fader(0, 0, 2,
				new PartyFader(PartyFader.Kind.DIMMER, List.of(5L, 6L))), List.of())));

		assertThat(board.isOn(at(0, 0))).isFalse();
	}

	@Test
	void aPadSetsPanAndTiltOfItsFixturesAndStartsInTheMiddle() {
		var pad = PartyCell.fader(0, 0, 2, 2, new PartyFader(PartyFader.Kind.PAN_TILT, List.of(5L, 6L)));
		board.configure(List.of(new PlayableCell(pad, List.of())));
		FixtureAttributes onlyFive = (fixtureId, attribute) -> fixtureId == 5L;

		board.press(at(0, 0), NOW);
		assertThat(board.output(NOW, onlyFive).get(5L).values()).isEqualTo(Map.of(PAN, 0.5, TILT, 0.5));
		board.slide(at(0, 0), new PartyPoint(0.2, 0.9), NOW);

		var output = board.output(NOW, onlyFive);
		assertThat(output.get(5L).values()).isEqualTo(Map.of(PAN, 0.2, TILT, 0.9));
		assertThat(output).as("a fixture without pan and tilt is left alone").doesNotContainKey(6L);
	}

	@Test
	void fieldsAreAddressedByPageAndGroupsReachOverAllPages() {
		var red = new PlayableCell(0, PartyCell.composition(0, 0, 1).inGroup("Color"),
				playable(PartyCell.composition(0, 0, 1), 1, Map.of(RED, 1.0)).compositions());
		var blue = new PlayableCell(1, PartyCell.composition(0, 0, 2).inGroup("Color"),
				playable(PartyCell.composition(0, 0, 2), 2, Map.of(BLUE, 1.0)).compositions());
		board.configure(List.of(red, blue));

		board.press(new PartyPlace(0, 0, 0), NOW);
		board.press(new PartyPlace(1, 0, 0), NOW);

		assertThat(board.onPlaces()).containsExactly(new PartyPlace(1, 0, 0));
		assertThatThrownBy(() -> board.press(new PartyPlace(2, 0, 0), NOW))
				.hasMessageContaining("Field 1/1 on page 3 plays nothing");
	}

	private static PartyPlace at(int row, int column) {
		return new PartyPlace(0, row, column);
	}

	private static PlayableCell playable(PartyCell cell, long compositionId, Map<Attribute, Double> values) {
		var look = new StepSequence(List.of(new StepSequence.Step(1, 0, Map.of(5L, new FixtureValues(values)))),
				StepSequence.PlayMode.LOOP);
		return new PlayableCell(cell, List.of(new ActiveComposition(compositionId, Optional.of(compositionId), look,
				List.of(5L), 1, 1, false)));
	}
}
