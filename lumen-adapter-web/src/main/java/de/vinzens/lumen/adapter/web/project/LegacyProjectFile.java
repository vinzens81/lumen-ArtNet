package de.vinzens.lumen.adapter.web.project;

import de.vinzens.lumen.adapter.web.composition.CompositionDtos;
import de.vinzens.lumen.adapter.web.composition.CompositionDtos.TargetDto;
import de.vinzens.lumen.adapter.web.output.PresetRefsJson;
import de.vinzens.lumen.adapter.web.project.ProjectDtos.ProjectRequest;
import de.vinzens.lumen.adapter.web.project.ProjectFile.FaderEntry;
import de.vinzens.lumen.adapter.web.project.ProjectFile.FixtureEntry;
import de.vinzens.lumen.adapter.web.project.ProjectFile.GroupEntry;
import de.vinzens.lumen.adapter.web.project.ProjectFile.PresetEntry;
import de.vinzens.lumen.adapter.web.project.ProjectFile.StagePlanEntry;
import de.vinzens.lumen.adapter.web.project.ProjectFile.UniverseEntry;
import de.vinzens.lumen.application.legacy.LegacyShow;
import de.vinzens.lumen.application.legacy.LegacyShow.LegacyComposition;
import de.vinzens.lumen.application.legacy.LegacyShow.LegacyKey;
import de.vinzens.lumen.application.legacy.LegacyShow.LegacyScene;
import de.vinzens.lumen.application.project.port.in.TransferProjectUseCase.ProjectExport;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.composition.KeyMode;
import de.vinzens.lumen.domain.composition.Pattern;
import de.vinzens.lumen.domain.composition.StepSequence;
import de.vinzens.lumen.domain.look.Look;
import de.vinzens.lumen.domain.party.PartyMatrix;
import de.vinzens.lumen.domain.project.StagePlan;
import de.vinzens.lumen.domain.scene.SceneNumber;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static de.vinzens.lumen.adapter.web.project.ProjectFile.list;
import static de.vinzens.lumen.adapter.web.project.ProjectFile.fixtureValues;

/**
 * Project files of version 1, from before looks: scenes with fixed values, copies of library compositions in scenes,
 * project-wide extra keys. The import converts them into looks, compositions and scenes.
 */
record LegacyProjectFile(String format, Integer version, Instant exportedAt, ProjectRequest project,
		List<UniverseEntry> universes, List<FixtureEntry> fixtures, List<GroupEntry> groups,
		List<SceneEntry> scenes, StagePlanEntry stagePlan, List<CompositionEntry> compositions,
		List<ExtraKeyEntry> extraKeys, List<PresetEntry> presets,
		List<FaderEntry> faders) {

	ProjectExport toExport(JsonMapper json) {
		if (project == null) {
			throw new DomainValidationException(List.of("project is missing"));
		}
		// scenes have no id in the file: compositions, keys and faders refer to them by number
		var sceneList = list(scenes);
		var sceneIds = new HashMap<String, Long>();
		var legacyScenes = new ArrayList<LegacyScene>();
		var sceneKeys = new HashMap<Long, Set<Integer>>();
		for (int i = 0; i < sceneList.size(); i++) {
			var entry = sceneList.get(i);
			var scene = entry.toScene(i + 1);
			sceneIds.put(scene.spec().number().toString(), scene.id());
			legacyScenes.add(scene);
			if (entry.extraKeys() != null && !entry.extraKeys().isEmpty()) {
				sceneKeys.put(scene.id(), Set.copyOf(entry.extraKeys()));
			}
		}
		var positions = new HashMap<String, Integer>();
		var legacyCompositions = new ArrayList<LegacyComposition>();
		for (var composition : list(compositions)) {
			var scene = composition.sceneNumber() == null ? "" : composition.sceneNumber();
			var position = positions.merge(scene, 1, Integer::sum) - 1;
			legacyCompositions.add(composition.toComposition(sceneIds, position, json));
		}
		var keys = list(extraKeys).stream().map(key -> key.toKey(sceneIds, json)).toList();
		return new ProjectExport(project.toSpec(),
				list(universes).stream().map(UniverseEntry::toUniverse).toList(),
				list(fixtures).stream().map(FixtureEntry::toFixture).toList(),
				list(groups).stream().map(GroupEntry::toGroup).toList(),
				List.of(), List.of(), List.of(),
				Optional.ofNullable(stagePlan).map(plan -> new StagePlan(plan.contentType(),
						plan.data() == null ? new byte[0] : plan.data())),
				PartyMatrix.EMPTY,
				list(presets).stream().map(PresetEntry::toPreset).toList(),
				List.of(),
				new LegacyShow(legacyScenes, legacyCompositions, keys, sceneKeys,
						list(faders).stream().map(f -> f.toLegacyFader(sceneIds)).toList()));
	}

	/**
	 * @param keyOnly   only played by extra keys
	 * @param values    normalized values (0–1) per fixture id and attribute
	 * @param presets   presets the values come from: fixture id → attribute group → preset id
	 * @param extraKeys extra keys usable in this scene
	 */
	record SceneEntry(String number, String name, String cueNote, Long autoFollowMs, Boolean keyOnly, Long fadeInMs,
			Long fadeOutMs, Long delayMs, String fadeCurve, String script, Map<Long, Map<String, Double>> values,
			Map<Long, Map<String, Long>> presets, List<Integer> extraKeys) {

		LegacyScene toScene(long id) {
			return new LegacyScene(id, ProjectFile.sceneSpec(number, name, cueNote, autoFollowMs, fadeInMs,
					fadeOutMs, delayMs, fadeCurve, script), new Look(fixtureValues(values), PresetRefsJson.fromJson(presets)),
					Boolean.TRUE.equals(keyOnly));
		}
	}

	/**
	 * A composition in the library ({@code sceneNumber} null) or a copy in a scene.
	 *
	 * @param sourceId id of the library composition a copy was made from
	 * @param steps    only for kind {@code steps}
	 */
	record CompositionEntry(Long id, String sceneNumber, Long sourceId, String name, String kind, JsonNode params,
			TargetDto target, List<StepEntry> steps, Double level, Double speedFactor, Boolean restart, Boolean once,
			Boolean perHead) {

		LegacyComposition toComposition(Map<String, Long> sceneIds, int position, JsonMapper json) {
			Optional<Long> sceneId = Optional.empty();
			if (sceneNumber != null) {
				var number = SceneNumber.parse(sceneNumber).toString();
				sceneId = Optional.of(Optional.ofNullable(sceneIds.get(number)).orElseThrow(() ->
						new DomainValidationException(List.of("Composition '" + name + "' refers to unknown scene "
								+ sceneNumber))));
			}
			Pattern pattern;
			if ("steps".equals(kind)) {
				var playMode = params == null ? "loop" : params.path("playMode").asString("loop");
				pattern = new StepSequence(list(steps).stream().map(StepEntry::toStep).toList(),
						StepSequence.PlayMode.valueOf(playMode.toUpperCase(Locale.ROOT)));
			} else {
				pattern = CompositionDtos.effect(kind, params, json);
			}
			return new LegacyComposition(Objects.requireNonNullElse(id, 0L), sceneId, Optional.ofNullable(sourceId),
					position, name, pattern, "steps".equals(kind) ? Optional.empty()
							: Optional.ofNullable(target).map(TargetDto::toTarget),
					Objects.requireNonNullElse(level, 1.0), Objects.requireNonNullElse(speedFactor, 1.0),
					Boolean.TRUE.equals(restart), Boolean.TRUE.equals(once), Boolean.TRUE.equals(perHead));
		}
	}

	/** @param values normalized values (0–1) per fixture id and attribute */
	record StepEntry(Long holdMs, Long fadeMs, Map<Long, Map<String, Double>> values) {

		StepSequence.Step toStep() {
			return new StepSequence.Step(Objects.requireNonNullElse(holdMs, 1000L) / 1000.0,
					Objects.requireNonNullElse(fadeMs, 0L) / 1000.0, fixtureValues(values));
		}
	}

	/**
	 * An extra key (mode toggle or flash) with its own composition or a scene.
	 *
	 * @param sceneNumber the scene the key plays, instead of a composition
	 */
	record ExtraKeyEntry(Integer slot, String mode, Long fadeMs, CompositionEntry composition, String sceneNumber) {

		LegacyKey toKey(Map<String, Long> sceneIds, JsonMapper json) {
			if (slot == null || (composition == null) == (sceneNumber == null)) {
				throw new DomainValidationException(List.of("An extra key needs a slot and either a composition or "
						+ "a sceneNumber"));
			}
			var keyMode = mode == null ? KeyMode.TOGGLE : KeyMode.parse(mode);
			var fade = Duration.ofMillis(Objects.requireNonNullElse(fadeMs, 0L));
			if (composition != null) {
				return new LegacyKey(slot, keyMode, fade, Optional.of(composition.toComposition(sceneIds, 0, json)),
						Optional.empty());
			}
			var sceneId = sceneIds.get(SceneNumber.parse(sceneNumber).toString());
			if (sceneId == null) {
				throw new DomainValidationException(List.of("Extra key " + slot + " refers to unknown scene "
						+ sceneNumber));
			}
			return new LegacyKey(slot, keyMode, fade, Optional.empty(), Optional.of(sceneId));
		}
	}
}
