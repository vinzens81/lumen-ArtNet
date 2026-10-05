package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelDefinition;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.library.Mode;
import de.vinzens.lumen.domain.output.DmxRenderer;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.output.RenderFixture;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** A bar with three RGB heads and no dimmer channel. */
class MultiPixelTest {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	private static final Attribute RED = Attribute.of(ChannelFunction.RED);
	private static final Mode BAR = bar(3);

	@Test
	void headsHaveOwnAttributesAndTheFixtureAttributeSetsThemAll() {
		assertThat(BAR.heads()).isEqualTo(3);
		assertThat(BAR.attributes()).contains(DIMMER, RED, RED.withHead(2), DIMMER.withHead(3));
		assertThat(Attribute.parse("red@2")).isEqualTo(RED.withHead(2));
		assertThat(Attribute.parse("generic:Mode@3").id()).isEqualTo("generic:Mode@3");
		assertThat(RED.withHead(2).withoutHead()).isEqualTo(RED);
	}

	@Test
	void headValuesOverrideTheFixtureValue() {
		var values = new FixtureValues(Map.of(DIMMER, 1.0, RED, 1.0, RED.withHead(2), 0.0,
				DIMMER.withHead(3), 0.5));

		var frame = DmxRenderer.render(List.of(1L), List.of(new RenderFixture(7, 1, 1, BAR)), Map.of(7L, values), 1)
				.get(1L);

		assertThat(frame.get(1)).as("head 1 red").isEqualTo(255);
		assertThat(frame.get(4)).as("head 2 red").isZero();
		assertThat(frame.get(7)).as("head 3 red at its own dimmer").isEqualTo(128);
	}

	@Test
	void aChaseRunsAlongTheHeads() {
		var chase = new ActiveComposition(1, Optional.empty(), new Chase(1, 1, Chase.Direction.FORWARD, 0, 0, 1),
				List.of(7L), 1, 1, false, false, true);
		var attributes = FixtureAttributes.of(Map.of(7L, BAR));

		var atStart = CompositionMixer.contribution(chase, 0.5, attributes).get(7L);
		var later = CompositionMixer.contribution(chase, 1.5, attributes).get(7L);

		assertThat(atStart.get(DIMMER.withHead(1))).contains(1.0);
		assertThat(atStart.get(DIMMER.withHead(2))).contains(0.0);
		assertThat(later.get(DIMMER.withHead(2))).contains(1.0);
		assertThat(atStart.get(DIMMER)).as("not the whole fixture").isEmpty();
	}

	@Test
	void headsOfABarWithADimmerChannelGetVirtualDimmers() {
		var channels = new ArrayList<>(bar(2).channels());
		channels.addFirst(new ChannelDefinition(ChannelFunction.DIMMER, Optional.empty(), 8, 0, false, List.of(),
				Optional.empty()));
		var mode = new Mode("7ch", channels);
		var values = new FixtureValues(Map.of(DIMMER, 1.0, RED, 1.0, DIMMER.withHead(2), 0.0));

		var frame = DmxRenderer.render(List.of(1L), List.of(new RenderFixture(7, 1, 1, mode)), Map.of(7L, values), 1)
				.get(1L);

		assertThat(mode.attributes()).contains(DIMMER.withHead(1));
		assertThat(frame.get(1)).as("dimmer channel").isEqualTo(255);
		assertThat(frame.get(2)).as("head 1 red, no level of its own").isEqualTo(255);
		assertThat(frame.get(5)).as("head 2 red at level 0").isZero();
	}

	private static Mode bar(int heads) {
		var channels = new ArrayList<ChannelDefinition>();
		for (int head = 1; head <= heads; head++) {
			for (var function : List.of(ChannelFunction.RED, ChannelFunction.GREEN, ChannelFunction.BLUE)) {
				channels.add(new ChannelDefinition(function, Optional.empty(), 8, 0, false, List.of(), Optional.empty(),
						head));
			}
		}
		return new Mode(heads * 3 + "ch", channels);
	}
}
