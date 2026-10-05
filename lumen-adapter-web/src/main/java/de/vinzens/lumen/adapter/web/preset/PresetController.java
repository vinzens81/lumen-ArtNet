package de.vinzens.lumen.adapter.web.preset;

import de.vinzens.lumen.adapter.web.output.ValuesJson;
import de.vinzens.lumen.application.preset.port.in.ManagePresetsUseCase;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.library.AttributeGroup;
import de.vinzens.lumen.domain.preset.Preset;
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
import java.util.Map;

/**
 * Presets of a project: named colors, positions or beam settings per fixture. Recording and applying use the
 * programmer of the active project.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/presets")
class PresetController {

	private final ManagePresetsUseCase presets;

	PresetController(ManagePresetsUseCase presets) {
		this.presets = presets;
	}

	@GetMapping
	List<PresetResponse> presets(@PathVariable long projectId) {
		return presets.presets(projectId).stream().map(PresetResponse::from).toList();
	}

	/** Records a preset from the programmer values of the given fixtures. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	PresetResponse create(@PathVariable long projectId, @RequestBody PresetRequest request) {
		if (request.group() == null) {
			throw new DomainValidationException(List.of("group is missing"));
		}
		return PresetResponse.from(presets.create(projectId, request.name(), AttributeGroup.parse(request.group()),
				fixtureIds(request.fixtureIds())));
	}

	@PutMapping("/{presetId}")
	PresetResponse rename(@PathVariable long projectId, @PathVariable long presetId,
			@RequestBody PresetRequest request) {
		return PresetResponse.from(presets.rename(projectId, presetId, request.name()));
	}

	/** Takes the programmer values of the given fixtures into the preset; scenes using it change with it. */
	@PostMapping("/{presetId}/record")
	PresetResponse record(@PathVariable long projectId, @PathVariable long presetId,
			@RequestBody FixturesRequest request) {
		return PresetResponse.from(presets.record(projectId, presetId, fixtureIds(request.fixtureIds())));
	}

	/** Sets the preset on the given fixtures in the programmer. */
	@PostMapping("/{presetId}/apply")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void apply(@PathVariable long projectId, @PathVariable long presetId, @RequestBody FixturesRequest request) {
		presets.apply(projectId, presetId, fixtureIds(request.fixtureIds()));
	}

	@DeleteMapping("/{presetId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable long projectId, @PathVariable long presetId) {
		presets.delete(projectId, presetId);
	}

	private static List<Long> fixtureIds(List<Long> ids) {
		return ids == null ? List.of() : ids;
	}

	/**
	 * @param group      "color", "position" or "beam" (only when creating)
	 * @param fixtureIds fixtures whose programmer values are recorded (only when creating)
	 */
	record PresetRequest(String name, String group, List<Long> fixtureIds) {
	}

	record FixturesRequest(List<Long> fixtureIds) {
	}

	/** @param values only attributes of the group, per fixture id */
	record PresetResponse(long id, String name, String group, Map<Long, Map<String, Double>> values) {

		static PresetResponse from(Preset preset) {
			return new PresetResponse(preset.id(), preset.spec().name(), preset.spec().group().id(),
					ValuesJson.toJson(preset.values()));
		}
	}
}
