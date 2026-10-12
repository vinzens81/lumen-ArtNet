package de.vinzens.lumen.adapter.web.party;

import de.vinzens.lumen.adapter.web.party.PartyDtos.PartyResponse;
import de.vinzens.lumen.adapter.web.party.PartyDtos.SlideRequest;
import de.vinzens.lumen.application.party.port.in.ControlPartyUseCase;
import de.vinzens.lumen.domain.party.PartyPlace;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * Live control in party mode: fields of the matrix by page, row and column (from 0). Commands answer 409 outside it.
 */
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

	@PostMapping("/pages/{page}/cells/{row}/{column}/press")
	PartyResponse press(@PathVariable int page, @PathVariable int row, @PathVariable int column) {
		party.press(new PartyPlace(page, row, column));
		return status();
	}

	@PostMapping("/pages/{page}/cells/{row}/{column}/release")
	PartyResponse release(@PathVariable int page, @PathVariable int row, @PathVariable int column) {
		party.release(new PartyPlace(page, row, column));
		return status();
	}

	/** Touches a gradient or pad field at a point; dragging goes over the WebSocket. */
	@PostMapping("/pages/{page}/cells/{row}/{column}/slide")
	PartyResponse slide(@PathVariable int page, @PathVariable int row, @PathVariable int column,
			@RequestBody SlideRequest request) {
		party.slide(new PartyPlace(page, row, column), request.toPoint());
		return status();
	}

	/** Everything off. */
	@PostMapping("/clear")
	PartyResponse clear() {
		party.clear();
		return status();
	}
}
