package de.vinzens.lumen.adapter.web.project;

import de.vinzens.lumen.adapter.web.composition.CompositionDtos.CompositionRequest;
import de.vinzens.lumen.adapter.web.composition.CompositionDtos.CompositionResponse;
import de.vinzens.lumen.adapter.web.composition.CompositionDtos.StepDto;
import de.vinzens.lumen.adapter.web.composition.CompositionDtos.TargetDto;
import de.vinzens.lumen.adapter.web.output.ValuesJson;
import de.vinzens.lumen.adapter.web.scene.TimingJson;
import de.vinzens.lumen.adapter.web.project.PatchDtos.ArtNetDto;
import de.vinzens.lumen.adapter.web.project.PatchDtos.PositionDto;
import de.vinzens.lumen.adapter.web.project.PatchDtos.UniverseRequest;
import de.vinzens.lumen.adapter.web.project.PatchDtos.UniverseResponse;
import de.vinzens.lumen.adapter.web.project.ProjectDtos.ProjectRequest;
import de.vinzens.lumen.application.project.port.in.TransferProjectUseCase.ProjectExport;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.composition.Composition;
import de.vinzens.lumen.domain.composition.ExtraKey;
import de.vinzens.lumen.domain.composition.KeyMode;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.party.PartyButton;
import de.vinzens.lumen.domain.project.FixtureSpec;
import de.vinzens.lumen.domain.project.Group;
import de.vinzens.lumen.domain.project.GroupSpec;
import de.vinzens.lumen.domain.project.PatchedFixture;
import de.vinzens.lumen.domain.project.Position;
import de.vinzens.lumen.domain.project.StagePlan;
import de.vinzens.lumen.domain.project.Universe;
import de.vinzens.lumen.domain.scene.Scene;
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
 * File format of an exported project (JSON). Ids only connect the parts of the file: fixtures refer to
 * universes, groups and scene values to fixtures. On import everything gets new ids.
 *
 * @param format  always {@value #FORMAT}
 * @param version format version, currently {@value #VERSION}
 */
record ProjectFile(String format, Integer version, Instant exportedAt, ProjectRequest project,
		List<UniverseEntry> universes, List<FixtureEntry> fixtures, List<GroupEntry> groups,
		List<SceneEntry> scenes, StagePlanEntry stagePlan, List<CompositionEntry> compositions,
		List<ExtraKeyEntry> extraKeys, List<PartyButtonEntry> partyButtons) {

	static final String FORMAT = "lumen-project";
	static final int VERSION = 1;

	static ProjectFile from(ProjectExport export, Instant now, JsonMapper json) {
		var spec = export.project();
		var sceneNumbers = new HashMap<Long, String>();
		export.scenes().forEach(scene -> sceneNumbers.put(scene.id(), scene.spec().number().toString()));
		return new ProjectFile(FORMAT, VERSION, now,
				new ProjectRequest(spec.name(), spec.venue().orElse(null), spec.fadeIn().toMillis(),
						spec.fadeOut().toMillis()),
				export.universes().stream().map(UniverseEntry::from).toList(),
				export.fixtures().stream().map(FixtureEntry::from).toList(),
				export.groups().stream().map(GroupEntry::from).toList(),
				export.scenes().stream()
						.map(scene -> SceneEntry.from(scene, export.sceneKeys().getOrDefault(scene.id(), Set.of())))
						.toList(),
				export.stagePlan().map(plan -> new StagePlanEntry(plan.contentType(), plan.data())).orElse(null),
				export.compositions().stream().map(c -> CompositionEntry.from(c, sceneNumbers, json)).toList(),
				export.keys().stream().map(k -> new ExtraKeyEntry(k.slot(), k.mode().id(), k.fade().toMillis(),
						k.composition().map(c -> CompositionEntry.from(c, sceneNumbers, json)).orElse(null),
						k.sceneId().map(sceneNumbers::get).orElse(null))).toList(),
				export.partyButtons().stream().map(PartyButtonEntry::from).toList());
	}

	ProjectExport toExport(JsonMapper json) {
		if (!FORMAT.equals(format)) {
			throw new DomainValidationException(List.of("This is not a Lumen project file (format '" + format + "')"));
		}
		if (version == null || version < 1 || version > VERSION) {
			throw new DomainValidationException(List.of("Project file version " + version
					+ " is not supported (supported: up to " + VERSION + ")"));
		}
		if (project == null) {
			throw new DomainValidationException(List.of("project is missing"));
		}
		// scenes have no id in the file: compositions refer to them by number
		var sceneList = list(scenes);
		var sceneIds = new HashMap<String, Long>();
		var exportScenes = new ArrayList<Scene>();
		var sceneKeys = new HashMap<Long, Set<Integer>>();
		for (int i = 0; i < sceneList.size(); i++) {
			var scene = sceneList.get(i).toScene(i + 1);
			sceneIds.put(scene.spec().number().toString(), scene.id());
			exportScenes.add(scene);
			var keys = sceneList.get(i).extraKeys();
			if (keys != null && !keys.isEmpty()) {
				sceneKeys.put(scene.id(), Set.copyOf(keys));
			}
		}
		return new ProjectExport(project.toSpec(),
				list(universes).stream().map(UniverseEntry::toUniverse).toList(),
				list(fixtures).stream().map(FixtureEntry::toFixture).toList(),
				list(groups).stream().map(GroupEntry::toGroup).toList(),
				exportScenes,
				Optional.ofNullable(stagePlan).map(plan -> new StagePlan(plan.contentType(),
						plan.data() == null ? new byte[0] : plan.data())),
				list(compositions).stream().map(c -> c.toComposition(sceneIds, json)).toList(),
				list(extraKeys).stream().map(k -> k.toKey(sceneIds, json)).toList(), sceneKeys,
				list(partyButtons).stream().map(PartyButtonEntry::toButton).toList());
	}

	/**
	 * A party button with its values per fixture id; files from before party mode have none.
	 *
	 * @param mode toggle or flash
	 */
	record PartyButtonEntry(String name, String mode, Map<Long, Map<String, Double>> values) {

		static PartyButtonEntry from(PartyButton button) {
			return new PartyButtonEntry(button.name(), button.mode().id(), ValuesJson.toJson(button.values()));
		}

		PartyButton toButton() {
			var keyMode = Arrays.stream(KeyMode.values()).filter(m -> m.id().equals(mode)).findFirst()
					.orElseThrow(() -> new DomainValidationException(List.of("Party button '" + name
							+ "' has unknown mode '" + mode + "'")));
			var fixtureValues = new HashMap<Long, FixtureValues>();
			(values == null ? Map.<Long, Map<String, Double>>of() : values)
					.forEach((fixtureId, attributes) -> fixtureValues.put(fixtureId, ValuesJson.fromJson(attributes)));
			return new PartyButton(0, 0, 0, name, keyMode, fixtureValues);
		}
	}

	private static long require(Long id, String what) {
		if (id == null) {
			throw new DomainValidationException(List.of(what + " is missing"));
		}
		return id;
	}

	private static <T> List<T> list(List<T> entries) {
		return entries == null ? List.of() : entries.stream().filter(Objects::nonNull).toList();
	}

	/**
	 * A composition in the library ({@code sceneNumber} null) or a copy in a scene; the other fields are the same as
	 * in the composition API.
	 *
	 * @param sourceId id of the library composition a copy was made from
	 */
	record CompositionEntry(Long id, String sceneNumber, Long sourceId, String name, String kind, JsonNode params,
			TargetDto target, List<StepDto> steps, Double level, Double speedFactor, Boolean restart, Boolean once) {

		static CompositionEntry from(Composition composition, Map<Long, String> sceneNumbers, JsonMapper json) {
			var api = CompositionResponse.from(composition);
			return new CompositionEntry(api.id(), composition.sceneId().map(sceneNumbers::get).orElse(null),
					api.sourceId(), api.name(), api.kind(), json.valueToTree(api.params()), api.target(), api.steps(),
					api.level(), api.speedFactor(), api.restart(), api.once());
		}

		Composition toComposition(Map<String, Long> sceneIds, JsonMapper json) {
			var spec = new CompositionRequest(name, kind, params, target, steps, level, speedFactor, restart, once)
					.toSpec(json);
			Optional<Long> sceneId = Optional.empty();
			if (sceneNumber != null) {
				var number = SceneNumber.parse(sceneNumber).toString();
				sceneId = Optional.of(Optional.ofNullable(sceneIds.get(number)).orElseThrow(() ->
						new DomainValidationException(List.of("Composition '" + name + "' refers to unknown scene "
								+ sceneNumber))));
			}
			return new Composition(Objects.requireNonNullElse(id, 0L), 0, sceneId, Optional.ofNullable(sourceId), 0,
					spec);
		}
	}

	/**
	 * An extra key of show mode (mode toggle or flash) with its own composition or a scene.
	 *
	 * @param sceneNumber the scene the key plays, instead of a composition
	 */
	record ExtraKeyEntry(Integer slot, String mode, Long fadeMs, CompositionEntry composition, String sceneNumber) {

		ExtraKey toKey(Map<String, Long> sceneIds, JsonMapper json) {
			if (slot == null || (composition == null) == (sceneNumber == null)) {
				throw new DomainValidationException(List.of("An extra key needs a slot and either a composition or "
						+ "a sceneNumber"));
			}
			var mode = this.mode == null ? KeyMode.TOGGLE : switch (this.mode) {
				case "toggle" -> KeyMode.TOGGLE;
				case "flash" -> KeyMode.FLASH;
				default -> throw new DomainValidationException(List.of("Extra key mode must be toggle or flash"));
			};
			var fade = Duration.ofMillis(Objects.requireNonNullElse(fadeMs, 0L));
			if (composition != null) {
				return new ExtraKey(slot, mode, fade, composition.toComposition(sceneIds, json));
			}
			var sceneId = sceneIds.get(SceneNumber.parse(sceneNumber).toString());
			if (sceneId == null) {
				throw new DomainValidationException(List.of("Extra key " + slot + " refers to unknown scene "
						+ sceneNumber));
			}
			return new ExtraKey(slot, mode, fade, new ExtraKey.Content.PlaysScene(sceneId));
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

	record FixtureEntry(Long id, String name, String shortName, String fixtureTypeId, String mode, Long universeId,
			Integer address, PositionDto position, Integer sortOrder) {

		static FixtureEntry from(PatchedFixture fixture) {
			var spec = fixture.spec();
			return new FixtureEntry(fixture.id(), spec.name(), spec.shortName().orElse(null), spec.fixtureTypeId(),
					spec.modeName(), spec.universeId(), spec.address(),
					spec.position().map(p -> new PositionDto(p.x(), p.y())).orElse(null), spec.sortOrder());
		}

		PatchedFixture toFixture() {
			return new PatchedFixture(require(id, "fixture id"), 0, new FixtureSpec(name, Optional.ofNullable(shortName),
					fixtureTypeId, mode, Objects.requireNonNullElse(universeId, 0L), Objects.requireNonNullElse(address, 0),
					Optional.ofNullable(position).map(p -> new Position(p.x(), p.y())),
					Objects.requireNonNullElse(sortOrder, 0)));
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
	 * @param keyOnly   only played by extra keys
	 * @param fadeInMs  own fade times, {@code null} = the project's (files before iteration 10 have none)
	 * @param script    text spoken on stage, {@code null} in files before iteration T
	 * @param values    normalized values (0–1) per fixture id and attribute
	 * @param extraKeys extra keys usable in this scene
	 */
	record SceneEntry(String number, String name, String cueNote, Long autoFollowMs, Boolean keyOnly, Long fadeInMs,
			Long fadeOutMs, Long delayMs, String script, Map<Long, Map<String, Double>> values,
			List<Integer> extraKeys) {

		static SceneEntry from(Scene scene, Set<Integer> keys) {
			var spec = scene.spec();
			var timing = spec.timing();
			return new SceneEntry(spec.number().toString(), spec.name(), spec.cueNote().orElse(null),
					spec.autoFollow().map(Duration::toMillis).orElse(null), spec.keyOnly(),
					TimingJson.fadeInMs(timing), TimingJson.fadeOutMs(timing), timing.delay().toMillis(),
					spec.script(), ValuesJson.toJson(scene.values()),
					keys.stream().sorted().toList());
		}

		Scene toScene(long id) {
			if (number == null) {
				throw new DomainValidationException(List.of("Scene '" + name + "' has no number"));
			}
			var spec = new SceneSpec(SceneNumber.parse(number), name, Optional.ofNullable(cueNote),
					Optional.ofNullable(autoFollowMs).map(Duration::ofMillis), Boolean.TRUE.equals(keyOnly),
					TimingJson.fromJson(fadeInMs, fadeOutMs, delayMs), Objects.requireNonNullElse(script, ""));
			var fixtureValues = new HashMap<Long, FixtureValues>();
			(values == null ? Map.<Long, Map<String, Double>>of() : values)
					.forEach((fixtureId, attributes) -> fixtureValues.put(fixtureId, ValuesJson.fromJson(attributes)));
			return new Scene(id, 0, spec, fixtureValues);
		}
	}
}
