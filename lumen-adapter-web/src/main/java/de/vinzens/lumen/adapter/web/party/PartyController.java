package de.vinzens.lumen.adapter.web.party;

import de.vinzens.lumen.adapter.web.party.PartyDtos.PartyResponse;
import de.vinzens.lumen.adapter.web.party.PartyDtos.SlideRequest;
import de.vinzens.lumen.application.party.port.in.ControlPartyUseCase;
import de.vinzens.lumen.domain.DomainValidationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Live control in party mode: fields of the matrix by row and column (from 0). Commands answer 409 outside it. */
@RestController
@RequestMapping("/api/party")
class PartyController {

	private final ControlPartyUseCase party;

	PartyController(ControlPartyUseCase party) {
		this.party = party;
	}

	@GetMapping
	PartyResponse status() {
		return PartyResponse.from(party.status());
	}

	@PostMapping("/cells/{row}/{column}/press")
	PartyResponse press(@PathVariable int row, @PathVariable int column) {
		party.press(row, column);
		return status();
	}

	@PostMapping("/cells/{row}/{column}/release")
	PartyResponse release(@PathVariable int row, @PathVariable int column) {
		party.release(row, column);
		return status();
	}

	/** Touches a gradient field at a point (0 = left end, 1 = right end); dragging goes over the WebSocket. */
	@PostMapping("/cells/{row}/{column}/slide")
	PartyResponse slide(@PathVariable int row, @PathVariable int column, @RequestBody SlideRequest request) {
		if (request.point() == null) {
			throw new DomainValidationException(List.of("point is required"));
		}
		party.slide(row, column, request.point());
		return status();
	}

	/** Everything off. */
	@PostMapping("/clear")
	PartyResponse clear() {
		party.clear();
		return status();
	}
}
