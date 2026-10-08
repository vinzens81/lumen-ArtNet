package de.vinzens.lumen.adapter.web.move;

import de.vinzens.lumen.adapter.web.move.MoveDtos.FigureRequest;
import de.vinzens.lumen.adapter.web.move.MoveDtos.FigureResponse;
import de.vinzens.lumen.adapter.web.move.MoveDtos.MoveRequest;
import de.vinzens.lumen.adapter.web.move.MoveDtos.MoveResponse;
import de.vinzens.lumen.adapter.web.move.MoveDtos.RecordPositionRequest;
import de.vinzens.lumen.application.move.port.in.ManageFiguresUseCase;
import de.vinzens.lumen.application.move.port.in.ManageMovesUseCase;
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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Figures (shapes in the pan/tilt grid) and moves (a figure laid onto fixtures) of a project. Pan and tilt in the
 * show come only from moves.
 */
@RestController
@RequestMapping("/api/projects/{projectId}")
class MoveController {

	private final ManageFiguresUseCase figures;
	private final ManageMovesUseCase moves;

	MoveController(ManageFiguresUseCase figures, ManageMovesUseCase moves) {
		this.figures = figures;
		this.moves = moves;
	}

	@GetMapping("/figures")
	List<FigureResponse> figures(@PathVariable long projectId) {
		return figures.figures(projectId).stream().map(FigureResponse::from).toList();
	}

	@PostMapping("/figures")
	@ResponseStatus(HttpStatus.CREATED)
	FigureResponse createFigure(@PathVariable long projectId, @RequestBody FigureRequest request) {
		return FigureResponse.from(figures.create(projectId, request.toSpec()));
	}

	@GetMapping("/figures/{figureId}")
	FigureResponse figure(@PathVariable long projectId, @PathVariable long figureId) {
		return FigureResponse.from(figures.figure(projectId, figureId));
	}

	/** Replaces name, points and whether the figure is closed; every move using it changes with it. */
	@PutMapping("/figures/{figureId}")
	FigureResponse updateFigure(@PathVariable long projectId, @PathVariable long figureId,
			@RequestBody FigureRequest request) {
		return FigureResponse.from(figures.update(projectId, figureId, request.toSpec()));
	}

	@PostMapping("/figures/{figureId}/duplicate")
	@ResponseStatus(HttpStatus.CREATED)
	FigureResponse duplicateFigure(@PathVariable long projectId, @PathVariable long figureId) {
		return FigureResponse.from(figures.duplicate(projectId, figureId));
	}

	/** Deletes a figure no move uses. */
	@DeleteMapping("/figures/{figureId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteFigure(@PathVariable long projectId, @PathVariable long figureId) {
		figures.delete(projectId, figureId);
	}

	@GetMapping("/moves")
	List<MoveResponse> moves(@PathVariable long projectId) {
		return moves.moves(projectId).stream().map(MoveResponse::from).toList();
	}

	@PostMapping("/moves")
	@ResponseStatus(HttpStatus.CREATED)
	MoveResponse createMove(@PathVariable long projectId, @RequestBody MoveRequest request) {
		return MoveResponse.from(moves.create(projectId, request.toSpec()));
	}

	@GetMapping("/moves/{moveId}")
	MoveResponse move(@PathVariable long projectId, @PathVariable long moveId) {
		return MoveResponse.from(moves.move(projectId, moveId));
	}

	@PutMapping("/moves/{moveId}")
	MoveResponse updateMove(@PathVariable long projectId, @PathVariable long moveId,
			@RequestBody MoveRequest request) {
		return MoveResponse.from(moves.update(projectId, moveId, request.toSpec()));
	}

	@PostMapping("/moves/{moveId}/duplicate")
	@ResponseStatus(HttpStatus.CREATED)
	MoveResponse duplicateMove(@PathVariable long projectId, @PathVariable long moveId) {
		return MoveResponse.from(moves.duplicate(projectId, moveId));
	}

	/** Deletes a move that no scene, light marker, key or party field plays. */
	@DeleteMapping("/moves/{moveId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteMove(@PathVariable long projectId, @PathVariable long moveId) {
		moves.delete(projectId, moveId);
	}

	/**
	 * "Record position": the programmer positions of the fixtures become a move with one point (their middle), each
	 * fixture with its distance from it as its own shift.
	 */
	@PostMapping("/moves/record-position")
	MoveResponse recordPosition(@PathVariable long projectId, @RequestBody RecordPositionRequest request) {
		return MoveResponse.from(moves.recordPosition(projectId, Optional.ofNullable(request.moveId()),
				Objects.requireNonNullElse(request.name(), ""),
				request.fixtureIds() == null ? List.of() : request.fixtureIds()));
	}
}
