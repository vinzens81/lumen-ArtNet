package de.vinzens.lumen.adapter.web.party;

import de.vinzens.lumen.adapter.web.output.ValuesJson;
import de.vinzens.lumen.application.party.port.in.ControlPartyUseCase.PartyStatus;
import de.vinzens.lumen.domain.party.Palette;
import de.vinzens.lumen.domain.party.PartyButton;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** JSON form of party mode. */
public final class PartyDtos {

	private PartyDtos() {
	}

	/**
	 * @param msSinceBeat    time since the last beat when the status was made; clients run the beat indicator from
	 *                       there with {@code beatMs}
	 * @param fixtures       palette id (or null) and intensity per fixture id that party mode has touched
	 * @param palettes       the built-in palettes in their order
	 */
	public record PartyResponse(double bpm, long beatMs, long msSinceBeat, Map<Long, FixtureState> fixtures,
			List<ButtonState> buttons, List<PaletteResponse> palettes) {

		public static PartyResponse from(PartyStatus status, Instant now) {
			var fixtures = new TreeMap<Long, FixtureState>();
			status.fixtures().forEach((id, state) -> fixtures.put(id,
					new FixtureState(state.palette().map(Palette::id).orElse(null), state.intensity())));
			return new PartyResponse(Math.round(status.bpm() * 10) / 10.0, status.beatLength().toMillis(),
					Math.max(0, Duration.between(status.lastBeat(), now).toMillis()), fixtures,
					status.buttons().stream()
							.map(b -> new ButtonState(b.id(), b.name(), b.mode().id(), b.on()))
							.toList(),
					Arrays.stream(Palette.values()).map(PaletteResponse::from).toList());
		}
	}

	public record FixtureState(String palette, double intensity) {
	}

	public record ButtonState(long id, String name, String mode, boolean on) {
	}

	/** @param color display color {@code #rrggbb} */
	public record PaletteResponse(String id, String label, String color) {

		static PaletteResponse from(Palette palette) {
			var color = palette.color();
			return new PaletteResponse(palette.id(), palette.label(), "#%02x%02x%02x".formatted(
					Math.round(color.red() * 255), Math.round(color.green() * 255), Math.round(color.blue() * 255)));
		}
	}

	/** A party button with its values, for the setup tab. */
	public record PartyButtonResponse(long id, int position, String name, String mode, int fixtureCount,
			Map<Long, Map<String, Double>> values) {

		static PartyButtonResponse from(PartyButton button) {
			return new PartyButtonResponse(button.id(), button.position(), button.name(), button.mode().id(),
					button.values().size(), ValuesJson.toJson(button.values()));
		}
	}
}
