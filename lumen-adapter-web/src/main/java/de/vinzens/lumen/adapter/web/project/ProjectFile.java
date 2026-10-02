package de.vinzens.lumen.adapter.web.project;

import de.vinzens.lumen.adapter.web.composition.CompositionDtos.CompositionRequest;
import de.vinzens.lumen.adapter.web.composition.CompositionDtos.CompositionResponse;
import de.vinzens.lumen.adapter.web.composition.CompositionDtos.StepDto;
import de.vinzens.lumen.adapter.web.composition.CompositionDtos.TargetDto;
import de.vinzens.lumen.adapter.web.output.ValuesJson;
import de.vinzens.lumen.adapter.web.project.PatchDtos.ArtNetDto;
import de.vinzens.lumen.adapter.web.project.PatchDtos.PositionDto;
import de.vinzens.lumen.adapter.web.project.PatchDtos.UniverseRequest;
import de.vinzens.lumen.adapter.web.project.PatchDtos.UniverseResponse;
import de.vinzens.lumen.adapter.web.project.ProjectDtos.ProjectRequest;
import de.vinzens.lumen.application.project.port.in.TransferProjectUseCase.ProjectExport;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.composition.Composition;
import de.vinzens.lumen.domain.output.FixtureValues;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * File format of an exported project (JSON). Ids only connect the parts of the file: fixtures refer to
 * universes, groups and scene values to fixtures. On import everything gets new ids.
 *
 * @param format  always {@value #FORMAT}
 * @param version format version, currently {@value #VERSION}
 */
record ProjectFile(String format, Integer version, Instant exportedAt, ProjectRequest project,
		List<UniverseEntry> universes, List<FixtureEntry> fixtures, List<GroupEntry> groups,
		List<SceneEntry> scenes, StagePlanEntry stagePlan, List<CompositionEntry> compositions) {

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
				export.scenes().stream().map(SceneEntry::from).toList(),
				export.stagePlan().map(plan -> new StagePlanEntry(plan.contentType(), plan.data())).orElse(null),
				export.compositions().stream().map(c -> CompositionEntry.from(c, sceneNumbers, json)).toList());
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
		for (int i = 0; i < sceneList.size(); i++) {
			var scene = sceneList.get(i).toScene(i + 1);
			sceneIds.put(scene.spec().number().toString(), scene.id());
			exportScenes.add(scene);
		}
		return new ProjectExport(project.toSpec(),
				list(universes).stream().map(UniverseEntry::toUniverse).toList(),
				list(fixtures).stream().map(FixtureEntry::toFixture).toList(),
				list(groups).stream().map(GroupEntry::toGroup).toList(),
				exportScenes,
				Optional.ofNullable(stagePlan).map(plan -> new StagePlan(plan.contentType(),
						plan.data() == null ? new byte[0] : plan.data())),
				list(compositions).stream().map(c -> c.toComposition(sceneIds, json)).toList());
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
			TargetDto target, List<StepDto> steps, Double level, Double speedFactor, Boolean restart) {

		static CompositionEntry from(Composition composition, Map<Long, String> sceneNumbers, JsonMapper json) {
			var api = CompositionResponse.from(composition);
			return new CompositionEntry(api.id(), composition.sceneId().map(sceneNumbers::get).orElse(null),
					api.sourceId(), api.name(), api.kind(), json.valueToTree(api.params()), api.target(), api.steps(),
					api.level(), api.speedFactor(), api.restart());
		}

		Composition toComposition(Map<String, Long> sceneIds, JsonMapper json) {
			var spec = new CompositionRequest(name, kind, params, target, steps, level, speedFactor, restart)
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

	/** @param data the image, Base64 in JSON */
	record StagePlanEntry(String contentType, byte[] data) {
	}

	/** @param mode {@code auto}, {@code unicast} or {@code broadcast} */
	record UniverseEntry(Long id, Integer number, String name, ArtNetDto artnet, String mode, List<String> ipAddresses) {

		static UniverseEntry from(Universe universe) {
			var response = UniverseResponse.from(universe);
			return new UniverseEntry(universe.id(), response.number(), response.name(), response.artnet(),
					response.mode(), response.ipAddresses());
		}

		Universe toUniverse() {
			return new Universe(require(id, "universe id"), 0,
					new UniverseRequest(number, name, artnet, mode, ipAddresses).toSpec());
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

	/** @param values normalized values (0–1) per fixture id and attribute */
	record SceneEntry(String number, String name, String cueNote, Long autoFollowMs,
			Map<Long, Map<String, Double>> values) {

		static SceneEntry from(Scene scene) {
			var spec = scene.spec();
			return new SceneEntry(spec.number().toString(), spec.name(), spec.cueNote().orElse(null),
					spec.autoFollow().map(Duration::toMillis).orElse(null), ValuesJson.toJson(scene.values()));
		}

		Scene toScene(long id) {
			if (number == null) {
				throw new DomainValidationException(List.of("Scene '" + name + "' has no number"));
			}
			var spec = new SceneSpec(SceneNumber.parse(number), name, Optional.ofNullable(cueNote),
					Optional.ofNullable(autoFollowMs).map(Duration::ofMillis));
			var fixtureValues = new HashMap<Long, FixtureValues>();
			(values == null ? Map.<Long, Map<String, Double>>of() : values)
					.forEach((fixtureId, attributes) -> fixtureValues.put(fixtureId, ValuesJson.fromJson(attributes)));
			return new Scene(id, 0, spec, fixtureValues);
		}
	}
}
