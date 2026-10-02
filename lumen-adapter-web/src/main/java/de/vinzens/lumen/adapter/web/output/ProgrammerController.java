package de.vinzens.lumen.adapter.web.output;

import de.vinzens.lumen.application.output.port.in.ControlProgrammerUseCase;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.output.FixtureValues;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

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
		var result = new TreeMap<Long, Map<String, Double>>();
		programmer.programmerValues().forEach((fixtureId, values) -> result.put(fixtureId, toJson(values)));
		return result;
	}

	@PatchMapping("/fixtures/{fixtureId}")
	Map<String, Double> setValues(@PathVariable long fixtureId, @RequestBody Map<String, Double> body) {
		return toJson(programmer.setValues(fixtureId, fromJson(body)));
	}

	@DeleteMapping("/fixtures/{fixtureId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void clearFixture(@PathVariable long fixtureId) {
		programmer.clearFixture(fixtureId);
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void clear() {
		programmer.clear();
	}

	private static FixtureValues fromJson(Map<String, Double> body) {
		var values = new HashMap<Attribute, Double>();
		var errors = new ArrayList<String>();
		body.forEach((id, value) -> {
			try {
				values.put(Attribute.parse(id), value);
			} catch (DomainValidationException e) {
				errors.addAll(e.errors());
			}
		});
		if (!errors.isEmpty()) {
			throw new DomainValidationException(errors);
		}
		return new FixtureValues(values);
	}

	private static Map<String, Double> toJson(FixtureValues values) {
		var result = new TreeMap<String, Double>();
		values.values().forEach((attribute, value) -> result.put(attribute.id(), value));
		return result;
	}
}
