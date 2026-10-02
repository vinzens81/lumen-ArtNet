package de.vinzens.lumen.adapter.web.output;

import de.vinzens.lumen.application.output.port.in.ControlMasterUseCase;
import de.vinzens.lumen.application.output.port.in.QueryHealthUseCase;
import de.vinzens.lumen.application.output.port.in.QueryOutputUseCase;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.output.MasterState;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/output")
class OutputController {

	private final QueryOutputUseCase output;
	private final ControlMasterUseCase master;
	private final QueryHealthUseCase health;

	OutputController(QueryOutputUseCase output, ControlMasterUseCase master, QueryHealthUseCase health) {
		this.output = output;
		this.master = master;
		this.health = health;
	}

	/** Render loop and Art-Net status, updated every second (also pushed via WebSocket). */
	@GetMapping("/health")
	HealthResponse health() {
		return HealthResponse.from(health.health());
	}

	/**
	 * Current DMX values of a universe in the active project.
	 *
	 * @param number universe number as shown in the patch
	 */
	@GetMapping("/universes/{number}/dmx")
	DmxResponse dmx(@PathVariable int number) {
		return new DmxResponse(number, output.frame(number).toIntArray());
	}

	@GetMapping("/master")
	MasterResponse master() {
		return MasterResponse.from(master.master());
	}

	@PutMapping("/master")
	MasterResponse setMaster(@RequestBody LevelRequest request) {
		if (request.level() == null) {
			throw new DomainValidationException(List.of("level is missing"));
		}
		return MasterResponse.from(master.setMasterLevel(request.level()));
	}

	@PutMapping("/blackout")
	MasterResponse setBlackout(@RequestBody BlackoutRequest request) {
		return MasterResponse.from(master.setBlackout(Boolean.TRUE.equals(request.active())));
	}

	/**
	 * @param values channel values 0–255, index 0 = channel 1
	 */
	record DmxResponse(int universe, int[] values) {
	}

	record LevelRequest(Double level) {
	}

	record BlackoutRequest(Boolean active) {
	}

	record MasterResponse(double level, boolean blackout) {

		static MasterResponse from(MasterState state) {
			return new MasterResponse(state.level(), state.blackout());
		}
	}
}
