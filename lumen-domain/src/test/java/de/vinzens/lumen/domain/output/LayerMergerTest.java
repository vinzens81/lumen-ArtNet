package de.vinzens.lumen.domain.output;

import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelFunction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LayerMergerTest {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	private static final Attribute RED = Attribute.of(ChannelFunction.RED);

	@Test
	void intensityIsHighestTakesPrecedence() {
		var low = new LayerOutput(10, Map.of(1L, new FixtureValues(Map.of(DIMMER, 0.8))));
		var high = new LayerOutput(20, Map.of(1L, new FixtureValues(Map.of(DIMMER, 0.3))));

		assertThat(LayerMerger.merge(List.of(high, low)).get(1L).get(DIMMER)).contains(0.8);
	}

	@Test
	void otherAttributesFollowPriority() {
		var low = new LayerOutput(10, Map.of(1L, new FixtureValues(Map.of(RED, 0.8))));
		var high = new LayerOutput(20, Map.of(1L, new FixtureValues(Map.of(RED, 0.3))));

		assertThat(LayerMerger.merge(List.of(high, low)).get(1L).get(RED)).contains(0.3);
	}

	@Test
	void programmerKeepsOtherAttributesWhenUpdating() {
		var programmer = Programmer.EMPTY
				.with(1, new FixtureValues(Map.of(DIMMER, 1.0, RED, 1.0)))
				.with(1, new FixtureValues(Map.of(RED, 0.5)));

		assertThat(programmer.values(1).values()).containsExactlyInAnyOrderEntriesOf(Map.of(DIMMER, 1.0, RED, 0.5));
		assertThat(programmer.without(1).fixtures()).isEmpty();
	}

	@Test
	void valuesMustBeNormalized() {
		assertThatThrownBy(() -> new FixtureValues(Map.of(DIMMER, 1.5, RED, -0.1)))
				.isInstanceOfSatisfying(DomainValidationException.class, e -> assertThat(e.errors()).hasSize(2));
	}

	@Test
	void attributeIdsRoundTrip() {
		assertThat(Attribute.parse("color-wheel")).isEqualTo(Attribute.of(ChannelFunction.COLOR_WHEEL));
		assertThat(Attribute.parse("generic:Program speed").id()).isEqualTo("generic:Program speed");
		assertThatThrownBy(() -> Attribute.parse("generic")).isInstanceOf(DomainValidationException.class);
		assertThatThrownBy(() -> Attribute.parse("brightness")).isInstanceOf(DomainValidationException.class);
	}
}
