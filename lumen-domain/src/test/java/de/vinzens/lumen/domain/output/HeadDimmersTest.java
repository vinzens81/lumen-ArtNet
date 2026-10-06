package de.vinzens.lumen.domain.output;

import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelDefinition;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.library.Mode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class HeadDimmersTest {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	private static final Mode DIMMER_PACK = new Mode("4ch", IntStream.rangeClosed(1, 4)
			.mapToObj(head -> new ChannelDefinition(ChannelFunction.DIMMER, Optional.empty(), 8, 0, false, List.of(),
					Optional.empty(), head))
			.toList());

	@Test
	void theWholeDimmerOfADimmerPackGoesToEveryChannelWithoutItsOwnValue() {
		var values = new FixtureValues(Map.of(DIMMER, 1.0, DIMMER.withHead(2), 0.3));

		var spread = HeadDimmers.spread(DIMMER_PACK, values);

		assertThat(DIMMER_PACK.dimmersOnlyPerHead()).isTrue();
		assertThat(spread.values()).containsOnly(Map.entry(DIMMER.withHead(1), 1.0), Map.entry(DIMMER.withHead(2), 0.3),
				Map.entry(DIMMER.withHead(3), 1.0), Map.entry(DIMMER.withHead(4), 1.0));
	}

	@Test
	void aFixtureWithAMasterDimmerKeepsIt() {
		var withMaster = new Mode("5ch", List.of(
				new ChannelDefinition(ChannelFunction.DIMMER, Optional.empty(), 8, 0, false, List.of(), Optional.empty()),
				new ChannelDefinition(ChannelFunction.DIMMER, Optional.empty(), 8, 0, false, List.of(), Optional.empty(),
						1)));
		var values = new FixtureValues(Map.of(DIMMER, 1.0));

		assertThat(withMaster.dimmersOnlyPerHead()).isFalse();
		assertThat(HeadDimmers.spread(withMaster, values)).isEqualTo(values);
	}
}
