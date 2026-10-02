package de.vinzens.lumen.adapter.web.output;

import de.vinzens.lumen.application.output.port.in.QueryOutputUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/output")
class OutputController {

	private final QueryOutputUseCase output;

	OutputController(QueryOutputUseCase output) {
		this.output = output;
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

	/**
	 * @param values channel values 0–255, index 0 = channel 1
	 */
	record DmxResponse(int universe, int[] values) {
	}
}
