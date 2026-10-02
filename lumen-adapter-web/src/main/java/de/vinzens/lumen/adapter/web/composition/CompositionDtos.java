package de.vinzens.lumen.adapter.web.composition;

import de.vinzens.lumen.adapter.web.output.ValuesJson;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.composition.Chase;
import de.vinzens.lumen.domain.composition.Color;
import de.vinzens.lumen.domain.composition.ColorCycle;
import de.vinzens.lumen.domain.composition.Composition;
import de.vinzens.lumen.domain.composition.CompositionSpec;
import de.vinzens.lumen.domain.composition.Flicker;
import de.vinzens.lumen.domain.composition.Pattern;
import de.vinzens.lumen.domain.composition.PatternKind;
import de.vinzens.lumen.domain.composition.Rainbow;
import de.vinzens.lumen.domain.composition.StepSequence;
import de.vinzens.lumen.domain.composition.StepSequence.Step;
import de.vinzens.lumen.domain.composition.Target;
import de.vinzens.lumen.domain.composition.Wave;
import de.vinzens.lumen.domain.output.FixtureValues;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * JSON form of compositions. Parameters are typed per kind; times are milliseconds, colors {@code #rrggbb},
 * enum values lower case.
 */
public final class CompositionDtos {

	private CompositionDtos() {
	}

	/**
	 * @param params  parameters of the kind, see the {@code *Params} records
	 * @param target  {@code {"groupId": 3}} or {@code {"fixtureIds": [1, 2]}}; none for {@code steps}
	 * @param steps   only for {@code steps}
	 */
	public record CompositionRequest(String name, String kind, JsonNode params, TargetDto target, List<StepDto> steps,
			Double level, Double speedFactor, Boolean restart, Boolean once) {

		public CompositionSpec toSpec(JsonMapper json) {
			var patternKind = PatternKind.fromId(kind);
			var pattern = pattern(patternKind, json);
			Target resolvedTarget = patternKind == PatternKind.STEPS ? new Target.Steps() : toTarget();
			return new CompositionSpec(name, pattern, resolvedTarget, Objects.requireNonNullElse(level, 1.0),
					Objects.requireNonNullElse(speedFactor, 1.0), Boolean.TRUE.equals(restart), Boolean.TRUE.equals(once));
		}

		private Target toTarget() {
			if (target == null || (target.groupId() == null) == (target.fixtureIds() == null)) {
				throw new DomainValidationException(List.of("target needs either groupId or fixtureIds"));
			}
			return target.groupId() != null ? new Target.Group(target.groupId())
					: new Target.Fixtures(target.fixtureIds());
		}

		private Pattern pattern(PatternKind patternKind, JsonMapper json) {
			return switch (patternKind) {
				case CHASE -> read(json, ChaseParams.class).toPattern();
				case COLOR_CYCLE -> read(json, ColorCycleParams.class).toPattern();
				case RAINBOW -> read(json, RainbowParams.class).toPattern();
				case WAVE -> read(json, WaveParams.class).toPattern();
				case FLICKER -> read(json, FlickerParams.class).toPattern();
				case STEPS -> new StepSequence(
						(steps == null ? List.<StepDto>of() : steps).stream().map(StepDto::toStep).toList(),
						enumValue(StepSequence.PlayMode.class, read(json, StepsParams.class).playMode(), "playMode",
								StepSequence.PlayMode.LOOP));
			};
		}

		private <T> T read(JsonMapper json, Class<T> type) {
			try {
				var node = params == null || params.isNull() ? json.createObjectNode() : params;
				return json.readerFor(type).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(node);
			} catch (JacksonException e) {
				throw new DomainValidationException(List.of("Invalid params for " + kind + ": " + e.getOriginalMessage()));
			}
		}
	}

	public record TargetDto(Long groupId, List<Long> fixtureIds) {
	}

	/** @param values normalized values (0–1) per fixture id and attribute */
	public record StepDto(Long holdMs, Long fadeMs, Map<Long, Map<String, Double>> values) {

		Step toStep() {
			var perFixture = new HashMap<Long, FixtureValues>();
			(values == null ? Map.<Long, Map<String, Double>>of() : values)
					.forEach((fixtureId, attributes) -> perFixture.put(fixtureId, ValuesJson.fromJson(attributes)));
			return new Step(seconds(holdMs, 1000), seconds(fadeMs, 0), perFixture);
		}

		static StepDto from(Step step) {
			return new StepDto(millis(step.holdSeconds()), millis(step.fadeSeconds()), ValuesJson.toJson(step.values()));
		}
	}

	// --- parameters per kind (all optional, with defaults) ------------------------------------------------------

	/** @param direction forward, backward, ping_pong or random */
	public record ChaseParams(Long stepMs, Integer width, String direction, Double softness, Double min, Double max) {

		Chase toPattern() {
			return new Chase(seconds(stepMs, 500), Objects.requireNonNullElse(width, 1),
					enumValue(Chase.Direction.class, direction, "direction", Chase.Direction.FORWARD),
					or(softness, 0), or(min, 0), or(max, 1));
		}

		static ChaseParams from(Chase chase) {
			return new ChaseParams(millis(chase.stepSeconds()), chase.width(), lower(chase.direction()),
					chase.softness(), chase.min(), chase.max());
		}
	}

	/** @param colors at least two, as {@code #rrggbb} */
	public record ColorCycleParams(List<String> colors, Long holdMs, Long fadeMs, Double spread, Double intensity) {

		ColorCycle toPattern() {
			var parsed = (colors == null ? List.of("#ff0000", "#0000ff") : colors).stream()
					.map(CompositionDtos::parseColor).toList();
			return new ColorCycle(parsed, seconds(holdMs, 2000), seconds(fadeMs, 1000), or(spread, 0),
					or(intensity, 1));
		}

		static ColorCycleParams from(ColorCycle cycle) {
			return new ColorCycleParams(cycle.colors().stream().map(CompositionDtos::formatColor).toList(),
					millis(cycle.holdSeconds()), millis(cycle.fadeSeconds()), cycle.spread(), cycle.intensity());
		}
	}

	public record RainbowParams(Long cycleMs, Double saturation, Double spread, Double intensity) {

		Rainbow toPattern() {
			return new Rainbow(seconds(cycleMs, 10_000), or(saturation, 1), or(spread, 0), or(intensity, 1));
		}

		static RainbowParams from(Rainbow rainbow) {
			return new RainbowParams(millis(rainbow.cycleSeconds()), rainbow.saturation(), rainbow.spread(),
					rainbow.intensity());
		}
	}

	/** @param shape sine, triangle, sawtooth or square */
	public record WaveParams(String shape, Long periodMs, Double min, Double max, Double spread) {

		Wave toPattern() {
			return new Wave(enumValue(Wave.Shape.class, shape, "shape", Wave.Shape.SINE), seconds(periodMs, 2000),
					or(min, 0), or(max, 1), or(spread, 0));
		}

		static WaveParams from(Wave wave) {
			return new WaveParams(lower(wave.shape()), millis(wave.periodSeconds()), wave.min(), wave.max(),
					wave.spread());
		}
	}

	/** @param character candle, fire or broken_tube */
	public record FlickerParams(String character, Double strength, Double speed, Long seed) {

		Flicker toPattern() {
			return new Flicker(enumValue(Flicker.Character.class, character, "character", Flicker.Character.CANDLE),
					or(strength, 0.5), or(speed, 1), Objects.requireNonNullElse(seed, 1L));
		}

		static FlickerParams from(Flicker flicker) {
			return new FlickerParams(lower(flicker.character()), flicker.strength(), flicker.speed(), flicker.seed());
		}
	}

	/** @param playMode loop or ping_pong */
	public record StepsParams(String playMode) {
	}

	// --- response ----------------------------------------------------------------------------------------------

	/**
	 * @param sceneId  the scene of a copy, null in the library
	 * @param sourceId library origin of a copy
	 * @param position order within the scene
	 * @param once     runs through once and stays at the end
	 */
	public record CompositionResponse(long id, Long sceneId, Long sourceId, int position, String name, String kind,
			Object params, TargetDto target, List<StepDto> steps, double level, double speedFactor, boolean restart,
			boolean once) {

		public static CompositionResponse from(Composition composition) {
			var spec = composition.spec();
			var pattern = spec.pattern();
			Object params = switch (pattern) {
				case Chase chase -> ChaseParams.from(chase);
				case ColorCycle cycle -> ColorCycleParams.from(cycle);
				case Rainbow rainbow -> RainbowParams.from(rainbow);
				case Wave wave -> WaveParams.from(wave);
				case Flicker flicker -> FlickerParams.from(flicker);
				case StepSequence steps -> new StepsParams(lower(steps.playMode()));
			};
			var target = switch (spec.target()) {
				case Target.Group group -> new TargetDto(group.groupId(), null);
				case Target.Fixtures fixtures -> new TargetDto(null, fixtures.fixtureIds());
				case Target.Steps steps -> null;
			};
			var steps = pattern instanceof StepSequence sequence
					? sequence.steps().stream().map(StepDto::from).toList()
					: null;
			return new CompositionResponse(composition.id(), composition.sceneId().orElse(null),
					composition.sourceId().orElse(null), composition.position(), spec.name(), pattern.kind().id(),
					params, target, steps, spec.level(), spec.speedFactor(), spec.restart(), spec.once());
		}
	}

	// --- helpers -----------------------------------------------------------------------------------------------

	private static double or(Double value, double fallback) {
		return value == null ? fallback : value;
	}

	private static double seconds(Long millis, long fallbackMillis) {
		return (millis == null ? fallbackMillis : millis) / 1000.0;
	}

	private static long millis(double seconds) {
		return Math.round(seconds * 1000);
	}

	private static String lower(Enum<?> value) {
		return value.name().toLowerCase(Locale.ROOT);
	}

	private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String field, E fallback) {
		if (value == null) {
			return fallback;
		}
		try {
			return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			var allowed = new HashSet<String>();
			for (var constant : type.getEnumConstants()) {
				allowed.add(lower(constant));
			}
			throw new DomainValidationException(List.of(field + " must be one of " + allowed.stream().sorted().toList()
					+ ", was '" + value + "'"));
		}
	}

	private static Color parseColor(String hex) {
		if (hex == null || !hex.matches("#[0-9a-fA-F]{6}")) {
			throw new DomainValidationException(List.of("colors must look like #ff8800, was '" + hex + "'"));
		}
		var n = Integer.parseInt(hex.substring(1), 16);
		return new Color(((n >> 16) & 255) / 255.0, ((n >> 8) & 255) / 255.0, (n & 255) / 255.0);
	}

	private static String formatColor(Color color) {
		return String.format(Locale.ROOT, "#%02x%02x%02x", Math.round(color.red() * 255),
				Math.round(color.green() * 255), Math.round(color.blue() * 255));
	}
}
