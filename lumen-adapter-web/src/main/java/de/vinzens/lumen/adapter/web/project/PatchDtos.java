package de.vinzens.lumen.adapter.web.project;

import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.project.ArtNetAddress;
import de.vinzens.lumen.domain.project.ArtNetTarget;
import de.vinzens.lumen.domain.project.FixtureSpec;
import de.vinzens.lumen.domain.project.Group;
import de.vinzens.lumen.domain.project.GroupSpec;
import de.vinzens.lumen.domain.project.OutputProtocol;
import de.vinzens.lumen.domain.project.PatchIssue;
import de.vinzens.lumen.domain.project.PatchedFixture;
import de.vinzens.lumen.domain.project.Position;
import de.vinzens.lumen.domain.project.PositionLimits;
import de.vinzens.lumen.domain.project.SacnOutput;
import de.vinzens.lumen.domain.project.StageShape;
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
	 * @param universe    sACN universe 1–63999, missing = the number in Lumen
	 * @param priority    0–200, missing = 100
	 * @param ipAddresses manual receivers; none = multicast
	 */
	record SacnDto(Integer universe, Integer priority, List<String> ipAddresses) {

		static SacnDto from(SacnOutput sacn) {
			return new SacnDto(sacn.universe(), sacn.priority(), sacn.ipAddresses());
		}

		SacnOutput toOutput(int number) {
			var defaults = SacnOutput.forUniverse(Math.max(number, 1));
			return new SacnOutput(Objects.requireNonNullElse(universe, defaults.universe()),
					Objects.requireNonNullElse(priority, defaults.priority()),
					Objects.requireNonNullElse(ipAddresses, List.of()));
		}
	}

	/**
	 * Without {@code artnet}, port address {@code number - 1} is used.
	 *
	 * @param mode        {@code auto} (default: nodes found via ArtPoll plus {@code ipAddresses}),
	 *                    {@code unicast} (only {@code ipAddresses}) or {@code broadcast}
	 * @param ipAddresses      manual node addresses
	 * @param networkInterface interface the packets go out on, e.g. {@code en7}; {@code null} = all networks
	 * @param protocol         {@code artnet} (default), {@code sacn} or {@code both}
	 * @param sacn             sACN settings, kept with Art-Net too; missing = defaults for the number
	 */
	record UniverseRequest(Integer number, String name, ArtNetDto artnet, String mode, List<String> ipAddresses,
			String networkInterface, String protocol, SacnDto sacn) {

		UniverseSpec toSpec() {
			var n = Objects.requireNonNullElse(number, 0);
			var address = artnet != null
					? new ArtNetAddress(artnet.net(), artnet.subnet(), artnet.universe())
					: ArtNetAddress.fromPortAddress(Math.max(n - 1, 0));
			return new UniverseSpec(n, Optional.ofNullable(name), address, target(),
					Optional.ofNullable(networkInterface),
					protocol == null ? OutputProtocol.ART_NET : OutputProtocol.parse(protocol),
					Objects.requireNonNullElse(sacn, new SacnDto(null, null, null)).toOutput(n));
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
			int portAddress, String mode, List<String> ipAddresses, String networkInterface, String protocol,
			SacnDto sacn) {

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
					artNet.portAddress(), mode, ips, spec.networkInterface().orElse(null), spec.protocol().asText(),
					SacnDto.from(spec.sacn()));
		}
	}

	// --- Fixtures ---------------------------------------------------------------------------------------------

	record PositionDto(double x, double y) {
	}

	/**
	 * @param kind     {@code auto} (default), {@code spot}, {@code bar} or {@code strip}
	 * @param rotation clockwise in degrees, 0 = across the stage
	 * @param length   bar/strip length as a fraction of the stage width
	 */
	record StageShapeDto(String kind, Integer rotation, Double length) {

		StageShape toShape() {
			return new StageShape(kind == null ? StageShape.Kind.AUTO : StageShape.Kind.parse(kind),
					Math.floorMod(Objects.requireNonNullElse(rotation, 0), 360),
					Objects.requireNonNullElse(length, StageShape.DEFAULT_LENGTH));
		}

		static StageShape toShape(StageShapeDto dto) {
			return dto == null ? StageShape.DEFAULT : dto.toShape();
		}

		static StageShapeDto from(StageShape shape) {
			return new StageShapeDto(shape.kind().id(), shape.rotation(), shape.length());
		}
	}

	/** @param stageShape missing = drawn automatically ({@code auto}) */
	/** @param limits allowed range of pan and tilt in the show, {@code null} = no limit */
	record FixtureRequest(String name, String shortName, String fixtureTypeId, String mode, Long universeId,
			Integer address, PositionDto position, Integer sortOrder, StageShapeDto stageShape, LimitsDto limits) {

		FixtureSpec toSpec() {
			return new FixtureSpec(name, Optional.ofNullable(shortName), fixtureTypeId, mode,
					Objects.requireNonNullElse(universeId, 0L),
					Objects.requireNonNullElse(address, 0),
					Optional.ofNullable(position).map(p -> new Position(p.x(), p.y())),
					Objects.requireNonNullElse(sortOrder, 0),
					StageShapeDto.toShape(stageShape),
					Optional.ofNullable(limits).map(LimitsDto::toLimits));
		}
	}

	/** The allowed range of pan and tilt in DMX values (0–255, inclusive); the programmer is not limited. */
	record LimitsDto(int panMin, int panMax, int tiltMin, int tiltMax) {

		PositionLimits toLimits() {
			return new PositionLimits(panMin, panMax, tiltMin, tiltMax);
		}

		static LimitsDto from(PositionLimits limits) {
			return new LimitsDto(limits.panMin(), limits.panMax(), limits.tiltMin(), limits.tiltMax());
		}
	}

	/**
	 * @param channelCount DMX channels occupied according to the library, {@code null} if type or mode is unknown
	 * @param lastAddress  last occupied address, {@code null} if type or mode is unknown
	 */
	record FixtureResponse(long id, String name, String shortName, String fixtureTypeId, String mode,
			long universeId, int address, Integer channelCount, Integer lastAddress, PositionDto position,
			int sortOrder, StageShapeDto stageShape, LimitsDto limits) {

		static FixtureResponse from(PatchedFixture fixture, Optional<Integer> channelCount) {
			var spec = fixture.spec();
			return new FixtureResponse(fixture.id(), spec.name(), spec.shortName().orElse(null), spec.fixtureTypeId(),
					spec.modeName(), spec.universeId(), spec.address(), channelCount.orElse(null),
					channelCount.map(count -> spec.address() + count - 1).orElse(null),
					spec.position().map(p -> new PositionDto(p.x(), p.y())).orElse(null), spec.sortOrder(),
					StageShapeDto.from(spec.stageShape()), spec.limits().map(LimitsDto::from).orElse(null));
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
