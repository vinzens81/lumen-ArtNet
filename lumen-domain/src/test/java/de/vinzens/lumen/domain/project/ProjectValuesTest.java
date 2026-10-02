package de.vinzens.lumen.domain.project;

import de.vinzens.lumen.domain.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectValuesTest {

	@Test
	void artNetPortAddressRoundTrip() {
		var address = new ArtNetAddress(1, 2, 3);

		assertThat(address.portAddress()).isEqualTo(0x123);
		assertThat(ArtNetAddress.fromPortAddress(0x123)).isEqualTo(address);
	}

	@Test
	void artNetAddressRejectsOutOfRangeValues() {
		assertThatThrownBy(() -> new ArtNetAddress(128, 16, -1))
				.isInstanceOfSatisfying(DomainValidationException.class,
						e -> assertThat(e.errors()).hasSize(3));
	}

	@Test
	void parsesArtNetTargets() {
		assertThat(ArtNetTarget.parse("broadcast")).isInstanceOf(ArtNetTarget.Broadcast.class);
		assertThat(ArtNetTarget.parse(" 192.168.1.50 ").asText()).isEqualTo("192.168.1.50");
		assertThatThrownBy(() -> ArtNetTarget.parse("192.168.1.300")).isInstanceOf(DomainValidationException.class);
		assertThatThrownBy(() -> ArtNetTarget.parse("node.local")).isInstanceOf(DomainValidationException.class);
	}

	@Test
	void projectSpecCollectsAllErrors() {
		assertThatThrownBy(() -> new ProjectSpec(" ", Optional.empty(), Duration.ofSeconds(-1), Duration.ofHours(1)))
				.isInstanceOfSatisfying(DomainValidationException.class, e -> assertThat(e.errors())
						.containsExactly("name fehlt", "fadeIn muss zwischen 0 und 10 Minuten liegen",
								"fadeOut muss zwischen 0 und 10 Minuten liegen"));
	}

	@Test
	void projectSpecNormalizesText() {
		var spec = new ProjectSpec(" Der Sturm ", Optional.of("  "), Duration.ZERO, Duration.ZERO);

		assertThat(spec.name()).isEqualTo("Der Sturm");
		assertThat(spec.venue()).isEmpty();
	}

	@Test
	void fixtureSpecValidatesAddressAndPosition() {
		assertThatThrownBy(() -> new FixtureSpec("PAR", Optional.empty(), "par", "4ch", 1, 513,
				Optional.empty(), 0)).hasMessageContaining("address muss zwischen 1 und 512 liegen");
		assertThatThrownBy(() -> new Position(1.5, 0)).hasMessageContaining("position.x");
	}

	@Test
	void groupSpecRejectsDuplicateMembersAndInvalidColor() {
		assertThatThrownBy(() -> new GroupSpec("Front", Optional.of("orange"), 0, List.of(1L, 1L)))
				.isInstanceOfSatisfying(DomainValidationException.class, e -> assertThat(e.errors()).hasSize(2));
	}
}
