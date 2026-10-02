package de.vinzens.lumen.domain.library;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelFunctionTest {

	@Test
	void resolvesYamlIds() {
		assertThat(ChannelFunction.fromId("color-wheel")).contains(ChannelFunction.COLOR_WHEEL);
		assertThat(ChannelFunction.fromId("COLOR_WHEEL")).isEmpty();
	}

	@Test
	void channelOverridesDefaultFadeBehavior() {
		var gobo = new ChannelDefinition(ChannelFunction.GOBO, java.util.Optional.empty(), 8, 0, false,
				java.util.List.of(), java.util.Optional.of(FadeBehavior.SMOOTH));

		assertThat(ChannelFunction.GOBO.defaultFade()).isEqualTo(FadeBehavior.SNAP);
		assertThat(gobo.effectiveFadeBehavior()).isEqualTo(FadeBehavior.SMOOTH);
		assertThat(ChannelFunction.DIMMER.defaultFade()).isEqualTo(FadeBehavior.INTENSITY);
	}
}
