package de.vinzens.lumen.adapter.web.composition;

import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.composition.Chase;
import de.vinzens.lumen.domain.composition.Color;
import de.vinzens.lumen.domain.composition.ColorCycle;
import de.vinzens.lumen.domain.composition.Composition;
import de.vinzens.lumen.domain.composition.CompositionSpec;
import de.vinzens.lumen.domain.composition.CompositionStep;
import de.vinzens.lumen.domain.composition.Flicker;
import de.vinzens.lumen.domain.composition.GeneratorSettings;
import de.vinzens.lumen.domain.composition.IntensityMode;
import de.vinzens.lumen.domain.composition.Pattern;
import de.vinzens.lumen.domain.composition.PatternKind;
import de.vinzens.lumen.domain.composition.PlayMode;
import de.vinzens.lumen.domain.composition.Rainbow;
import de.vinzens.lumen.domain.composition.StepSequence;
import de.vinzens.lumen.domain.composition.Target;
import de.vinzens.lumen.domain.composition.Wave;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * JSON form of compositions: steps playing looks, and the effects steps are generated from. Effect parameters are
 * typed per kind; times are milliseconds, colors {@code #rrggbb}, enum values lower case.
 */
public final class CompositionDtos {

	private CompositionDtos() {
	}

	/**
	 * Properties and steps of a composition. Missing times and modes take the defaults (1 s fade, 2 s hold, loop,
	 * absolute intensity).
	 *
	 * @param playMode      loop, ping_pong or once
	 * @param intensityMode absolute or relative
	 */
	public record CompositionRequest(String name, Long fadeMs, Long holdMs, String playMode, String intensityMode,
			List<StepDto> steps) {

		/** @param generator kept from the stored composition: it only changes by generating again */
		public CompositionSpec toSpec(Optional<GeneratorSettings> generator) {
			return new CompositionSpec(name, seconds(fadeMs, 1000), seconds(holdMs, 2000),
					playMode == null ? PlayMode.LOOP : PlayMode.fromId(playMode),
					enumValue(IntensityMode.class, intensityMode, "intensityMode", IntensityMode.ABSOLUTE), generator);
		}

		public List<CompositionStep> toSteps() {
			return (steps == null ? List.<StepDto>of() : steps).stream().map(StepDto::toStep).toList();
		}
	}

	/**
	 * @param fadeMs fade into the step, {@code null} = the composition's default
	 * @param holdMs   hold of the step, {@code null} = the composition's default
	 * @param parallel starts together with the step before it (same row, the row's times), {@code null} = false
	 */
	public record StepDto(Long lookId, Long fadeMs, Long holdMs, Boolean parallel) {

		CompositionStep toStep() {
			if (lookId == null) {
				throw new DomainValidationException(List.of("lookId of a step is missing"));
			}
			return new CompositionStep(lookId, Optional.ofNullable(fadeMs).map(ms -> ms / 1000.0),
					Optional.ofNullable(holdMs).map(ms -> ms / 1000.0), Boolean.TRUE.equals(parallel));
		}

		static StepDto from(CompositionStep step) {
			return new StepDto(step.lookId(), step.fadeSeconds().map(CompositionDtos::millis).orElse(null),
					step.holdSeconds().map(CompositionDtos::millis).orElse(null), step.parallel());
		}
	}

	/**
	 * An effect the steps are generated from.
	 *
	 * @param kind    chase, color_cycle, rainbow, wave or flicker
	 * @param params  parameters of the kind, see the {@code *Params} records
	 * @param target  {@code {"groupId": 3}} or {@code {"fixtureIds": [1, 2]}}
	 * @param perHead every head of a multi-pixel fixture is an element of its own
	 */
	public record GeneratorDto(String kind, JsonNode params, TargetDto target, Boolean perHead) {

		public GeneratorSettings toSettings(JsonMapper json) {
			if (target == null) {
				throw new DomainValidationException(List.of("target needs either groupId or fixtureIds"));
			}
			return new GeneratorSettings(effect(kind, params, json), target.toTarget(), Boolean.TRUE.equals(perHead));
		}

		/** The settings as JSON again, with every parameter filled in. */
		static GeneratorResponse from(GeneratorSettings settings) {
			Object params = switch (settings.effect()) {
				case Chase chase -> ChaseParams.from(chase);
				case ColorCycle cycle -> ColorCycleParams.from(cycle);
				case Rainbow rainbow -> RainbowParams.from(rainbow);
				case Wave wave -> WaveParams.from(wave);
				case Flicker flicker -> FlickerParams.from(flicker);
				case StepSequence steps -> throw new IllegalStateException("a step sequence is not a generator");
			};
			var target = switch (settings.target()) {
				case Target.Group group -> new TargetDto(group.groupId(), null);
				case Target.Fixtures fixtures -> new TargetDto(null, fixtures.fixtureIds());
			};
			return new GeneratorResponse(settings.effect().kind().id(), params, target, settings.perHead());
		}
	}

	public record GeneratorResponse(String kind, Object params, TargetDto target, boolean perHead) {
	}

	public record TargetDto(Long groupId, List<Long> fixtureIds) {

		public Target toTarget() {
			if ((groupId == null) == (fixtureIds == null)) {
				throw new DomainValidationException(List.of("target needs either groupId or fixtureIds"));
			}
			return groupId != null ? new Target.Group(groupId) : new Target.Fixtures(fixtureIds);
		}
	}

	/** An effect from its kind (chase, color_cycle, rainbow, wave, flicker) and parameters. */
	public static Pattern effect(String kind, JsonNode params, JsonMapper json) {
		return switch (PatternKind.fromId(kind)) {
			case CHASE -> read(json, kind, params, ChaseParams.class).toPattern();
			case COLOR_CYCLE -> read(json, kind, params, ColorCycleParams.class).toPattern();
			case RAINBOW -> read(json, kind, params, RainbowParams.class).toPattern();
			case WAVE -> read(json, kind, params, WaveParams.class).toPattern();
			case FLICKER -> read(json, kind, params, FlickerParams.class).toPattern();
			case STEPS -> throw new DomainValidationException(List.of("steps are not an effect"));
		};
	}

	private static <T> T read(JsonMapper json, String kind, JsonNode params, Class<T> type) {
		try {
			var node = params == null || params.isNull() ? json.createObjectNode() : params;
			return json.readerFor(type).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(node);
		} catch (JacksonException e) {
			throw new DomainValidationException(List.of("Invalid params for " + kind + ": " + e.getOriginalMessage()));
		}
	}

	// --- parameters per kind (all optional, with defaults) ------------------------------------------------------

	/** @param direction forward, backward, ping_pong or random */
	public record ChaseParams(Long stepMs, Integer width, String direction, Double softness, Double min, Double max) {

		Chase toPattern() {
			return new Chase(seconds(stepMs, 500), width == null ? 1 : width,
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
					or(strength, 0.5), or(speed, 1), seed == null ? 1L : seed);
		}

		static FlickerParams from(Flicker flicker) {
			return new FlickerParams(lower(flicker.character()), flicker.strength(), flicker.speed(), flicker.seed());
		}
	}

	// --- response ----------------------------------------------------------------------------------------------

	/**
	 * @param generator what the steps were generated from, {@code null} if they were recorded or picked
	 * @param steps     in order, each playing a look
	 */
	public record CompositionResponse(long id, String name, long fadeMs, long holdMs, String playMode,
			String intensityMode, GeneratorResponse generator, List<StepDto> steps) {

		public static CompositionResponse from(Composition composition) {
			var spec = composition.spec();
			return new CompositionResponse(composition.id(), spec.name(), millis(spec.fadeSeconds()),
					millis(spec.holdSeconds()), spec.playMode().id(), lower(spec.intensityMode()),
					spec.generator().map(GeneratorDto::from).orElse(null),
					composition.steps().stream().map(StepDto::from).toList());
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
