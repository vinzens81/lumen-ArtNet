package de.vinzens.lumen.adapter.web.party;

import de.vinzens.lumen.application.party.port.in.ControlPartyUseCase.PartyStatus;
import de.vinzens.lumen.domain.composition.KeyMode;
import de.vinzens.lumen.domain.party.PartyCell;
import de.vinzens.lumen.domain.party.PartyFader;
import de.vinzens.lumen.domain.party.PartyMatrix;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** JSON form of party mode. Rows and columns count from 0. */
public final class PartyDtos {

	private PartyDtos() {
	}

	/**
	 * @param on     the fields that are on
	 * @param points the point last touched on each gradient field (0–1), also of those that are off
	 */
	public record PartyResponse(List<PositionDto> on, List<FaderPointDto> points) {

		private static final Comparator<PartyCell.Position> ORDER = Comparator.comparingInt(PartyCell.Position::row)
				.thenComparingInt(PartyCell.Position::column);

		public static PartyResponse from(PartyStatus status) {
			return new PartyResponse(status.on().stream()
					.sorted(ORDER)
					.map(position -> new PositionDto(position.row(), position.column()))
					.toList(),
					status.faderPoints().entrySet().stream()
							.sorted(Map.Entry.comparingByKey(ORDER))
							.map(entry -> new FaderPointDto(entry.getKey().row(), entry.getKey().column(),
									entry.getValue()))
							.toList());
		}
	}

	public record PositionDto(int row, int column) {
	}

	public record FaderPointDto(int row, int column, double point) {
	}

	/** @param point 0 = left end of the gradient, 1 = right end */
	public record SlideRequest(Double point) {
	}

	/**
	 * A gradient on a field.
	 *
	 * @param kind       {@code dimmer} or {@code color}
	 * @param fixtureIds the fixtures it sets
	 */
	public record FaderDto(String kind, List<Long> fixtureIds) {

		PartyFader toFader() {
			return new PartyFader(PartyFader.Kind.parse(kind), fixtureIds == null ? List.of() : fixtureIds);
		}

		static FaderDto from(PartyFader fader) {
			return new FaderDto(fader.kind().id(), fader.fixtureIds());
		}
	}

	/**
	 * @param separators rows a line is drawn above
	 * @param cells      the fields that are not empty
	 */
	public record PartyMatrixDto(Integer rows, Integer columns, List<Integer> separators, List<PartyCellDto> cells) {

		public PartyMatrix toMatrix() {
			return new PartyMatrix(Objects.requireNonNullElse(rows, 4), Objects.requireNonNullElse(columns, 4),
					Set.copyOf(separators == null ? List.of() : separators),
					(cells == null ? List.<PartyCellDto>of() : cells).stream().map(PartyCellDto::toCell).toList());
		}

		public static PartyMatrixDto from(PartyMatrix matrix) {
			return new PartyMatrixDto(matrix.rows(), matrix.columns(), matrix.separators().stream().sorted().toList(),
					matrix.cells().stream().map(PartyCellDto::from).toList());
		}
	}

	/**
	 * A field; without scene and composition it only shows its label.
	 *
	 * @param label       shown on the field, {@code null} = the name of what it plays
	 * @param color       fill color {@code #rrggbb}, {@code null} = the default
	 * @param borderColor border color {@code #rrggbb}, {@code null} = the default
	 * @param mode        toggle or flash, {@code null} = toggle
	 * @param group       fields of the same group exclude each other, {@code null} = no group
	 * @param span        columns the field covers, {@code null} = 1
	 * @param fader       the gradient it shows instead of playing something, {@code null} = none
	 */
	public record PartyCellDto(int row, int column, String label, Long sceneId, Long compositionId, Long moveId,
			String color, String borderColor, String mode, String group, Integer span, FaderDto fader) {

		PartyCell toCell() {
			return new PartyCell(row, column, Optional.ofNullable(label), Optional.ofNullable(sceneId),
					Optional.ofNullable(compositionId), Optional.ofNullable(moveId),
					Objects.requireNonNullElse(color, PartyCell.DEFAULT_COLOR),
					Objects.requireNonNullElse(borderColor, PartyCell.DEFAULT_BORDER),
					mode == null ? KeyMode.TOGGLE : KeyMode.parse(mode), Optional.ofNullable(group),
					Objects.requireNonNullElse(span, 1), Optional.ofNullable(fader).map(FaderDto::toFader));
		}

		static PartyCellDto from(PartyCell cell) {
			return new PartyCellDto(cell.row(), cell.column(), cell.label().orElse(null), cell.sceneId().orElse(null),
					cell.compositionId().orElse(null), cell.moveId().orElse(null), cell.color(), cell.borderColor(),
					cell.mode().id(), cell.group().orElse(null), cell.span(),
					cell.fader().map(FaderDto::from).orElse(null));
		}
	}
}
