package de.vinzens.lumen.domain.look;

import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.AttributeGroup;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.output.PresetRefs;
import de.vinzens.lumen.domain.output.Programmer;
import de.vinzens.lumen.domain.preset.Preset;
import de.vinzens.lumen.domain.preset.PresetSpec;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static de.vinzens.lumen.domain.library.AttributeGroup.COLOR;
import static de.vinzens.lumen.domain.library.AttributeGroup.POSITION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LookMergeTest {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	private static final Attribute RED = Attribute.of(ChannelFunction.RED);
	private static final Attribute BLUE = Attribute.of(ChannelFunction.BLUE);
	private static final Attribute PAN = Attribute.of(ChannelFunction.PAN);

	private final Look scene = Look.of(Map.of(
			1L, new FixtureValues(Map.of(DIMMER, 1.0, RED, 0.2, PAN, 0.5)),
			2L, new FixtureValues(Map.of(DIMMER, 0.5))));
	private final Look programmer = Look.of(Map.of(
			1L, new FixtureValues(Map.of(RED, 1.0, PAN, 0.9)),
			3L, new FixtureValues(Map.of(DIMMER, 0.7))));

	@Test
	void mergeTakesOnlyWhatTheChangesSet() {
		var merged = scene.merge(programmer, Optional.empty(), Set.of()).values();

		assertThat(merged.get(1L).values()).containsEntry(DIMMER, 1.0).containsEntry(RED, 1.0).containsEntry(PAN, 0.9);
		assertThat(merged.get(2L).values()).containsExactly(Map.entry(DIMMER, 0.5));
		assertThat(merged.get(3L).values()).containsExactly(Map.entry(DIMMER, 0.7));
	}

	@Test
	void mergeCanBeLimitedToFixturesAndAttributeGroups() {
		var colorsOfFixture1 = scene.merge(programmer, Optional.of(Set.of(1L, 3L)), Set.of(COLOR)).values();

		assertThat(colorsOfFixture1.get(1L).values()).containsEntry(RED, 1.0).containsEntry(PAN, 0.5);
		assertThat(colorsOfFixture1).doesNotContainKey(3L);

		var onlyFixture3 = scene.merge(programmer, Optional.of(Set.of(3L)), Set.of()).values();
		assertThat(onlyFixture3.get(1L)).isEqualTo(scene.values().get(1L));
		assertThat(onlyFixture3).containsKey(3L);
	}

	@Test
	void mergeTakesPresetReferencesOfTheMergedGroups() {
		var withRefs = new Look(scene.values(), PresetRefs.NONE.with(1L, COLOR, 7L).with(1L, POSITION, 8L));
		var changes = new Look(programmer.values(), PresetRefs.NONE.with(1L, POSITION, 9L));

		var merged = withRefs.merge(changes, Optional.empty(), Set.of()).presets();

		assertThat(merged.presetOf(1L, POSITION)).as("taken from the changes").contains(9L);
		assertThat(merged.presetOf(1L, COLOR)).as("red was set by hand").isEmpty();

		var onlyIntensity = withRefs.merge(changes, Optional.empty(), Set.of(AttributeGroup.INTENSITY)).presets();
		assertThat(onlyIntensity.presetOf(1L, COLOR)).contains(7L);
	}

	@Test
	void fixturesReferringToAPresetTakeItsCurrentValues() {
		var warm = new Preset(7, 1, new PresetSpec("Warm", COLOR), Map.of(
				1L, new FixtureValues(Map.of(RED, 0.9, BLUE, 0.1))));
		var look = new Look(Map.of(
				1L, new FixtureValues(Map.of(DIMMER, 1.0, RED, 0.5, PAN, 0.5)),
				2L, new FixtureValues(Map.of(DIMMER, 1.0, RED, 0.5))),
				PresetRefs.NONE.with(1L, COLOR, 7L).with(2L, COLOR, 7L));

		var updated = look.withPreset(warm).values();

		assertThat(updated.get(1L).values()).containsEntry(DIMMER, 1.0).containsEntry(RED, 0.9)
				.containsEntry(BLUE, 0.1).containsEntry(PAN, 0.5);
		assertThat(updated.get(2L)).as("no values in the preset").isEqualTo(look.values().get(2L));
	}

	@Test
	void settingValuesByHandDropsThePresetReferenceOfTheirGroup() {
		var programmer = Programmer.EMPTY
				.withPreset(1L, new FixtureValues(Map.of(RED, 1.0)), COLOR, 7L)
				.withPreset(1L, new FixtureValues(Map.of(PAN, 0.3)), POSITION, 8L)
				.with(1L, new FixtureValues(Map.of(BLUE, 0.4, DIMMER, 1.0)));

		assertThat(programmer.presets().presetOf(1L, COLOR)).isEmpty();
		assertThat(programmer.presets().presetOf(1L, POSITION)).contains(8L);
		assertThat(programmer.without(1L).presets().isEmpty()).isTrue();
	}

	@Test
	void presetsHoldOnlyTheirGroup() {
		assertThatThrownBy(() -> new Preset(1, 1, new PresetSpec("Warm", COLOR),
				Map.of(1L, new FixtureValues(Map.of(PAN, 0.5)))))
				.hasMessageContaining("cannot hold pan");
		assertThatThrownBy(() -> new PresetSpec("Bright", AttributeGroup.INTENSITY)).hasMessageContaining("group");
		assertThat(Preset.valuesOf(new PresetSpec("Warm", COLOR), programmer.values()))
				.containsOnlyKeys(1L).extractingByKey(1L).isEqualTo(new FixtureValues(Map.of(RED, 1.0)));
	}

	@Test
	void everyFunctionBelongsToAGroup() {
		for (var function : ChannelFunction.values()) {
			assertThat(AttributeGroup.of(function)).isNotNull();
		}
		assertThat(AttributeGroup.of(ChannelFunction.COLOR_WHEEL)).isEqualTo(COLOR);
	}
}
