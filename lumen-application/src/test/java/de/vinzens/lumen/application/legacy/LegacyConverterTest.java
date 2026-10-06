package de.vinzens.lumen.application.legacy;

import de.vinzens.lumen.application.legacy.LegacyShow.LegacyComposition;
import de.vinzens.lumen.application.legacy.LegacyShow.LegacyKey;
import de.vinzens.lumen.application.legacy.LegacyShow.LegacyScene;
import de.vinzens.lumen.application.project.ShowContent;
import de.vinzens.lumen.domain.composition.Chase;
import de.vinzens.lumen.domain.composition.Color;
import de.vinzens.lumen.domain.composition.ColorCycle;
import de.vinzens.lumen.domain.composition.Composition;
import de.vinzens.lumen.domain.composition.FixtureAttributes;
import de.vinzens.lumen.domain.composition.KeyMode;
import de.vinzens.lumen.domain.composition.Pattern;
import de.vinzens.lumen.domain.composition.PlayMode;
import de.vinzens.lumen.domain.composition.StepSequence;
import de.vinzens.lumen.domain.composition.Target;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.look.Look;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.scene.SceneComposition;
import de.vinzens.lumen.domain.scene.SceneKey;
import de.vinzens.lumen.domain.scene.SceneNumber;
import de.vinzens.lumen.domain.scene.SceneSpec;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyConverterTest {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	private static final Chase CHASE = new Chase(0.5, 1, Chase.Direction.FORWARD, 0, 0, 1);
	private static final Map<Long, List<Long>> GROUPS = Map.of(7L, List.of(1L, 2L, 3L));

	@Test
	void theFixedValuesOfASceneBecomeItsFirstComposition() {
		var show = show(List.of(scene(1, "1", Map.of(5L, 0.8)), scene(2, "2", Map.of())), List.of(), List.of(),
				Map.of());

		var content = convert(show).content();

		var base = composition(content, uses(content, 1).getFirst().compositionId());
		assertThat(base.spec().name()).isEqualTo("1 Scene 1");
		assertThat(base.steps()).hasSize(1);
		var look = content.looks().stream().filter(l -> l.id() == base.steps().getFirst().lookId()).findFirst()
				.orElseThrow();
		assertThat(look.spec().owner()).as("its own look").contains(base.id());
		assertThat(look.look().values().get(5L).get(DIMMER)).contains(0.8);
		assertThat(uses(content, 2)).as("a scene without values gets no composition").isEmpty();
	}

	@Test
	void copiesLikeTheirLibraryCompositionAreThatComposition() {
		var library = composition(10, Optional.empty(), Optional.empty(), "Fair chase", CHASE);
		var inScene1 = composition(11, Optional.of(1L), Optional.of(10L), "Fair chase", CHASE);
		var inScene2 = withUse(composition(12, Optional.of(2L), Optional.of(10L), "Fair chase", CHASE), 0.5, true);
		var changed = composition(13, Optional.of(3L), Optional.of(10L), "Fair chase",
				new Chase(0.2, 1, Chase.Direction.FORWARD, 0, 0, 1));
		var show = show(List.of(scene(1, "1", Map.of()), scene(2, "2", Map.of()), scene(3, "3", Map.of())),
				List.of(library, inScene1, inScene2, changed), List.of(), Map.of());

		var content = convert(show).content();

		var shared = uses(content, 1).getFirst().compositionId();
		assertThat(uses(content, 2)).singleElement().satisfies(use -> {
			assertThat(use.compositionId()).as("continues from scene 1 into 2").isEqualTo(shared);
			assertThat(use.level()).isEqualTo(0.5);
			assertThat(use.restart()).isTrue();
		});
		var other = uses(content, 3).getFirst().compositionId();
		assertThat(other).isNotEqualTo(shared);
		assertThat(composition(content, other).spec().name()).isEqualTo("Fair chase (3)");
		assertThat(content.compositions()).hasSize(2);
	}

	@Test
	void effectsAreGeneratedIntoStepsAndKeepTheirSettings() {
		var chase = composition(11, Optional.of(1L), Optional.empty(), "Chase", CHASE);
		var gone = new LegacyComposition(12, Optional.of(1L), Optional.empty(), 1, "Lost", CHASE,
				Optional.of(new Target.Group(99)), 1, 1, false, false, false);
		var show = show(List.of(scene(1, "1", Map.of())), List.of(chase, gone), List.of(), Map.of());

		var result = convert(show);

		var generated = composition(result.content(), uses(result.content(), 1).get(0).compositionId());
		assertThat(generated.steps()).as("one step per fixture of the group").hasSize(3);
		assertThat(generated.spec().generator()).get().satisfies(settings -> {
			assertThat(settings.effect()).isEqualTo(CHASE);
			assertThat(settings.target()).isEqualTo(new Target.Group(7));
		});
		assertThat(composition(result.content(), uses(result.content(), 1).get(1).compositionId()).steps()).isEmpty();
		assertThat(result.report()).anySatisfy(line -> assertThat(line).contains("\"Lost\"", "stays dark"));
	}

	@Test
	void colorEffectsLeaveTheIntensityToTheSceneWhereItHadOne() {
		var cycle = new ColorCycle(List.of(new Color(1, 0, 0), new Color(0, 0, 1)), 1, 1, 0, 1);
		var lit = composition(11, Optional.of(1L), Optional.empty(), "Colors", cycle);
		var alone = composition(12, Optional.of(2L), Optional.empty(), "Colors alone", cycle);
		var show = show(List.of(scene(1, "1", Map.of(1L, 0.5)), scene(2, "2", Map.of())), List.of(lit, alone),
				List.of(), Map.of());

		var content = convert(show).content();

		assertThat(firstStepDimmer(content, uses(content, 1).get(1).compositionId())).as("scene 1 lights it").isEmpty();
		assertThat(firstStepDimmer(content, uses(content, 2).getFirst().compositionId())).contains(1.0);
	}

	@Test
	void stepSequencesKeepTheirStepsAndTimes() {
		var steps = new StepSequence(List.of(
				new StepSequence.Step(2, 1, Map.of(1L, values(1))),
				new StepSequence.Step(5, 1, Map.of(2L, values(1)))), StepSequence.PlayMode.LOOP);
		var once = new LegacyComposition(11, Optional.of(1L), Optional.empty(), 0, "Sunrise", steps, Optional.empty(),
				1, 1, false, true, false);
		var show = show(List.of(scene(1, "1", Map.of())), List.of(once), List.of(), Map.of());

		var content = convert(show).content();

		var sunrise = composition(content, uses(content, 1).getFirst().compositionId());
		assertThat(sunrise.spec().playMode()).isEqualTo(PlayMode.ONCE);
		assertThat(sunrise.spec().holdSeconds()).isEqualTo(2);
		assertThat(sunrise.steps().get(0).holdSeconds()).isEmpty();
		assertThat(sunrise.steps().get(1).holdSeconds()).contains(5.0);
	}

	@Test
	void keysBecomeKeysOfTheScenesTheyWereUsableIn() {
		var storm = withUse(composition(20, Optional.empty(), Optional.empty(), "Storm", CHASE), 0.6, false);
		var stormKey = new LegacyKey(1, KeyMode.FLASH, Duration.ofSeconds(1), Optional.of(storm), Optional.empty());
		var sceneKey = new LegacyKey(2, KeyMode.TOGGLE, Duration.ZERO, Optional.empty(), Optional.of(9L));
		var keyScene = new LegacyScene(9, new SceneSpec(SceneNumber.of(9), "Police", Optional.empty(),
				Optional.empty(), true), Look.of(Map.of(4L, values(1))));
		var siren = composition(21, Optional.of(9L), Optional.empty(), "Siren", CHASE);
		var show = show(List.of(scene(1, "1", Map.of()), scene(2, "2", Map.of()), keyScene), List.of(siren),
				List.of(stormKey, sceneKey), Map.of(1L, Set.of(1, 2), 2L, Set.of(1)));

		var result = convert(show);
		var keys = result.content().sceneKeys();

		assertThat(keys.get(1L)).extracting(SceneKey::slot).containsExactly(1, 2, 3);
		assertThat(keys.get(1L).get(1).compositionId()).as("the key scene's values").isEqualTo(
				uses(result.content(), 9).get(0).compositionId());
		assertThat(keys.get(1L).get(2).compositionId()).as("its siren on a free key").isEqualTo(
				uses(result.content(), 9).get(1).compositionId());
		assertThat(keys.get(2L)).extracting(SceneKey::slot).containsExactly(1);
		assertThat(keys.get(9L)).as("not in the cue list: no keys").isEmpty();
		assertThat(result.report()).anySatisfy(line -> assertThat(line).contains("Key 1", "full level"));
		assertThat(result.report()).anySatisfy(line -> assertThat(line).contains("Key 2", "free keys"));
	}

	private static LegacyConverter.Result convert(LegacyShow show) {
		return LegacyConverter.convert(show, GROUPS, FixtureAttributes.ALL);
	}

	private static LegacyShow show(List<LegacyScene> scenes, List<LegacyComposition> compositions,
			List<LegacyKey> keys, Map<Long, Set<Integer>> sceneKeys) {
		return new LegacyShow(scenes, compositions, keys, sceneKeys);
	}

	private static LegacyScene scene(long id, String number, Map<Long, Double> dimmers) {
		var values = new HashMap<Long, FixtureValues>();
		dimmers.forEach((fixtureId, level) -> values.put(fixtureId, values(level)));
		return new LegacyScene(id, new SceneSpec(SceneNumber.parse(number), "Scene " + number, Optional.empty(),
				Optional.empty()), Look.of(values));
	}

	private static FixtureValues values(double dimmer) {
		return new FixtureValues(Map.of(DIMMER, dimmer));
	}

	/** An effect on group 7 (fixtures 1–3). */
	private static LegacyComposition composition(long id, Optional<Long> sceneId, Optional<Long> sourceId, String name,
			Pattern pattern) {
		return new LegacyComposition(id, sceneId, sourceId, 0, name, pattern, Optional.of(new Target.Group(7)), 1, 1,
				false, false, false);
	}

	private static LegacyComposition withUse(LegacyComposition c, double level, boolean restart) {
		return new LegacyComposition(c.id(), c.sceneId(), c.sourceId(), c.position(), c.name(), c.pattern(),
				c.target(), level, c.speedFactor(), restart, c.once(), c.perHead());
	}

	private static List<SceneComposition> uses(ShowContent content, long sceneId) {
		return content.sceneCompositions().getOrDefault(sceneId, List.of());
	}

	private static Composition composition(ShowContent content, long id) {
		return content.compositions().stream().filter(c -> c.id() == id).findFirst().orElseThrow();
	}

	private static Optional<Double> firstStepDimmer(ShowContent content, long compositionId) {
		var lookId = composition(content, compositionId).steps().getFirst().lookId();
		var look = content.looks().stream().filter(l -> l.id() == lookId).findFirst().orElseThrow();
		return look.look().values().get(1L).get(DIMMER);
	}
}
