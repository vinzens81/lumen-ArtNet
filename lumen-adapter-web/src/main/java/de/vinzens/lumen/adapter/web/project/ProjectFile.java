package de.vinzens.lumen.adapter.web.project;

import de.vinzens.lumen.adapter.web.output.ValuesJson;
import de.vinzens.lumen.adapter.web.project.PatchDtos.ArtNetDto;
import de.vinzens.lumen.adapter.web.project.PatchDtos.PositionDto;
import de.vinzens.lumen.adapter.web.project.PatchDtos.UniverseRequest;
import de.vinzens.lumen.adapter.web.project.PatchDtos.UniverseResponse;
import de.vinzens.lumen.adapter.web.project.ProjectDtos.ProjectRequest;
import de.vinzens.lumen.application.project.port.in.TransferProjectUseCase.ProjectExport;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.project.FixtureSpec;
import de.vinzens.lumen.domain.project.Group;
import de.vinzens.lumen.domain.project.GroupSpec;
import de.vinzens.lumen.domain.project.PatchedFixture;
import de.vinzens.lumen.domain.project.Position;
import de.vinzens.lumen.domain.project.Universe;
import de.vinzens.lumen.domain.scene.Scene;
import de.vinzens.lumen.domain.scene.SceneNumber;
import de.vinzens.lumen.domain.scene.SceneSpec;

import java.time.Duration;
import java.time.Instant;
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
		List<SceneEntry> scenes) {

	static final String FORMAT = "lumen-project";
	static final int VERSION = 1;

	static ProjectFile from(ProjectExport export, Instant now) {
		var spec = export.project();
		return new ProjectFile(FORMAT, VERSION, now,
				new ProjectRequest(spec.name(), spec.venue().orElse(null), spec.fadeIn().toMillis(),
						spec.fadeOut().toMillis()),
				export.universes().stream().map(UniverseEntry::from).toList(),
				export.fixtures().stream().map(FixtureEntry::from).toList(),
				export.groups().stream().map(GroupEntry::from).toList(),
				export.scenes().stream().map(SceneEntry::from).toList());
	}

	ProjectExport toExport() {
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
		return new ProjectExport(project.toSpec(),
				list(universes).stream().map(UniverseEntry::toUniverse).toList(),
				list(fixtures).stream().map(FixtureEntry::toFixture).toList(),
				list(groups).stream().map(GroupEntry::toGroup).toList(),
				list(scenes).stream().map(SceneEntry::toScene).toList());
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

	record GroupEntry(String name, String color, Integer sortOrder, List<Long> fixtureIds) {

		static GroupEntry from(Group group) {
			var spec = group.spec();
			return new GroupEntry(spec.name(), spec.color().orElse(null), spec.sortOrder(), spec.fixtureIds());
		}

		Group toGroup() {
			return new Group(0, 0, new GroupSpec(name, Optional.ofNullable(color), Objects.requireNonNullElse(sortOrder, 0),
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

		Scene toScene() {
			if (number == null) {
				throw new DomainValidationException(List.of("Scene '" + name + "' has no number"));
			}
			var spec = new SceneSpec(SceneNumber.parse(number), name, Optional.ofNullable(cueNote),
					Optional.ofNullable(autoFollowMs).map(Duration::ofMillis));
			var fixtureValues = new HashMap<Long, FixtureValues>();
			(values == null ? Map.<Long, Map<String, Double>>of() : values)
					.forEach((fixtureId, attributes) -> fixtureValues.put(fixtureId, ValuesJson.fromJson(attributes)));
			return new Scene(0, 0, spec, fixtureValues);
		}
	}
}
