package de.vinzens.lumen.domain.scene;

import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.AttributeGroup;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.output.FixtureValues;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SceneMergeTest {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	private static final Attribute RED = Attribute.of(ChannelFunction.RED);
	private static final Attribute PAN = Attribute.of(ChannelFunction.PAN);

	private final Map<Long, FixtureValues> scene = Map.of(
			1L, new FixtureValues(Map.of(DIMMER, 1.0, RED, 0.2, PAN, 0.5)),
			2L, new FixtureValues(Map.of(DIMMER, 0.5)));
	private final Map<Long, FixtureValues> programmer = Map.of(
			1L, new FixtureValues(Map.of(RED, 1.0, PAN, 0.9)),
			3L, new FixtureValues(Map.of(DIMMER, 0.7)));

	@Test
	void mergeTakesOnlyWhatTheChangesSet() {
		var merged = SceneValues.merge(scene, programmer, Optional.empty(), Set.of());

		assertThat(merged.get(1L).values()).containsEntry(DIMMER, 1.0).containsEntry(RED, 1.0).containsEntry(PAN, 0.9);
		assertThat(merged.get(2L).values()).containsExactly(Map.entry(DIMMER, 0.5));
		assertThat(merged.get(3L).values()).containsExactly(Map.entry(DIMMER, 0.7));
	}

	@Test
	void mergeCanBeLimitedToFixturesAndAttributeGroups() {
		var colorsOfFixture1 = SceneValues.merge(scene, programmer, Optional.of(Set.of(1L, 3L)),
				Set.of(AttributeGroup.COLOR));

		assertThat(colorsOfFixture1.get(1L).values()).containsEntry(RED, 1.0).containsEntry(PAN, 0.5);
		assertThat(colorsOfFixture1).doesNotContainKey(3L);

		var onlyFixture3 = SceneValues.merge(scene, programmer, Optional.of(Set.of(3L)), Set.of());
		assertThat(onlyFixture3.get(1L)).isEqualTo(scene.get(1L));
		assertThat(onlyFixture3).containsKey(3L);
	}

	@Test
	void everyFunctionBelongsToAGroup() {
		for (var function : ChannelFunction.values()) {
			assertThat(AttributeGroup.of(function)).isNotNull();
		}
		assertThat(AttributeGroup.of(ChannelFunction.COLOR_WHEEL)).isEqualTo(AttributeGroup.COLOR);
	}
}
