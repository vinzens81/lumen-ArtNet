package de.vinzens.lumen.adapter.web.project;

import de.vinzens.lumen.domain.DomainValidationException;
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

	// --- Universes ------------------------------------------------------------------------------------------

	record ArtNetDto(int net, int subnet, int universe) {
	}

	/**
	 * Without {@code artnet}, port address {@code number - 1} is used.
	 *
	 * @param mode        {@code auto} (default: nodes found via ArtPoll plus {@code ipAddresses}),
	 *                    {@code unicast} (only {@code ipAddresses}) or {@code broadcast}
	 * @param ipAddresses      manual node addresses
	 * @param networkInterface interface the packets go out on, e.g. {@code en7}; {@code null} = all networks
	 */
	record UniverseRequest(Integer number, String name, ArtNetDto artnet, String mode, List<String> ipAddresses,
			String networkInterface) {

		UniverseSpec toSpec() {
			var n = Objects.requireNonNullElse(number, 0);
			var address = artnet != null
					? new ArtNetAddress(artnet.net(), artnet.subnet(), artnet.universe())
					: ArtNetAddress.fromPortAddress(Math.max(n - 1, 0));
			return new UniverseSpec(n, Optional.ofNullable(name), address, target(),
					Optional.ofNullable(networkInterface));
		}

		private ArtNetTarget target() {
			var ips = Objects.requireNonNullElse(ipAddresses, List.<String>of());
			return switch (Objects.requireNonNullElse(mode, MODE_AUTO)) {
				case MODE_AUTO -> new ArtNetTarget.Unicast(true, ips);
				case MODE_UNICAST -> new ArtNetTarget.Unicast(false, ips);
				case MODE_BROADCAST -> new ArtNetTarget.Broadcast();
				default -> throw new DomainValidationException(List.of(
						"mode must be auto, unicast or broadcast, was '" + mode + "'"));
			};
		}
	}

	static final String MODE_AUTO = "auto";
	static final String MODE_UNICAST = "unicast";
	static final String MODE_BROADCAST = "broadcast";

	record UniverseResponse(long id, int number, String name, String displayName, ArtNetDto artnet,
			int portAddress, String mode, List<String> ipAddresses, String networkInterface) {

		static UniverseResponse from(Universe universe) {
			var spec = universe.spec();
			var artNet = spec.artNet();
			var mode = switch (spec.target()) {
				case ArtNetTarget.Broadcast broadcast -> MODE_BROADCAST;
				case ArtNetTarget.Unicast unicast -> unicast.discovered() ? MODE_AUTO : MODE_UNICAST;
			};
			var ips = spec.target() instanceof ArtNetTarget.Unicast unicast ? unicast.ipAddresses() : List.<String>of();
			return new UniverseResponse(universe.id(), spec.number(), spec.name().orElse(null),
					universe.displayName(), new ArtNetDto(artNet.net(), artNet.subnet(), artNet.universe()),
					artNet.portAddress(), mode, ips, spec.networkInterface().orElse(null));
		}
	}

	// --- Fixtures ---------------------------------------------------------------------------------------------

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
	 * @param channelCount DMX channels occupied according to the library, {@code null} if type or mode is unknown
	 * @param lastAddress  last occupied address, {@code null} if type or mode is unknown
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

	// --- Groups ----------------------------------------------------------------------------------------------

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

	// --- Issues ----------------------------------------------------------------------------------------------

	record PatchIssueResponse(String severity, List<Long> fixtureIds, List<Long> universeIds, String message) {

		static PatchIssueResponse from(PatchIssue issue) {
			return new PatchIssueResponse(issue.severity().name().toLowerCase(Locale.ROOT), issue.fixtureIds(),
					issue.universeIds(), issue.message());
		}
	}
}
