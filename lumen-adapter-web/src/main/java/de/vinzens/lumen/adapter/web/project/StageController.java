package de.vinzens.lumen.adapter.web.project;

import de.vinzens.lumen.adapter.web.project.PatchDtos.PositionDto;
import de.vinzens.lumen.application.project.port.in.ManageStageUseCase;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.project.Position;
import de.vinzens.lumen.domain.project.StagePlan;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stage view: fixture positions (0–1 within the stage area) and the floor plan image.
 */
@RestController
@RequestMapping("/api/projects/{projectId}")
class StageController {

	private final ManageStageUseCase stage;

	StageController(ManageStageUseCase stage) {
		this.stage = stage;
	}

	/** Moves several fixtures at once; {@code null} takes a fixture off the stage. */
	@PutMapping("/fixtures/positions")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void placeFixtures(@PathVariable long projectId, @RequestBody PositionsRequest request) {
		if (request.positions() == null) {
			throw new DomainValidationException(List.of("positions is missing"));
		}
		var positions = new HashMap<Long, Optional<Position>>();
		request.positions().forEach((fixtureId, position) -> positions.put(fixtureId,
				Optional.ofNullable(position).map(p -> new Position(p.x(), p.y()))));
		stage.placeFixtures(projectId, positions);
	}

	@GetMapping("/stage/plan")
	ResponseEntity<byte[]> stagePlan(@PathVariable long projectId) {
		return stage.stagePlan(projectId)
				.map(plan -> ResponseEntity.ok()
						.contentType(MediaType.parseMediaType(plan.contentType()))
						.cacheControl(CacheControl.noCache())
						.header("X-Content-Type-Options", "nosniff")
						.body(plan.data()))
				.orElse(ResponseEntity.notFound().build());
	}

	/** The image as request body with its content type (PNG, JPEG or WebP, at most 5 MB). */
	@PutMapping(path = "/stage/plan", consumes = {"image/png", "image/jpeg", "image/webp"})
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void setStagePlan(@PathVariable long projectId, @RequestHeader("Content-Type") String contentType,
			@RequestBody byte[] data) {
		var type = MediaType.parseMediaType(contentType);
		stage.setStagePlan(projectId, new StagePlan(type.getType() + "/" + type.getSubtype(), data));
	}

	@DeleteMapping("/stage/plan")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void removeStagePlan(@PathVariable long projectId) {
		stage.removeStagePlan(projectId);
	}

	record PositionsRequest(Map<Long, PositionDto> positions) {
	}
}
