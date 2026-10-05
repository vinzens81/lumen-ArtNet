package de.vinzens.lumen.domain.project;

import de.vinzens.lumen.domain.DomainValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalNetworkTest {

	@Test
	void broadcastAddressOfTheNetwork() {
		assertThat(new LocalNetwork("en0", "192.168.0.23", 24).broadcastAddress()).isEqualTo("192.168.0.255");
		assertThat(new LocalNetwork("en7", "2.0.0.10", 8).broadcastAddress()).isEqualTo("2.255.255.255");
		assertThat(new LocalNetwork("en8", "10.1.130.7", 22).broadcastAddress()).isEqualTo("10.1.131.255");
		assertThat(new LocalNetwork("en9", "10.0.0.1", 32).broadcastAddress()).isEqualTo("10.0.0.1");
	}

	@Test
	void containsAddressesOfTheSameNetworkOnly() {
		var wifi = new LocalNetwork("en0", "192.168.0.23", 24);
		assertThat(wifi.contains("192.168.0.150")).isTrue();
		assertThat(wifi.contains("192.168.1.150")).isFalse();
		assertThat(wifi.contains("not an ip")).isFalse();

		var artNet = new LocalNetwork("en7", "2.0.0.10", 8);
		assertThat(artNet.contains("2.12.34.56")).isTrue();
		assertThat(artNet.contains("10.0.0.5")).isFalse();
	}

	@Test
	void validatesAddressAndPrefix() {
		assertThatThrownBy(() -> new LocalNetwork(" ", "300.0.0.1", 33))
				.isInstanceOfSatisfying(DomainValidationException.class, e -> assertThat(e.errors()).containsExactly(
						"interfaceName is missing", "'300.0.0.1' is not a valid IPv4 address",
						"prefixLength must be between 0 and 32"));
	}
}
