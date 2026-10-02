package de.vinzens.lumen.adapter.web.project;

import de.vinzens.lumen.domain.project.ArtNetAddress;
import de.vinzens.lumen.domain.project.ArtNetTarget;
import de.vinzens.lumen.domain.project.FixtureSpec;
import de.vinzens.lumen.domain.project.Group;
import de.vinzens.lumen.domain.project.GroupSpec;
import de.vinzens.lumen.domain.project.PatchIssue;
import de.vinzens.lumen.domain.project.PatchedFixture;
import de.vinzens.lumen.domain.project.Position;
import de.vinzens.lumen.domain.project.Universe;
import de.vinzens.lumen.domain.project.UniverseSpec;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

final class PatchDtos {

	private PatchDtos() {
	}

	// --- Universen -------------------------------------------------------------------------------------------

	record ArtNetDto(int net, int subnet, int universe) {
	}

	/**
	 * Ohne {@code artnet} wird Port-Adresse {@code number - 1} verwendet, ohne {@code target} Broadcast.
	 */
	record UniverseRequest(Integer number, String name, ArtNetDto artnet, String target) {

		UniverseSpec toSpec() {
			var n = Objects.requireNonNullElse(number, 0);
			var address = artnet != null
					? new ArtNetAddress(artnet.net(), artnet.subnet(), artnet.universe())
					: ArtNetAddress.fromPortAddress(Math.max(n - 1, 0));
			var artNetTarget = target != null ? ArtNetTarget.parse(target) : new ArtNetTarget.Broadcast();
			return new UniverseSpec(n, Optional.ofNullable(name), address, artNetTarget);
		}
	}

	record UniverseResponse(long id, int number, String name, String displayName, ArtNetDto artnet,
			int portAddress, String target) {

		static UniverseResponse from(Universe universe) {
			var spec = universe.spec();
			var artNet = spec.artNet();
			return new UniverseResponse(universe.id(), spec.number(), spec.name().orElse(null),
					universe.displayName(), new ArtNetDto(artNet.net(), artNet.subnet(), artNet.universe()),
					artNet.portAddress(), spec.target().asText());
		}
	}

	// --- Geräte ----------------------------------------------------------------------------------------------

	record PositionDto(double x, double y) {
	}

	record FixtureRequest(String name, String shortName, String fixtureTypeId, String mode, Long universeId,
			Integer address, PositionDto position, Integer sortOrder) {

		FixtureSpec toSpec() {
			return new FixtureSpec(name, Optional.ofNullable(shortName), fixtureTypeId, mode,
					Objects.requireNonNullElse(universeId, 0L),
					Objects.requireNonNullElse(address, 0),
					Optional.ofNullable(position).map(p -> new Position(p.x(), p.y())),
					Objects.requireNonNullElse(sortOrder, 0));
		}
	}

	/**
	 * @param channelCount belegte DMX-Kanäle laut Bibliothek, {@code null} wenn Typ oder Modus unbekannt
	 * @param lastAddress  letzte belegte Adresse, {@code null} wenn Typ oder Modus unbekannt
	 */
	record FixtureResponse(long id, String name, String shortName, String fixtureTypeId, String mode,
			long universeId, int address, Integer channelCount, Integer lastAddress, PositionDto position,
			int sortOrder) {

		static FixtureResponse from(PatchedFixture fixture, Optional<Integer> channelCount) {
			var spec = fixture.spec();
			return new FixtureResponse(fixture.id(), spec.name(), spec.shortName().orElse(null), spec.fixtureTypeId(),
					spec.modeName(), spec.universeId(), spec.address(), channelCount.orElse(null),
					channelCount.map(count -> spec.address() + count - 1).orElse(null),
					spec.position().map(p -> new PositionDto(p.x(), p.y())).orElse(null), spec.sortOrder());
		}
	}

	// --- Gruppen ---------------------------------------------------------------------------------------------

	record GroupRequest(String name, String color, Integer sortOrder, List<Long> fixtureIds) {

		GroupSpec toSpec() {
			return new GroupSpec(name, Optional.ofNullable(color), Objects.requireNonNullElse(sortOrder, 0),
					Objects.requireNonNullElse(fixtureIds, List.of()));
		}
	}

	record GroupResponse(long id, String name, String color, int sortOrder, List<Long> fixtureIds) {

		static GroupResponse from(Group group) {
			var spec = group.spec();
			return new GroupResponse(group.id(), spec.name(), spec.color().orElse(null), spec.sortOrder(),
					spec.fixtureIds());
		}
	}

	// --- Prüfung ---------------------------------------------------------------------------------------------

	record PatchIssueResponse(String severity, List<Long> fixtureIds, List<Long> universeIds, String message) {

		static PatchIssueResponse from(PatchIssue issue) {
			return new PatchIssueResponse(issue.severity().name().toLowerCase(Locale.ROOT), issue.fixtureIds(),
					issue.universeIds(), issue.message());
		}
	}
}
