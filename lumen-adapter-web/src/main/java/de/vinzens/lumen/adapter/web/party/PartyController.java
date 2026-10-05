package de.vinzens.lumen.adapter.web.party;

import de.vinzens.lumen.adapter.web.party.PartyDtos.PartyResponse;
import de.vinzens.lumen.application.party.port.in.ControlPartyUseCase;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.party.Palette;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;

/** Live control in party mode. Commands answer 409 outside party mode. */
@RestController
@RequestMapping("/api/party")
class PartyController {

	private final ControlPartyUseCase party;
	private final Clock clock;

	PartyController(ControlPartyUseCase party, Clock clock) {
		this.party = party;
		this.clock = clock;
	}

	@GetMapping
	PartyResponse status() {
		return PartyResponse.from(party.status(), clock.instant());
	}

	@PostMapping("/tap")
	PartyResponse tap() {
		party.tap();
		return status();
	}

	@PutMapping("/bpm")
	PartyResponse bpm(@RequestBody BpmRequest request) {
		if (request.bpm() == null) {
			throw new DomainValidationException(List.of("bpm is missing"));
		}
		party.setBpm(request.bpm());
		return status();
	}

	/** Gives the fixtures a palette; dark fixtures come on at full. */
	@PostMapping("/palette")
	PartyResponse palette(@RequestBody PaletteRequest request) {
		var palette = Palette.fromId(request.palette() == null ? "" : request.palette())
				.orElseThrow(() -> new DomainValidationException(List.of("Unknown palette '" + request.palette() + "'")));
		party.setPalette(fixtureIds(request.fixtureIds()), palette);
		return status();
	}

	@PutMapping("/intensity")
	PartyResponse intensity(@RequestBody IntensityRequest request) {
		if (request.level() == null) {
			throw new DomainValidationException(List.of("level is missing"));
		}
		party.setIntensity(fixtureIds(request.fixtureIds()), request.level());
		return status();
	}

	@PostMapping("/buttons/{buttonId}/press")
	PartyResponse press(@PathVariable long buttonId) {
		party.pressButton(buttonId);
		return status();
	}

	@PostMapping("/buttons/{buttonId}/release")
	PartyResponse release(@PathVariable long buttonId) {
		party.releaseButton(buttonId);
		return status();
	}

	/** Palettes, intensities and buttons off. */
	@PostMapping("/clear")
	PartyResponse clear() {
		party.clear();
		return status();
	}

	private static List<Long> fixtureIds(List<Long> ids) {
		if (ids == null || ids.isEmpty()) {
			throw new DomainValidationException(List.of("fixtureIds is missing"));
		}
		return ids;
	}

	record BpmRequest(Double bpm) {
	}

	record PaletteRequest(List<Long> fixtureIds, String palette) {
	}

	record IntensityRequest(List<Long> fixtureIds, Double level) {
	}
}
