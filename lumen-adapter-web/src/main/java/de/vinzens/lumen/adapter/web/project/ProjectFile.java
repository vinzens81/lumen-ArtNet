package de.vinzens.lumen.adapter.web.project;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.vinzens.lumen.adapter.web.composition.CompositionDtos.CompositionRequest;
import de.vinzens.lumen.adapter.web.composition.CompositionDtos.CompositionResponse;
import de.vinzens.lumen.adapter.web.composition.CompositionDtos.GeneratorDto;
import de.vinzens.lumen.adapter.web.composition.CompositionDtos.StepDto;
import de.vinzens.lumen.adapter.web.output.PresetRefsJson;
import de.vinzens.lumen.adapter.web.output.ValuesJson;
import de.vinzens.lumen.adapter.web.project.PatchDtos.ArtNetDto;
import de.vinzens.lumen.adapter.web.project.PatchDtos.PositionDto;
import de.vinzens.lumen.adapter.web.project.PatchDtos.StageShapeDto;
import de.vinzens.lumen.adapter.web.project.PatchDtos.UniverseRequest;
import de.vinzens.lumen.adapter.web.project.PatchDtos.UniverseResponse;
import de.vinzens.lumen.adapter.web.project.ProjectDtos.ProjectRequest;
import de.vinzens.lumen.adapter.web.scene.TimingJson;
import de.vinzens.lumen.application.legacy.LegacyShow;
import de.vinzens.lumen.application.legacy.LegacyShow.LegacyFader;
import de.vinzens.lumen.application.project.port.in.TransferProjectUseCase.ProjectExport;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.composition.Composition;
import de.vinzens.lumen.domain.composition.Fader;
import de.vinzens.lumen.domain.composition.KeyMode;
import de.vinzens.lumen.domain.library.AttributeGroup;
import de.vinzens.lumen.domain.look.Look;
import de.vinzens.lumen.domain.look.LookSpec;
import de.vinzens.lumen.domain.look.NamedLook;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.party.PartyCell;
import de.vinzens.lumen.domain.party.PartyMatrix;
import de.vinzens.lumen.domain.preset.Preset;
import de.vinzens.lumen.domain.preset.PresetSpec;
import de.vinzens.lumen.domain.project.FixtureSpec;
import de.vinzens.lumen.domain.project.Group;
import de.vinzens.lumen.domain.project.GroupSpec;
import de.vinzens.lumen.domain.project.PatchedFixture;
import de.vinzens.lumen.domain.project.Position;
import de.vinzens.lumen.domain.project.StagePlan;
import de.vinzens.lumen.domain.project.Universe;
import de.vinzens.lumen.domain.scene.Scene;
import de.vinzens.lumen.domain.scene.SceneComposition;
import de.vinzens.lumen.domain.scene.SceneKey;
import de.vinzens.lumen.domain.scene.SceneNumber;
import de.vinzens.lumen.domain.scene.SceneSpec;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * File format of an exported project (JSON), version {@value #VERSION}. Ids only connect the parts of the file:
 * fixtures refer to universes, groups and looks to fixtures, looks to presets and their composition, compositions to
 * looks, scenes and faders to compositions, fields of the party matrix to compositions and scene numbers. On import
 * everything gets new ids. Files of version 1 (before looks) are read by {@link LegacyProjectFile}. In files of version
 * 2, faders played a scene (by number) and scenes could be outside the cue list: such a fader plays the first
 * composition of its scene, such scenes are left out. Party buttons of older files are left out.
 *
 * @param format  always {@value #FORMAT}
 * @param version format version, currently {@value #VERSION}
 */
record ProjectFile(String format, Integer version, Instant exportedAt, ProjectRequest project,
		List<UniverseEntry> universes, List<FixtureEntry> fixtures, List<GroupEntry> groups, List<LookEntry> looks,
		List<CompositionEntry> compositions, List<SceneEntry> scenes, StagePlanEntry stagePlan,
		PartyMatrixEntry partyMatrix, List<PresetEntry> presets, List<FaderEntry> faders) {

	static final String FORMAT = "lumen-project";
	static final int VERSION = 3;

	static ProjectFile from(ProjectExport export, Instant now, JsonMapper json) {
		var spec = export.project();
		var sceneNumbers = new HashMap<Long, String>();
		export.scenes().forEach(scene -> sceneNumbers.put(scene.id(), scene.spec().number().toString()));
		return new ProjectFile(FORMAT, VERSION, now,
				new ProjectRequest(spec.name(), spec.venue().orElse(null), spec.fadeIn().toMillis(),
						spec.fadeOut().toMillis(), spec.fadeCurve().id()),
				export.universes().stream().map(UniverseEntry::from).toList(),
				export.fixtures().stream().map(FixtureEntry::from).toList(),
				export.groups().stream().map(GroupEntry::from).toList(),
				export.looks().stream().map(LookEntry::from).toList(),
				export.compositions().stream().map(c -> CompositionEntry.from(c, json)).toList(),
				export.scenes().stream().map(SceneEntry::from).toList(),
				export.stagePlan().map(plan -> new StagePlanEntry(plan.contentType(), plan.data())).orElse(null),
				PartyMatrixEntry.from(export.partyMatrix(), sceneNumbers),
				export.presets().stream().map(PresetEntry::from).toList(),
				export.faders().stream().map(f -> new FaderEntry(f.slot(), f.name().orElse(null), f.compositionId(),
						null)).toList());
	}

	/** Reads a file of any supported version. */
	static ProjectExport read(JsonNode file, JsonMapper json) {
		var format = file.path("format").asString(null);
		if (!FORMAT.equals(format)) {
			throw new DomainValidationException(List.of("This is not a Lumen project file (format '" + format + "')"));
		}
		var version = file.path("version").asInt(0);
		return switch (version) {
			case 1 -> json.treeToValue(file, LegacyProjectFile.class).toExport(json);
			case 2, VERSION -> json.treeToValue(file, ProjectFile.class).toExport(json);
			default -> throw new DomainValidationException(List.of("Project file version " + version
					+ " is not supported (supported: up to " + VERSION + ")"));
		};
	}

	ProjectExport toExport(JsonMapper json) {
		if (project == null) {
			throw new DomainValidationException(List.of("project is missing"));
		}
		// version 2: faders refer to scenes by number, scenes outside the cue list are left out
		var sceneList = list(scenes);
		var firstComposition = new HashMap<String, Long>();
		var sceneIds = new HashMap<String, Long>();
		var exportScenes = new ArrayList<Scene>();
		for (int i = 0; i < sceneList.size(); i++) {
			var entry = sceneList.get(i);
			var scene = entry.toScene(i + 1);
			scene.compositions().stream().findFirst().ifPresent(use ->
					firstComposition.put(scene.spec().number().toString(), use.compositionId()));
			if (!Boolean.TRUE.equals(entry.outsideCueList())) {
				exportScenes.add(scene);
				sceneIds.put(scene.spec().number().toString(), scene.id());
			}
		}
		return new ProjectExport(project.toSpec(),
				list(universes).stream().map(UniverseEntry::toUniverse).toList(),
				list(fixtures).stream().map(FixtureEntry::toFixture).toList(),
				list(groups).stream().map(GroupEntry::toGroup).toList(),
				list(looks).stream().map(LookEntry::toLook).toList(),
				list(compositions).stream().map(c -> c.toComposition(json)).toList(),
				exportScenes,
				Optional.ofNullable(stagePlan).map(plan -> new StagePlan(plan.contentType(),
						plan.data() == null ? new byte[0] : plan.data())),
				partyMatrix == null ? PartyMatrix.EMPTY : partyMatrix.toMatrix(sceneIds),
				list(presets).stream().map(PresetEntry::toPreset).toList(),
				list(faders).stream().flatMap(f -> f.toFader(firstComposition).stream()).toList(),
				LegacyShow.EMPTY);
	}

	/**
	 * A fader of the show; files from before faders have none.
	 *
	 * @param compositionId the composition it plays
	 * @param scene         files of version 1 and 2: number of the scene it played
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	record FaderEntry(Integer slot, String name, Long compositionId, String scene) {

		/** @param firstComposition version 2: the first composition per scene number; a fader of an empty scene is left out */
		Optional<Fader> toFader(Map<String, Long> firstComposition) {
			if (slot == null || (compositionId == null && scene == null)) {
				throw new DomainValidationException(List.of("A fader has no slot or composition"));
			}
			if (compositionId != null) {
				return Optional.of(new Fader(slot, Optional.ofNullable(name), compositionId));
			}
			return Optional.ofNullable(firstComposition.get(SceneNumber.parse(scene).toString()))
					.map(id -> new Fader(slot, Optional.ofNullable(name), id));
		}

		LegacyFader toLegacyFader(Map<String, Long> sceneIds) {
			if (slot == null || scene == null) {
				throw new DomainValidationException(List.of("A fader has no slot or scene"));
			}
			var sceneId = sceneIds.get(SceneNumber.parse(scene).toString());
			if (sceneId == null) {
				throw new DomainValidationException(List.of("Fader " + slot + " refers to unknown scene " + scene));
			}
			return new LegacyFader(slot, Optional.ofNullable(name), sceneId);
		}
	}

	/**
	 * A preset with its values per fixture id; files from before presets have none.
	 *
	 * @param id    connects scenes to the preset within the file
	 * @param group "color", "position" or "beam"
	 */
	record PresetEntry(Long id, String name, String group, Map<Long, Map<String, Double>> values) {

		static PresetEntry from(Preset preset) {
			return new PresetEntry(preset.id(), preset.spec().name(), preset.spec().group().id(),
					ValuesJson.toJson(preset.values()));
		}

		Preset toPreset() {
			if (id == null || group == null) {
				throw new DomainValidationException(List.of("Preset '" + name + "' has no id or group"));
			}
			var fixtureValues = new HashMap<Long, FixtureValues>();
			(values == null ? Map.<Long, Map<String, Double>>of() : values)
					.forEach((fixtureId, attributes) -> fixtureValues.put(fixtureId, ValuesJson.fromJson(attributes)));
			return new Preset(id, 0, new PresetSpec(name, AttributeGroup.parse(group)), fixtureValues);
		}
	}

	/**
	 * The party matrix; files before version 3 have none.
	 *
	 * @param separators rows a line is drawn above
	 */
	record PartyMatrixEntry(Integer rows, Integer columns, List<Integer> separators, List<PartyCellEntry> cells) {

		static PartyMatrixEntry from(PartyMatrix matrix, Map<Long, String> sceneNumbers) {
			return new PartyMatrixEntry(matrix.rows(), matrix.columns(), matrix.separators().stream().sorted().toList(),
					matrix.cells().stream().map(cell -> PartyCellEntry.from(cell, sceneNumbers)).toList());
		}

		PartyMatrix toMatrix(Map<String, Long> sceneIds) {
			return new PartyMatrix(Objects.requireNonNullElse(rows, 4), Objects.requireNonNullElse(columns, 4),
					Set.copyOf(list(separators)), list(cells).stream().map(cell -> cell.toCell(sceneIds)).toList());
		}
	}

	/**
	 * A field of the party matrix.
	 *
	 * @param scene         number of the scene it plays
	 * @param compositionId the composition it plays
	 * @param mode          toggle or flash
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	record PartyCellEntry(int row, int column, String label, String scene, Long compositionId, String color,
			String borderColor, String mode, String group) {

		static PartyCellEntry from(PartyCell cell, Map<Long, String> sceneNumbers) {
			return new PartyCellEntry(cell.row(), cell.column(), cell.label().orElse(null),
					cell.sceneId().map(sceneNumbers::get).orElse(null), cell.compositionId().orElse(null),
					cell.color(), cell.borderColor(), cell.mode().id(), cell.group().orElse(null));
		}

		PartyCell toCell(Map<String, Long> sceneIds) {
			var sceneId = Optional.ofNullable(scene).map(number -> {
				var id = sceneIds.get(SceneNumber.parse(number).toString());
				if (id == null) {
					throw new DomainValidationException(List.of("Field " + (row + 1) + "/" + (column + 1)
							+ " of the party matrix refers to unknown scene " + number));
				}
				return id;
			});
			return new PartyCell(row, column, Optional.ofNullable(label), sceneId, Optional.ofNullable(compositionId),
					Objects.requireNonNullElse(color, PartyCell.DEFAULT_COLOR),
					Objects.requireNonNullElse(borderColor, PartyCell.DEFAULT_BORDER),
					mode == null ? KeyMode.TOGGLE : KeyMode.parse(mode), Optional.ofNullable(group));
		}
	}

	static long require(Long id, String what) {
		if (id == null) {
			throw new DomainValidationException(List.of(what + " is missing"));
		}
		return id;
	}

	static <T> List<T> list(List<T> entries) {
		return entries == null ? List.of() : entries.stream().filter(Objects::nonNull).toList();
	}

	/**
	 * A look with its values per fixture id.
	 *
	 * @param owner   id of the composition the look belongs to, {@code null} = shared
	 * @param presets presets the values come from: fixture id → attribute group → preset id
	 */
	record LookEntry(Long id, String name, Long owner, Map<Long, Map<String, Double>> values,
			Map<Long, Map<String, Long>> presets) {

		static LookEntry from(NamedLook look) {
			return new LookEntry(look.id(), look.spec().name(), look.spec().owner().orElse(null),
					ValuesJson.toJson(look.look().values()), PresetRefsJson.toJson(look.look().presets()));
		}

		NamedLook toLook() {
			return new NamedLook(require(id, "look id"), 0, new LookSpec(name, Optional.ofNullable(owner)),
					new Look(fixtureValues(values), PresetRefsJson.fromJson(presets)));
		}
	}

	/** A composition; the fields are the same as in the composition API. */
	record CompositionEntry(Long id, String name, Long fadeMs, Long holdMs, String playMode, String intensityMode,
			GeneratorDto generator, List<StepDto> steps) {

		static CompositionEntry from(Composition composition, JsonMapper json) {
			var api = CompositionResponse.from(composition);
			var generator = api.generator() == null ? null : new GeneratorDto(api.generator().kind(),
					json.valueToTree(api.generator().params()), api.generator().target(), api.generator().perHead());
			return new CompositionEntry(api.id(), api.name(), api.fadeMs(), api.holdMs(), api.playMode(),
					api.intensityMode(), generator, api.steps());
		}

		Composition toComposition(JsonMapper json) {
			var request = new CompositionRequest(name, fadeMs, holdMs, playMode, intensityMode, steps);
			var settings = Optional.ofNullable(generator).map(g -> g.toSettings(json));
			return new Composition(require(id, "composition id"), 0, request.toSpec(settings), request.toSteps());
		}
	}

	/** @param data the image, Base64 in JSON */
	record StagePlanEntry(String contentType, byte[] data) {
	}

	/**
	 * @param mode             {@code auto}, {@code unicast} or {@code broadcast}
	 * @param networkInterface interface name, {@code null} = all networks (and in files before iteration N)
	 */
	record UniverseEntry(Long id, Integer number, String name, ArtNetDto artnet, String mode, List<String> ipAddresses,
			String networkInterface) {

		static UniverseEntry from(Universe universe) {
			var response = UniverseResponse.from(universe);
			return new UniverseEntry(universe.id(), response.number(), response.name(), response.artnet(),
					response.mode(), response.ipAddresses(), response.networkInterface());
		}

		Universe toUniverse() {
			return new Universe(require(id, "universe id"), 0,
					new UniverseRequest(number, name, artnet, mode, ipAddresses, networkInterface).toSpec());
		}
	}

	/** @param stageShape missing in files before stage shapes = {@code auto} */
	record FixtureEntry(Long id, String name, String shortName, String fixtureTypeId, String mode, Long universeId,
			Integer address, PositionDto position, Integer sortOrder, StageShapeDto stageShape) {

		static FixtureEntry from(PatchedFixture fixture) {
			var spec = fixture.spec();
			return new FixtureEntry(fixture.id(), spec.name(), spec.shortName().orElse(null), spec.fixtureTypeId(),
					spec.modeName(), spec.universeId(), spec.address(),
					spec.position().map(p -> new PositionDto(p.x(), p.y())).orElse(null), spec.sortOrder(),
					StageShapeDto.from(spec.stageShape()));
		}

		PatchedFixture toFixture() {
			return new PatchedFixture(require(id, "fixture id"), 0, new FixtureSpec(name, Optional.ofNullable(shortName),
					fixtureTypeId, mode, Objects.requireNonNullElse(universeId, 0L), Objects.requireNonNullElse(address, 0),
					Optional.ofNullable(position).map(p -> new Position(p.x(), p.y())),
					Objects.requireNonNullElse(sortOrder, 0), StageShapeDto.toShape(stageShape)));
		}
	}

	/** @param id referred to by compositions; may be missing in files without compositions */
	record GroupEntry(Long id, String name, String color, Integer sortOrder, List<Long> fixtureIds) {

		static GroupEntry from(Group group) {
			var spec = group.spec();
			return new GroupEntry(group.id(), spec.name(), spec.color().orElse(null), spec.sortOrder(),
					spec.fixtureIds());
		}

		Group toGroup() {
			return new Group(Objects.requireNonNullElse(id, 0L), 0, new GroupSpec(name, Optional.ofNullable(color), Objects.requireNonNullElse(sortOrder, 0),
					fixtureIds == null ? List.of() : fixtureIds));
		}
	}

	/**
	 * @param outsideCueList files of version 2: GO skipped the scene, it was played by a fader
	 * @param fadeInMs       own fade times, {@code null} = the project's
	 * @param fadeCurve      own fade curve, {@code null} = the project's
	 * @param script         text spoken on stage
	 * @param compositions   compositions of the scene in their order, referring to composition ids of the file
	 * @param keys           extra keys of the scene
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	record SceneEntry(String number, String name, String cueNote, Long autoFollowMs, Boolean outsideCueList,
			Long fadeInMs, Long fadeOutMs, Long delayMs, String fadeCurve, String script,
			List<SceneCompositionEntry> compositions, List<SceneKeyEntry> keys) {

		static SceneEntry from(Scene scene) {
			var spec = scene.spec();
			var timing = spec.timing();
			return new SceneEntry(spec.number().toString(), spec.name(), spec.cueNote().orElse(null),
					spec.autoFollow().map(Duration::toMillis).orElse(null), null,
					TimingJson.fadeInMs(timing), TimingJson.fadeOutMs(timing), timing.delay().toMillis(),
					TimingJson.fadeCurve(timing), spec.script(),
					scene.compositions().stream().map(SceneCompositionEntry::from).toList(),
					scene.keys().stream().map(SceneKeyEntry::from).toList());
		}

		Scene toScene(long id) {
			return new Scene(id, 0, sceneSpec(number, name, cueNote, autoFollowMs, fadeInMs, fadeOutMs, delayMs,
					fadeCurve, script),
					list(compositions).stream().map(SceneCompositionEntry::toUse).toList(),
					list(keys).stream().map(SceneKeyEntry::toKey).toList());
		}
	}

	record SceneCompositionEntry(Long compositionId, Double level, Double speedFactor, Boolean restart) {

		static SceneCompositionEntry from(SceneComposition use) {
			return new SceneCompositionEntry(use.compositionId(), use.level(), use.speedFactor(), use.restart());
		}

		SceneComposition toUse() {
			return new SceneComposition(require(compositionId, "compositionId of a scene"),
					Objects.requireNonNullElse(level, 1.0), Objects.requireNonNullElse(speedFactor, 1.0),
					Boolean.TRUE.equals(restart));
		}
	}

	/** @param mode toggle or flash */
	record SceneKeyEntry(Integer slot, Long compositionId, String mode, Long fadeMs) {

		static SceneKeyEntry from(SceneKey key) {
			return new SceneKeyEntry(key.slot(), key.compositionId(), key.mode().id(), key.fade().toMillis());
		}

		SceneKey toKey() {
			if (slot == null) {
				throw new DomainValidationException(List.of("A key of a scene has no slot"));
			}
			return new SceneKey(slot, require(compositionId, "compositionId of key " + slot),
					mode == null ? KeyMode.TOGGLE : KeyMode.parse(mode),
					Duration.ofMillis(Objects.requireNonNullElse(fadeMs, 0L)));
		}
	}

	static SceneSpec sceneSpec(String number, String name, String cueNote, Long autoFollowMs, Long fadeInMs,
			Long fadeOutMs, Long delayMs, String fadeCurve, String script) {
		if (number == null) {
			throw new DomainValidationException(List.of("Scene '" + name + "' has no number"));
		}
		return new SceneSpec(SceneNumber.parse(number), name, Optional.ofNullable(cueNote),
				Optional.ofNullable(autoFollowMs).map(Duration::ofMillis), TimingJson.fromJson(fadeInMs, fadeOutMs, delayMs, fadeCurve), Objects.requireNonNullElse(script, ""));
	}

	/** Values per fixture id from JSON. */
	static Map<Long, FixtureValues> fixtureValues(Map<Long, Map<String, Double>> json) {
		var result = new HashMap<Long, FixtureValues>();
		(json == null ? Map.<Long, Map<String, Double>>of() : json)
				.forEach((fixtureId, attributes) -> result.put(fixtureId, ValuesJson.fromJson(attributes)));
		return result;
	}
}
