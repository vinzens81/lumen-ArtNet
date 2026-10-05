package de.vinzens.lumen.adapter.web.party;

import de.vinzens.lumen.adapter.web.party.PartyDtos.PartyButtonResponse;
import de.vinzens.lumen.application.party.port.in.ManagePartyButtonsUseCase;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.composition.KeyMode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** The party buttons of a project; recording takes the values of the programmer. */
@RestController
@RequestMapping("/api/projects/{projectId}/party-buttons")
class PartyButtonController {

	private final ManagePartyButtonsUseCase buttons;

	PartyButtonController(ManagePartyButtonsUseCase buttons) {
		this.buttons = buttons;
	}

	@GetMapping
	List<PartyButtonResponse> list(@PathVariable long projectId) {
		return buttons.buttons(projectId).stream().map(PartyButtonResponse::from).toList();
	}

	/** Records a new button from the programmer. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	PartyButtonResponse record(@PathVariable long projectId, @RequestBody ButtonRequest request) {
		return PartyButtonResponse.from(buttons.record(projectId, request.name(), mode(request.mode())));
	}

	@PutMapping("/{buttonId}")
	PartyButtonResponse update(@PathVariable long projectId, @PathVariable long buttonId,
			@RequestBody ButtonRequest request) {
		return PartyButtonResponse.from(buttons.update(projectId, buttonId, request.name(), mode(request.mode())));
	}

	/** Overwrites the values of the button with the programmer. */
	@PostMapping("/{buttonId}/record")
	PartyButtonResponse rerecord(@PathVariable long projectId, @PathVariable long buttonId) {
		return PartyButtonResponse.from(buttons.rerecord(projectId, buttonId));
	}

	@PostMapping("/{buttonId}/load")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void load(@PathVariable long projectId, @PathVariable long buttonId) {
		buttons.loadIntoProgrammer(projectId, buttonId);
	}

	@PutMapping("/order")
	List<PartyButtonResponse> reorder(@PathVariable long projectId, @RequestBody OrderRequest request) {
		return buttons.reorder(projectId, request.ids() == null ? List.of() : request.ids()).stream()
				.map(PartyButtonResponse::from).toList();
	}

	@DeleteMapping("/{buttonId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable long projectId, @PathVariable long buttonId) {
		buttons.delete(projectId, buttonId);
	}

	private static KeyMode mode(String id) {
		if (id == null) {
			return KeyMode.TOGGLE;
		}
		return Arrays.stream(KeyMode.values()).filter(m -> m.id().equals(id.toLowerCase(Locale.ROOT))).findFirst()
				.orElseThrow(() -> new DomainValidationException(List.of("mode must be toggle or flash, was '" + id
						+ "'")));
	}

	/** @param mode toggle (default) or flash */
	record ButtonRequest(String name, String mode) {
	}

	record OrderRequest(List<Long> ids) {
	}
}
