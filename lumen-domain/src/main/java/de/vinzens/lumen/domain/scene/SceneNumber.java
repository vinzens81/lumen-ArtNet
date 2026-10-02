package de.vinzens.lumen.domain.scene;

import de.vinzens.lumen.domain.DomainValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Cue number of a scene, e.g. {@code 1}, {@code 2.5} or {@code 10.25}. Scenes are ordered by their number;
 * decimals allow inserting a scene without renumbering the others. Numbers are normalized, so {@code 2.50}
 * and {@code 2.5} are the same.
 */
public record SceneNumber(BigDecimal value) implements Comparable<SceneNumber> {

	private static final Pattern FORMAT = Pattern.compile("\\d{1,4}(\\.\\d{1,3})?");

	public SceneNumber {
		Objects.requireNonNull(value, "value");
		if (value.signum() <= 0) {
			throw new DomainValidationException(List.of("number must be greater than 0"));
		}
		value = value.stripTrailingZeros();
		if (value.scale() < 0) {
			value = value.setScale(0);
		}
	}

	public static SceneNumber parse(String text) {
		if (text == null || !FORMAT.matcher(text.strip()).matches()) {
			throw new DomainValidationException(List.of(
					"number '" + text + "' must look like 1, 2.5 or 10.25 (up to 4 digits, 3 decimals)"));
		}
		return new SceneNumber(new BigDecimal(text.strip()));
	}

	public static SceneNumber of(int number) {
		return new SceneNumber(BigDecimal.valueOf(number));
	}

	/** The next whole number after this one: 2 → 3, 2.5 → 3. */
	public SceneNumber nextWhole() {
		return new SceneNumber(value.setScale(0, RoundingMode.FLOOR).add(BigDecimal.ONE));
	}

	@Override
	public int compareTo(SceneNumber other) {
		return value.compareTo(other.value);
	}

	@Override
	public String toString() {
		return value.toPlainString();
	}
}
