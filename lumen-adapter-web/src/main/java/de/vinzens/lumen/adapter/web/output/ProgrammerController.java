package de.vinzens.lumen.adapter.web.output;

import de.vinzens.lumen.application.output.port.in.ControlProgrammerUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static de.vinzens.lumen.adapter.web.output.ValuesJson.fromJson;
import static de.vinzens.lumen.adapter.web.output.ValuesJson.toJson;

/**
 * Live values of fixtures in the active project. Values are normalized (0.0–1.0) and keyed by attribute id,
 * e.g. {@code {"dimmer": 1, "red": 0.5, "generic:Program speed": 0.2}}.
 */
@RestController
@RequestMapping("/api/programmer")
class ProgrammerController {

	private final ControlProgrammerUseCase programmer;

	ProgrammerController(ControlProgrammerUseCase programmer) {
		this.programmer = programmer;
	}

	@GetMapping
	Map<Long, Map<String, Double>> values() {
		return toJson(programmer.programmerValues());
	}

	/** Which preset the values of each fixture come from: fixture id → attribute group → preset id. */
	@GetMapping("/presets")
	Map<Long, Map<String, Long>> presets() {
		return PresetRefsJson.toJson(programmer.programmerPresets());
	}

	@PatchMapping("/fixtures/{fixtureId}")
	Map<String, Double> setValues(@PathVariable long fixtureId, @RequestBody Map<String, Double> body) {
		return toJson(programmer.setValues(fixtureId, fromJson(body)));
	}

	/** Sets values for a selection; each fixture only receives the attributes it has. */
	@PatchMapping("/fixtures")
	Map<Long, Map<String, Double>> setSelectionValues(@RequestBody SelectionRequest request) {
		return toJson(programmer.setValues(request.fixtureIds() == null ? List.of() : request.fixtureIds(),
				fromJson(request.values())));
	}

	@PatchMapping("/groups/{groupId}")
	Map<Long, Map<String, Double>> setGroupValues(@PathVariable long groupId, @RequestBody Map<String, Double> body) {
		return toJson(programmer.setGroupValues(groupId, fromJson(body)));
	}

	@DeleteMapping("/fixtures/{fixtureId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void clearFixture(@PathVariable long fixtureId) {
		programmer.clearFixture(fixtureId);
	}

	/** Removes the programmer values of the given fixtures. */
	@PostMapping("/clear")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void clearFixtures(@RequestBody ClearRequest request) {
		programmer.clearFixtures(request.fixtureIds() == null ? List.of() : request.fixtureIds());
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void clear() {
		programmer.clear();
	}

	record SelectionRequest(List<Long> fixtureIds, Map<String, Double> values) {
	}

	record ClearRequest(List<Long> fixtureIds) {
	}
}
