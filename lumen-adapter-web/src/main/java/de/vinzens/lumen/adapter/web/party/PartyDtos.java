package de.vinzens.lumen.adapter.web.party;

import de.vinzens.lumen.application.party.port.in.ControlPartyUseCase.PartyStatus;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.composition.KeyMode;
import de.vinzens.lumen.domain.party.PartyCell;
import de.vinzens.lumen.domain.party.PartyFader;
import de.vinzens.lumen.domain.party.PartyMatrix;
import de.vinzens.lumen.domain.party.PartyPage;
import de.vinzens.lumen.domain.party.PartyPlace;
import de.vinzens.lumen.domain.party.PartyPoint;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** JSON form of party mode. Pages, rows and columns count from 0. */
public final class PartyDtos {

	private PartyDtos() {
	}

	/**
	 * @param on     the fields that are on
	 * @param points the point last touched on each gradient and pad field, also of those that are off
	 */
	public record PartyResponse(List<PlaceDto> on, List<FaderPointDto> points) {

		private static final Comparator<PartyPlace> ORDER = Comparator.comparingInt(PartyPlace::page)
				.thenComparingInt(PartyPlace::row).thenComparingInt(PartyPlace::column);

		public static PartyResponse from(PartyStatus status) {
			return new PartyResponse(status.on().stream()
					.sorted(ORDER)
					.map(place -> new PlaceDto(place.page(), place.row(), place.column()))
					.toList(),
					status.faderPoints().entrySet().stream()
							.sorted(Map.Entry.comparingByKey(ORDER))
							.map(entry -> new FaderPointDto(entry.getKey().page(), entry.getKey().row(),
									entry.getKey().column(), entry.getValue().x(), entry.getValue().y()))
							.toList());
		}
	}

	public record PlaceDto(int page, int row, int column) {
	}

	/** A point on a gradient ({@code x} only) or a pad: 0–1, x from the left, y from the bottom. */
	public record FaderPointDto(int page, int row, int column, double x, double y) {
	}

	/**
	 * @param x 0 = left end of the gradient or pad, 1 = right end
	 * @param y 0 = bottom of a pad, 1 = top; {@code null} = the middle (a gradient)
	 */
	public record SlideRequest(Double x, Double y) {

		public PartyPoint toPoint() {
			if (x == null) {
				throw new DomainValidationException(List.of("x is required"));
			}
			return new PartyPoint(x, Objects.requireNonNullElse(y, 0.5));
		}
	}

	/**
	 * A gradient or pad on a field.
	 *
	 * @param kind       {@code dimmer}, {@code color} or {@code pan-tilt}
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

	/** @param pages the pages, shown as tabs, at least one */
	public record PartyMatrixDto(List<PartyPageDto> pages) {

		public PartyMatrix toMatrix() {
			return new PartyMatrix((pages == null ? List.<PartyPageDto>of() : pages).stream()
					.map(PartyPageDto::toPage).toList());
		}

		public static PartyMatrixDto from(PartyMatrix matrix) {
			return new PartyMatrixDto(matrix.pages().stream().map(PartyPageDto::from).toList());
		}
	}

	/**
	 * @param name       shown on the tab, {@code null} = "Party"
	 * @param separators rows a line is drawn above
	 * @param cells      the fields that are not empty
	 */
	public record PartyPageDto(String name, Integer rows, Integer columns, List<Integer> separators,
			List<PartyCellDto> cells) {

		PartyPage toPage() {
			return new PartyPage(Objects.requireNonNullElse(name, PartyPage.DEFAULT_NAME),
					Objects.requireNonNullElse(rows, 4), Objects.requireNonNullElse(columns, 4),
					Set.copyOf(separators == null ? List.of() : separators),
					(cells == null ? List.<PartyCellDto>of() : cells).stream().map(PartyCellDto::toCell).toList());
		}

		static PartyPageDto from(PartyPage page) {
			return new PartyPageDto(page.name(), page.rows(), page.columns(),
					page.separators().stream().sorted().toList(), page.cells().stream().map(PartyCellDto::from).toList());
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
	 * @param rowSpan     rows the field covers, {@code null} = 1
	 * @param fader       the gradient or pad it shows instead of playing something, {@code null} = none
	 */
	public record PartyCellDto(int row, int column, String label, Long sceneId, Long compositionId, Long moveId,
			String color, String borderColor, String mode, String group, Integer span, Integer rowSpan,
			FaderDto fader) {

		PartyCell toCell() {
			return new PartyCell(row, column, Optional.ofNullable(label), Optional.ofNullable(sceneId),
					Optional.ofNullable(compositionId), Optional.ofNullable(moveId),
					Objects.requireNonNullElse(color, PartyCell.DEFAULT_COLOR),
					Objects.requireNonNullElse(borderColor, PartyCell.DEFAULT_BORDER),
					mode == null ? KeyMode.TOGGLE : KeyMode.parse(mode), Optional.ofNullable(group),
					Objects.requireNonNullElse(span, 1), Objects.requireNonNullElse(rowSpan, 1),
					Optional.ofNullable(fader).map(FaderDto::toFader));
		}

		static PartyCellDto from(PartyCell cell) {
			return new PartyCellDto(cell.row(), cell.column(), cell.label().orElse(null), cell.sceneId().orElse(null),
					cell.compositionId().orElse(null), cell.moveId().orElse(null), cell.color(), cell.borderColor(),
					cell.mode().id(), cell.group().orElse(null), cell.span(), cell.rowSpan(),
					cell.fader().map(FaderDto::from).orElse(null));
		}
	}
}
