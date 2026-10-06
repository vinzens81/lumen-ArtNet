package de.vinzens.lumen.adapter.web.party;

import de.vinzens.lumen.application.party.port.in.ControlPartyUseCase.PartyStatus;
import de.vinzens.lumen.domain.composition.KeyMode;
import de.vinzens.lumen.domain.party.PartyCell;
import de.vinzens.lumen.domain.party.PartyMatrix;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** JSON form of party mode. Rows and columns count from 0. */
public final class PartyDtos {

	private PartyDtos() {
	}

	/** @param on the fields that are on */
	public record PartyResponse(List<PositionDto> on) {

		public static PartyResponse from(PartyStatus status) {
			return new PartyResponse(status.on().stream()
					.sorted(Comparator.comparingInt(PartyCell.Position::row).thenComparingInt(PartyCell.Position::column))
					.map(position -> new PositionDto(position.row(), position.column()))
					.toList());
		}
	}

	public record PositionDto(int row, int column) {
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
	 */
	public record PartyCellDto(int row, int column, String label, Long sceneId, Long compositionId, String color,
			String borderColor, String mode, String group) {

		PartyCell toCell() {
			return new PartyCell(row, column, Optional.ofNullable(label), Optional.ofNullable(sceneId),
					Optional.ofNullable(compositionId), Objects.requireNonNullElse(color, PartyCell.DEFAULT_COLOR),
					Objects.requireNonNullElse(borderColor, PartyCell.DEFAULT_BORDER),
					mode == null ? KeyMode.TOGGLE : KeyMode.parse(mode), Optional.ofNullable(group));
		}

		static PartyCellDto from(PartyCell cell) {
			return new PartyCellDto(cell.row(), cell.column(), cell.label().orElse(null), cell.sceneId().orElse(null),
					cell.compositionId().orElse(null), cell.color(), cell.borderColor(), cell.mode().id(),
					cell.group().orElse(null));
		}
	}
}
