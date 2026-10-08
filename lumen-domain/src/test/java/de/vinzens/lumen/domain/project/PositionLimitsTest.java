package de.vinzens.lumen.domain.project;

import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.output.FixtureValues;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PositionLimitsTest {

	private static final Attribute PAN = Attribute.of(ChannelFunction.PAN);
	private static final Attribute TILT = Attribute.of(ChannelFunction.TILT);
	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);

	@Test
	void holdsPanAndTiltOfEveryHeadWithinTheRange() {
		var limits = new PositionLimits(51, 204, 0, 102);
		var values = new FixtureValues(Map.of(PAN, 0.1, TILT, 0.9, PAN.withHead(2), 1.0, DIMMER, 1.0));
		var limited = limits.apply(values);
		assertThat(limited.get(PAN)).contains(0.2);
		assertThat(limited.get(TILT)).contains(0.4);
		assertThat(limited.get(PAN.withHead(2))).contains(0.8);
		assertThat(limited.get(DIMMER)).contains(1.0);
		var inside = new FixtureValues(Map.of(PAN, 0.5));
		assertThat(limits.apply(inside)).isSameAs(inside);
	}

	@Test
	void onlyFixturesWithLimitsChange() {
		var values = Map.of(1L, new FixtureValues(Map.of(PAN, 1.0)), 2L, new FixtureValues(Map.of(PAN, 1.0)));
		var limited = PositionLimits.apply(values, Map.of(1L, new PositionLimits(0, 128, 0, 255)));
		assertThat(limited.get(1L).get(PAN).orElseThrow()).isEqualTo(128 / 255.0);
		assertThat(limited.get(2L).get(PAN)).contains(1.0);
	}

	@Test
	void minIsNotAboveMax() {
		assertThatThrownBy(() -> new PositionLimits(200, 100, 0, 255)).isInstanceOf(DomainValidationException.class);
		assertThatThrownBy(() -> new PositionLimits(0, 256, 0, 255)).isInstanceOf(DomainValidationException.class);
	}
}
