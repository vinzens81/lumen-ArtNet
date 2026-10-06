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
		assertThat(ArtNetTarget.parse("auto")).isEqualTo(ArtNetTarget.auto());
		assertThat(ArtNetTarget.parse("auto, 10.0.0.5,10.0.0.6,10.0.0.5")).isEqualTo(
				new ArtNetTarget.Unicast(true, List.of("10.0.0.5", "10.0.0.6")));
		assertThat(ArtNetTarget.parse("auto,10.0.0.5").asText()).isEqualTo("auto,10.0.0.5");
		assertThatThrownBy(() -> ArtNetTarget.parse("192.168.1.300")).isInstanceOf(DomainValidationException.class);
		assertThatThrownBy(() -> ArtNetTarget.parse("node.local")).isInstanceOf(DomainValidationException.class);
		assertThatThrownBy(() -> new ArtNetTarget.Unicast(false, List.of()))
				.hasMessageContaining("at least one IP address");
	}

	@Test
	void nodeIsOnlineForTenSecondsAfterItsLastReply() {
		var seen = java.time.Instant.parse("2026-10-02T12:00:00Z");
		var node = new ArtNetNode("10.0.0.5", 0, "Dimmer", "Dimmer node", "aa:bb", List.of(0), "", seen);

		assertThat(node.isOnline(seen.plusSeconds(9))).isTrue();
		assertThat(node.isOnline(seen.plusSeconds(10))).isFalse();
		assertThat(node.outputs(0)).isTrue();
	}

	@Test
	void projectSpecCollectsAllErrors() {
		assertThatThrownBy(() -> new ProjectSpec(" ", Optional.empty(), Duration.ofSeconds(-1), Duration.ofHours(1)))
				.isInstanceOfSatisfying(DomainValidationException.class, e -> assertThat(e.errors())
						.containsExactly("name is missing", "fadeIn must be between 0 and 10 minutes",
								"fadeOut must be between 0 and 10 minutes"));
	}

	@Test
	void projectSpecNormalizesText() {
		var spec = new ProjectSpec(" The Tempest ", Optional.of("  "), Duration.ZERO, Duration.ZERO);

		assertThat(spec.name()).isEqualTo("The Tempest");
		assertThat(spec.venue()).isEmpty();
	}

	@Test
	void fixtureSpecValidatesAddressAndPosition() {
		assertThatThrownBy(() -> new FixtureSpec("PAR", Optional.empty(), "par", "4ch", 1, 513,
				Optional.empty(), 0)).hasMessageContaining("address must be between 1 and 512");
		assertThatThrownBy(() -> new Position(1.5, 0)).hasMessageContaining("position.x");
	}

	@Test
	void stageShapeValidatesRotationAndLength() {
		assertThatThrownBy(() -> new StageShape(StageShape.Kind.BAR, 360, 0.2)).hasMessageContaining("rotation");
		assertThatThrownBy(() -> new StageShape(StageShape.Kind.BAR, 0, 0)).hasMessageContaining("length");
		assertThatThrownBy(() -> StageShape.Kind.parse("star")).hasMessageContaining("auto, spot, bar, strip");
		assertThat(StageShape.Kind.parse("strip")).isEqualTo(StageShape.Kind.STRIP);
	}

	@Test
	void groupSpecRejectsDuplicateMembersAndInvalidColor() {
		assertThatThrownBy(() -> new GroupSpec("Front", Optional.of("orange"), 0, List.of(1L, 1L)))
				.isInstanceOfSatisfying(DomainValidationException.class, e -> assertThat(e.errors()).hasSize(2));
	}
}
