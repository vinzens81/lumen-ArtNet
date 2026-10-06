package de.vinzens.lumen.adapter.web.party;

import de.vinzens.lumen.adapter.web.party.PartyDtos.PartyMatrixDto;
import de.vinzens.lumen.application.party.port.in.ManagePartyMatrixUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The party matrix of a project: size, lines between rows and fields, always as a whole. */
@RestController
@RequestMapping("/api/projects/{projectId}/party-matrix")
class PartyMatrixController {

	private final ManagePartyMatrixUseCase matrices;

	PartyMatrixController(ManagePartyMatrixUseCase matrices) {
		this.matrices = matrices;
	}

	@GetMapping
	PartyMatrixDto matrix(@PathVariable long projectId) {
		return PartyMatrixDto.from(matrices.matrix(projectId));
	}

	@PutMapping
	PartyMatrixDto save(@PathVariable long projectId, @RequestBody PartyMatrixDto request) {
		return PartyMatrixDto.from(matrices.save(projectId, request.toMatrix()));
	}
}
