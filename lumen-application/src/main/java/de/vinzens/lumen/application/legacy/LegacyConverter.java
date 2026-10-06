package de.vinzens.lumen.application.legacy;

import de.vinzens.lumen.application.legacy.LegacyShow.LegacyComposition;
import de.vinzens.lumen.application.legacy.LegacyShow.LegacyKey;
import de.vinzens.lumen.application.legacy.LegacyShow.LegacyScene;
import de.vinzens.lumen.application.project.ShowContent;
import de.vinzens.lumen.domain.composition.ColorCycle;
import de.vinzens.lumen.domain.composition.Composition;
import de.vinzens.lumen.domain.composition.CompositionSpec;
import de.vinzens.lumen.domain.composition.CompositionStep;
import de.vinzens.lumen.domain.composition.FixtureAttributes;
import de.vinzens.lumen.domain.composition.Generator;
import de.vinzens.lumen.domain.composition.GeneratorSettings;
import de.vinzens.lumen.domain.composition.IntensityMode;
import de.vinzens.lumen.domain.composition.Pattern;
import de.vinzens.lumen.domain.composition.PlayMode;
import de.vinzens.lumen.domain.composition.Rainbow;
import de.vinzens.lumen.domain.composition.StepSequence;
import de.vinzens.lumen.domain.composition.Target;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.look.Look;
import de.vinzens.lumen.domain.look.LookSpec;
import de.vinzens.lumen.domain.look.NamedLook;
import de.vinzens.lumen.domain.scene.SceneComposition;
import de.vinzens.lumen.domain.scene.SceneKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Turns a show of the old model ({@link LegacyShow}) into looks, compositions and scenes:
 * <ol>
 * <li>the fixed values of a scene become a composition with one step and a look of its own, first in the scene</li>
 * <li>copies of a library composition with the library's content are that composition; copies of the same origin
 * that were changed alike become one composition – so they still continue seamlessly from scene to scene</li>
 * <li>step sequences keep their steps, each with a look of its own</li>
 * <li>effects (chase, rainbow, …) are turned into steps by the {@link Generator}; their settings are kept</li>
 * <li>extra keys become keys of the scenes they were usable in; a key that played a scene plays its compositions
 * (more than one go on free keys)</li>
 * </ol>
 * What could not be taken over exactly is listed in the report. Ids in the result are new and only connect its parts.
 */
public final class LegacyConverter {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);

	/** @param report what changed or got lost, for the operator */
	public record Result(ShowContent content, List<String> report) {

		public Result {
			report = List.copyOf(report);
		}
	}

	private LegacyConverter() {
	}

	/**
	 * @param groupMembers fixtures per group id, in order (effects on groups run on the members at this moment)
	 * @param attributes   what the patched fixtures can do, for effects running along the heads of pixel bars
	 */
	public static Result convert(LegacyShow show, Map<Long, List<Long>> groupMembers, FixtureAttributes attributes) {
		return new Conversion(show, groupMembers, attributes).run();
	}

	/** What becomes a composition: the fixed values of a scene, or an old composition. */
	private sealed interface Draft {

		String name();
	}

	private record Base(String name, Look look) implements Draft {
	}

	private record Copy(String name, LegacyComposition composition) implements Draft {
	}

	/** What makes two copies of the same origin the same composition. */
	private record Content(Optional<Long> sourceId, Pattern pattern, Optional<Target> target, boolean once,
			boolean perHead) {

		static Content of(Optional<Long> sourceId, LegacyComposition composition) {
			return new Content(sourceId, composition.pattern(), composition.target(), composition.once(),
					composition.perHead());
		}
	}

	private static final class Conversion {

		private final LegacyShow show;
		private final Map<Long, List<Long>> groupMembers;
		private final FixtureAttributes attributes;

		private final Map<Long, Draft> drafts = new LinkedHashMap<>();
		private final Map<Content, Long> byContent = new HashMap<>();
		private final Set<String> names = new HashSet<>();
		private final Map<Long, List<SceneComposition>> sceneCompositions = new HashMap<>();
		private final Map<Long, List<SceneKey>> sceneKeys = new HashMap<>();
		private final List<NamedLook> looks = new ArrayList<>();
		private final List<String> report = new ArrayList<>();
		private final Map<Long, LegacyScene> scenes = new LinkedHashMap<>();
		private long nextComposition = 1;
		private long nextLook = 1;

		Conversion(LegacyShow show, Map<Long, List<Long>> groupMembers, FixtureAttributes attributes) {
			this.show = show;
			this.groupMembers = groupMembers;
			this.attributes = attributes;
			show.scenes().stream()
					.sorted(Comparator.comparing(scene -> scene.spec().number()))
					.forEach(scene -> scenes.put(scene.id(), scene));
		}

		Result run() {
			for (var scene : scenes.values()) {
				var list = sceneCompositions.computeIfAbsent(scene.id(), id -> new ArrayList<>());
				sceneKeys.put(scene.id(), new ArrayList<>());
				if (!scene.look().values().isEmpty()) {
					var id = add(new Base(uniqueName(label(scene) + " " + scene.spec().name(), label(scene)),
							scene.look()));
					list.add(SceneComposition.of(id));
				}
			}
			for (var library : show.compositions().stream().filter(c -> c.sceneId().isEmpty()).toList()) {
				var id = add(new Copy(uniqueName(library.name(), "library"), library));
				byContent.put(Content.of(Optional.of(library.id()), library), id);
			}
			var copies = show.compositions().stream()
					.filter(c -> c.sceneId().isPresent() && scenes.containsKey(c.sceneId().get()))
					.sorted(Comparator.comparing((LegacyComposition c) -> scenes.get(c.sceneId().get()).spec().number())
							.thenComparingInt(LegacyComposition::position))
					.toList();
			for (var copy : copies) {
				var scene = scenes.get(copy.sceneId().get());
				var id = compositionOf(copy, label(scene));
				var list = sceneCompositions.get(scene.id());
				if (list.stream().anyMatch(use -> use.compositionId() == id)) {
					report.add("Scene " + label(scene) + ": \"" + copy.name() + "\" was in the scene twice – kept once");
					continue;
				}
				list.add(new SceneComposition(id, copy.level(), copy.speedFactor(), copy.restart()));
			}
			convertKeys();
			var compositions = new ArrayList<Composition>();
			drafts.forEach((id, draft) -> compositions.add(build(id, draft)));
			return new Result(new ShowContent(looks, compositions, freeze(sceneCompositions), freeze(sceneKeys)),
					report);
		}

		private long compositionOf(LegacyComposition copy, String where) {
			if (copy.sourceId().isPresent()) {
				var existing = byContent.get(Content.of(copy.sourceId(), copy));
				if (existing != null) {
					return existing;
				}
			}
			var id = add(new Copy(uniqueName(copy.name(), where), copy));
			copy.sourceId().ifPresent(source -> byContent.put(Content.of(copy.sourceId(), copy), id));
			return id;
		}

		private void convertKeys() {
			var extras = new ArrayList<SceneKeyDraft>();
			for (var key : show.keys()) {
				List<Long> played;
				if (key.composition().isPresent()) {
					var composition = key.composition().get();
					played = List.of(compositionOf(composition, "key " + key.slot()));
					if (composition.level() != 1 || composition.speedFactor() != 1) {
						report.add("Key " + key.slot() + ": level and speed of \"" + composition.name()
								+ "\" are no longer set per key – it plays at full level and normal speed");
					}
				} else {
					played = key.sceneId().map(sceneCompositions::get).orElse(List.of()).stream()
							.map(SceneComposition::compositionId).toList();
					var name = key.sceneId().map(scenes::get).map(scene -> "scene " + label(scene)).orElse("a scene");
					if (played.isEmpty()) {
						report.add("Key " + key.slot() + " played " + name + ", which is empty – left out");
						continue;
					}
					if (played.size() > 1) {
						report.add("Key " + key.slot() + " played " + name + " with " + played.size()
								+ " parts – they go on free keys of the same scenes");
					}
				}
				for (var scene : scenes.values()) {
					var enabled = show.sceneKeys().getOrDefault(scene.id(), Set.of()).contains(key.slot());
					if (!enabled || scene.spec().outsideCueList()) {
						continue;
					}
					sceneKeys.get(scene.id()).add(new SceneKey(key.slot(), played.getFirst(), key.mode(), key.fade()));
					played.stream().skip(1).forEach(id -> extras.add(new SceneKeyDraft(scene, id, key)));
				}
			}
			for (var extra : extras) {
				var keys = sceneKeys.get(extra.scene().id());
				if (keys.stream().anyMatch(k -> k.compositionId() == extra.compositionId())) {
					continue;
				}
				var used = new HashSet<Integer>();
				keys.forEach(k -> used.add(k.slot()));
				var free = IntStream.rangeClosed(1, SceneKey.SLOTS).filter(s -> !used.contains(s))
						.findFirst();
				if (free.isEmpty()) {
					report.add("Scene " + label(extra.scene()) + ": no free key for \""
							+ drafts.get(extra.compositionId()).name() + "\" (from key " + extra.key().slot() + ")");
					continue;
				}
				keys.add(new SceneKey(free.getAsInt(), extra.compositionId(), extra.key().mode(), extra.key().fade()));
			}
		}

		private record SceneKeyDraft(LegacyScene scene, long compositionId, LegacyKey key) {
		}

		private Composition build(long id, Draft draft) {
			return switch (draft) {
				case Base base -> new Composition(id, 0, CompositionSpec.of(base.name()),
						List.of(CompositionStep.of(look(base.name(), id, base.look()))));
				case Copy copy when copy.composition().pattern() instanceof StepSequence sequence ->
						steps(id, copy, sequence);
				case Copy copy -> generated(id, copy);
			};
		}

		private Composition steps(long id, Copy copy, StepSequence sequence) {
			var name = copy.name();
			var playMode = copy.composition().once() ? PlayMode.ONCE
					: sequence.playMode() == StepSequence.PlayMode.PING_PONG ? PlayMode.PING_PONG : PlayMode.LOOP;
			if (sequence.steps().isEmpty()) {
				return new Composition(id, 0, spec(name, 1, 2, playMode, IntensityMode.ABSOLUTE, Optional.empty()),
						List.of());
			}
			var first = sequence.steps().getFirst();
			var steps = new ArrayList<CompositionStep>();
			for (int i = 0; i < sequence.steps().size(); i++) {
				var step = sequence.steps().get(i);
				var lookId = look(name + " – Step " + (i + 1), id, Look.of(step.values()));
				steps.add(new CompositionStep(lookId,
						step.fadeSeconds() == first.fadeSeconds() ? Optional.empty() : Optional.of(step.fadeSeconds()),
						step.holdSeconds() == first.holdSeconds() ? Optional.empty() : Optional.of(step.holdSeconds())));
			}
			return new Composition(id, 0, spec(name, first.fadeSeconds(), first.holdSeconds(), playMode,
					IntensityMode.ABSOLUTE, Optional.empty()), steps);
		}

		private Composition generated(long id, Copy copy) {
			var old = copy.composition();
			var target = old.target().orElse(new Target.Fixtures(List.of()));
			var fixtures = switch (target) {
				case Target.Group group -> groupMembers.getOrDefault(group.groupId(), List.of());
				case Target.Fixtures list -> list.fixtureIds();
			};
			var effect = withoutOwnIntensity(id, old.pattern(), fixtures);
			var settings = Optional.of(new GeneratorSettings(effect, target, old.perHead()));
			if (fixtures.isEmpty()) {
				report.add("\"" + copy.name() + "\" runs on no fixtures (its group is gone or empty) – it stays dark");
				return new Composition(id, 0, spec(copy.name(), 1, 2, PlayMode.LOOP, IntensityMode.ABSOLUTE, settings),
						List.of());
			}
			var generated = Generator.generate(effect, fixtures, old.perHead(), attributes);
			var steps = new ArrayList<CompositionStep>();
			for (var step : generated.steps()) {
				var lookId = look(copy.name() + " – Step " + (steps.size() + 1), id, Look.of(step.values()));
				steps.add(new CompositionStep(lookId, step.fadeSeconds(), step.holdSeconds()));
			}
			var playMode = old.once() ? PlayMode.ONCE
					: generated.playMode() == StepSequence.PlayMode.PING_PONG ? PlayMode.PING_PONG : PlayMode.LOOP;
			return new Composition(id, 0, spec(copy.name(), generated.fadeSeconds(), generated.holdSeconds(), playMode,
					generated.intensityMode(), settings), steps);
		}

		/**
		 * Color effects used to light only fixtures without an intensity of their own in the scene. Where the scenes
		 * give the fixtures an intensity, the effect now leaves the intensity to them.
		 */
		private Pattern withoutOwnIntensity(long compositionId, Pattern effect, List<Long> fixtures) {
			var lit = sceneCompositions.entrySet().stream()
					.filter(entry -> entry.getValue().stream().anyMatch(use -> use.compositionId() == compositionId))
					.map(entry -> scenes.get(entry.getKey()).look().values())
					.anyMatch(values -> fixtures.stream().anyMatch(id -> values.containsKey(id)
							&& values.get(id).get(DIMMER).orElse(0.0) > 0));
			if (!lit) {
				return effect;
			}
			return switch (effect) {
				case ColorCycle cycle -> new ColorCycle(cycle.colors(), cycle.holdSeconds(), cycle.fadeSeconds(),
						cycle.spread(), 0);
				case Rainbow rainbow -> new Rainbow(rainbow.cycleSeconds(), rainbow.saturation(), rainbow.spread(), 0);
				default -> effect;
			};
		}

		private static CompositionSpec spec(String name, double fade, double hold, PlayMode playMode,
				IntensityMode intensity, Optional<GeneratorSettings> generator) {
			return new CompositionSpec(name, fade, hold, playMode, intensity, generator);
		}

		private long add(Draft draft) {
			var id = nextComposition++;
			drafts.put(id, draft);
			return id;
		}

		private long look(String name, long owner, Look content) {
			var id = nextLook++;
			looks.add(new NamedLook(id, 0, new LookSpec(name, Optional.of(owner)), content));
			return id;
		}

		private String uniqueName(String name, String where) {
			var candidate = name;
			if (names.contains(candidate)) {
				candidate = name + " (" + where + ")";
			}
			for (int i = 2; names.contains(candidate); i++) {
				candidate = name + " (" + where + ", " + i + ")";
			}
			names.add(candidate);
			return candidate;
		}

		private static String label(LegacyScene scene) {
			return scene.spec().number().toString();
		}

		private static <T> Map<Long, List<T>> freeze(Map<Long, List<T>> lists) {
			var result = new HashMap<Long, List<T>>();
			lists.forEach((id, list) -> result.put(id, List.copyOf(list)));
			return result;
		}
	}
}
