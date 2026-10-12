package de.vinzens.lumen.adapter.web.quicksetup;

import de.vinzens.lumen.application.quicksetup.port.in.QuickSetupUseCase;
import de.vinzens.lumen.application.quicksetup.port.in.QuickSetupUseCase.PartyTarget;
import de.vinzens.lumen.application.quicksetup.port.in.QuickSetupUseCase.Request;
import de.vinzens.lumen.application.quicksetup.port.in.QuickSetupUseCase.Result;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/** Quick setup: looks, compositions, moves and party fields for fixtures in one go. */
@RestController
@RequestMapping("/api/projects/{projectId}/quick-setup")
class QuickSetupController {

	private final QuickSetupUseCase quickSetup;

	QuickSetupController(QuickSetupUseCase quickSetup) {
		this.quickSetup = quickSetup;
	}

	@PostMapping
	QuickSetupResponse setUp(@PathVariable long projectId, @RequestBody QuickSetupRequest request) {
		return QuickSetupResponse.from(quickSetup.setUp(projectId, request.toRequest()));
	}

	/**
	 * Missing switches count as off.
	 *
	 * @param name         what everything is named after, {@code null} = from the names of the fixtures
	 * @param intensity    looks for 0, 50 and 100 %
	 * @param colors       looks for red, orange, green, blue and violet, with a color palette
	 * @param moves        line, circle and eight for fixtures with pan and tilt
	 * @param partyButtons a row of buttons in the party matrix
	 * @param gradients    a row with an intensity and a color gradient (and a pan/tilt pad) in the party matrix
	 * @param partyPage    the page of the party matrix for the rows (from 0), {@code null} = the first with room
	 * @param newPartyPage {@code true} = a new page for the rows, named after the setup
	 */
	record QuickSetupRequest(List<Long> fixtureIds, String name, Boolean intensity, Boolean colors, Boolean moves,
			Boolean partyButtons, Boolean gradients, Integer partyPage, Boolean newPartyPage) {

		Request toRequest() {
			return new Request(fixtureIds == null ? List.of() : fixtureIds, Optional.ofNullable(name),
					Boolean.TRUE.equals(intensity), Boolean.TRUE.equals(colors), Boolean.TRUE.equals(moves),
					Boolean.TRUE.equals(partyButtons), Boolean.TRUE.equals(gradients),
					new PartyTarget(Optional.ofNullable(partyPage), Boolean.TRUE.equals(newPartyPage)));
		}
	}

	/**
	 * What was made.
	 *
	 * @param presets   the color palette
	 * @param partyPage page of the party matrix the rows went to (0-based), {@code null} = none
	 * @param partyRows rows of that page that were filled (0-based)
	 * @param notes     what was left out and why
	 */
	record QuickSetupResponse(String name, List<Made> looks, List<Made> compositions, List<Made> moves,
			List<Made> presets, Integer partyPage, List<Integer> partyRows, List<String> notes) {

		static QuickSetupResponse from(Result result) {
			return new QuickSetupResponse(result.name(),
					result.looks().stream().map(look -> new Made(look.id(), look.spec().name())).toList(),
					result.compositions().stream().map(c -> new Made(c.id(), c.spec().name())).toList(),
					result.moves().stream().map(move -> new Made(move.id(), move.spec().name())).toList(),
					result.presets().stream().map(preset -> new Made(preset.id(), preset.spec().name())).toList(),
					result.partyPage().orElse(null), result.partyRows(), result.notes());
		}
	}

	record Made(long id, String name) {
	}
}
