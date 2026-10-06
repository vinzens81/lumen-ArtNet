package de.vinzens.lumen.domain.scene;

import de.vinzens.lumen.domain.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SceneValuesTest {

	@Test
	void numbersAreNormalizedAndOrderedNumerically() {
		assertThat(SceneNumber.parse("2.50")).isEqualTo(SceneNumber.parse("2.5"));
		assertThat(SceneNumber.parse("3.0").toString()).isEqualTo("3");
		assertThat(SceneNumber.parse("10").toString()).isEqualTo("10");

		var sorted = Stream.of("10", "2.5", "2", "1", "2.25").map(SceneNumber::parse).sorted().map(String::valueOf);
		assertThat(sorted).containsExactly("1", "2", "2.25", "2.5", "10");
	}

	@Test
	void nextWholeNumber() {
		assertThat(SceneNumber.parse("2.5").nextWhole()).isEqualTo(SceneNumber.of(3));
		assertThat(SceneNumber.of(3).nextWhole()).isEqualTo(SceneNumber.of(4));
	}

	@Test
	void rejectsInvalidNumbers() {
		for (var invalid : List.of("0", "-1", "abc", "1.2345", "12345", "1.", "")) {
			assertThatThrownBy(() -> SceneNumber.parse(invalid)).as(invalid)
					.isInstanceOf(DomainValidationException.class);
		}
	}

	@Test
	void specValidatesNameAndAutoFollow() {
		assertThatThrownBy(() -> new SceneSpec(SceneNumber.of(1), " ", Optional.empty(), Optional.of(Duration.ZERO)))
				.isInstanceOfSatisfying(DomainValidationException.class, e -> assertThat(e.errors())
						.containsExactly("name is missing", "autoFollow must be between 0 and 60 minutes"));

		var spec = new SceneSpec(SceneNumber.of(1), " Dawn ", Optional.of("  "), Optional.of(Duration.ofSeconds(8)));
		assertThat(spec.name()).isEqualTo("Dawn");
		assertThat(spec.cueNote()).isEmpty();
	}

	@Test
	void specNormalizesTheScript() {
		var spec = new SceneSpec(SceneNumber.of(1), "Dawn", Optional.empty(), Optional.empty(),
				SceneTiming.DEFAULT, "\r\n  To be.\r\nOr not.\r\n\r\n");
		assertThat(spec.script()).isEqualTo("To be.\nOr not.");
		assertThat(new SceneSpec(SceneNumber.of(1), "Dawn", Optional.empty(), Optional.empty()).script()).isEmpty();
	}

	@Test
	void specLimitsTheScript() {
		var tooLong = "a".repeat(SceneSpec.MAX_SCRIPT_LENGTH + 1);
		assertThatThrownBy(() -> new SceneSpec(SceneNumber.of(1), "Dawn", Optional.empty(), Optional.empty(),
				SceneTiming.DEFAULT, tooLong))
				.isInstanceOfSatisfying(DomainValidationException.class, e -> assertThat(e.errors())
						.containsExactly("script must have at most 100000 characters"));
	}
}
