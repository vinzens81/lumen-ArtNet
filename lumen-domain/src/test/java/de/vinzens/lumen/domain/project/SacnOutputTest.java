package de.vinzens.lumen.domain.project;

import de.vinzens.lumen.domain.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SacnOutputTest {

	@Test
	void multicastGroupCarriesTheUniverseInTheLastTwoBytes() {
		assertThat(new SacnOutput(1, 100, List.of()).multicastAddress()).isEqualTo("239.255.0.1");
		assertThat(new SacnOutput(258, 100, List.of()).multicastAddress()).isEqualTo("239.255.1.2");
		assertThat(new SacnOutput(63_999, 100, List.of()).multicastAddress()).isEqualTo("239.255.249.255");
	}

	@Test
	void withoutAddressesItIsMulticast() {
		assertThat(new SacnOutput(1, 100, List.of(" ")).multicast()).isTrue();
		assertThat(new SacnOutput(1, 100, List.of("10.0.0.5", "10.0.0.5 ")).ipAddresses())
				.containsExactly("10.0.0.5");
	}

	@Test
	void defaultsFollowTheNumberInLumen() {
		assertThat(SacnOutput.forUniverse(3)).isEqualTo(new SacnOutput(3, 100, List.of()));
		assertThat(SacnOutput.forUniverse(70_000).universe()).isEqualTo(63_999);
	}

	@Test
	void rejectsUniverseZeroPriorityAbove200AndBadAddresses() {
		assertThatThrownBy(() -> new SacnOutput(0, 201, List.of("10.0.0")))
				.isInstanceOf(DomainValidationException.class)
				.hasMessageContaining("sACN universe must be between 1 and 63999, was 0")
				.hasMessageContaining("sACN priority must be between 0 and 200, was 201")
				.hasMessageContaining("'10.0.0' is not a valid IPv4 address");
	}

	@Test
	void parsesTheProtocol() {
		assertThat(OutputProtocol.parse(" sACN")).isEqualTo(OutputProtocol.SACN);
		assertThat(OutputProtocol.BOTH.sendsArtNet()).isTrue();
		assertThat(OutputProtocol.SACN.sendsArtNet()).isFalse();
		assertThatThrownBy(() -> OutputProtocol.parse("dmx")).isInstanceOf(DomainValidationException.class);
	}
}
