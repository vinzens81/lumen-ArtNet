package de.vinzens.lumen.adapter.web.look;

import de.vinzens.lumen.adapter.web.output.PresetRefsJson;
import de.vinzens.lumen.adapter.web.output.ValuesJson;
import de.vinzens.lumen.application.look.port.in.ManageLooksUseCase;
import de.vinzens.lumen.application.look.port.in.ManageLooksUseCase.RecordOptions;
import de.vinzens.lumen.domain.library.AttributeGroup;
import de.vinzens.lumen.domain.look.LookRevision;
import de.vinzens.lumen.domain.look.NamedLook;
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

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Looks of a project: fixed values recorded from the programmer, played by the steps of compositions.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/looks")
public class LookController {

	private final ManageLooksUseCase looks;

	LookController(ManageLooksUseCase looks) {
		this.looks = looks;
	}

	/** Shared looks and the own looks of compositions ({@code owner} set). */
	@GetMapping
	List<LookResponse> looks(@PathVariable long projectId) {
		return looks.looks(projectId).stream().map(LookResponse::from).toList();
	}

	@GetMapping("/{lookId}")
	LookResponse look(@PathVariable long projectId, @PathVariable long lookId) {
		return LookResponse.from(looks.look(projectId, lookId));
	}

	/** A new shared look; with {@code record: true} it takes the programmer, otherwise it starts dark. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	LookResponse create(@PathVariable long projectId, @RequestBody LookRequest request) {
		return LookResponse.from(looks.create(projectId, request.name(), Boolean.TRUE.equals(request.record())));
	}

	/**
	 * Renames the look; {@code shared: true} makes the look of a composition a shared one, {@code folder} moves it
	 * (missing = it stays where it is).
	 */
	@PutMapping("/{lookId}")
	LookResponse update(@PathVariable long projectId, @PathVariable long lookId, @RequestBody LookRequest request) {
		var current = looks.look(projectId, lookId);
		var shared = request.shared() == null ? current.spec().isShared() : request.shared();
		return LookResponse.from(looks.update(projectId, lookId, request.name(), shared, request.folder()));
	}

	@PostMapping("/{lookId}/duplicate")
	@ResponseStatus(HttpStatus.CREATED)
	LookResponse duplicate(@PathVariable long projectId, @PathVariable long lookId) {
		return LookResponse.from(looks.duplicate(projectId, lookId));
	}

	/**
	 * Takes the programmer into the look: without a body it replaces everything, with {@code merge: true} only what
	 * the programmer sets is taken (optionally only some fixtures or attribute groups).
	 */
	@PostMapping("/{lookId}/record")
	LookResponse record(@PathVariable long projectId, @PathVariable long lookId,
			@RequestBody(required = false) RecordRequest request) {
		return LookResponse.from(looks.record(projectId, lookId,
				request == null ? RecordOptions.REPLACE : request.toOptions()));
	}

	/** Earlier contents of the look, newest first. */
	@GetMapping("/{lookId}/revisions")
	List<RevisionResponse> revisions(@PathVariable long projectId, @PathVariable long lookId) {
		return looks.revisions(projectId, lookId).stream().map(RevisionResponse::from).toList();
	}

	/** Puts an earlier content back; the current one becomes a revision itself. */
	@PostMapping("/{lookId}/revisions/{revisionId}/restore")
	LookResponse restore(@PathVariable long projectId, @PathVariable long lookId, @PathVariable long revisionId) {
		return LookResponse.from(looks.restore(projectId, lookId, revisionId));
	}

	/** Loads the look into the programmer to preview and edit it. */
	@PostMapping("/{lookId}/load")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void load(@PathVariable long projectId, @PathVariable long lookId) {
		looks.loadIntoProgrammer(projectId, lookId);
	}

	@DeleteMapping("/{lookId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable long projectId, @PathVariable long lookId) {
		looks.delete(projectId, lookId);
	}

	/**
	 * @param record when creating: take the programmer
	 * @param shared when updating: make a look of a composition shared, {@code null} = unchanged
	 * @param folder when updating: the folder it is listed in, {@code null} = unchanged
	 */
	record LookRequest(String name, Boolean record, Boolean shared, String folder) {
	}

	/**
	 * @param merge      take only what the programmer sets, keep the rest of the look
	 * @param fixtureIds when merging: only these fixtures, {@code null} = all in the programmer
	 * @param groups     when merging: only these attribute groups ("intensity", "color", "position", "beam",
	 *                   "other"), {@code null} or empty = all
	 */
	public record RecordRequest(Boolean merge, List<Long> fixtureIds, List<String> groups) {

		public RecordOptions toOptions() {
			var attributeGroups = new HashSet<AttributeGroup>();
			if (groups != null) {
				groups.forEach(group -> attributeGroups.add(AttributeGroup.parse(group)));
			}
			return new RecordOptions(Boolean.TRUE.equals(merge),
					Optional.ofNullable(fixtureIds).map(Set::copyOf), attributeGroups);
		}
	}

	/**
	 * @param owner   the composition the look belongs to, {@code null} = shared
	 * @param values  programmer-style values per fixture id
	 * @param presets presets the values come from: fixture id → attribute group → preset id
	 * @param folder  the folder it is listed in
	 */
	record LookResponse(long id, String name, Long owner, int fixtureCount, Map<Long, Map<String, Double>> values,
			Map<Long, Map<String, Long>> presets, String folder) {

		static LookResponse from(NamedLook look) {
			return new LookResponse(look.id(), look.spec().name(), look.spec().owner().orElse(null),
					look.look().values().size(), ValuesJson.toJson(look.look().values()),
					PresetRefsJson.toJson(look.look().presets()), look.spec().folder());
		}
	}

	/** @param reason what replaced this content, e.g. "Update from programmer" */
	record RevisionResponse(long id, Instant createdAt, String reason, int fixtureCount,
			Map<Long, Map<String, Double>> values, Map<Long, Map<String, Long>> presets) {

		static RevisionResponse from(LookRevision revision) {
			var look = revision.look();
			return new RevisionResponse(revision.id(), revision.createdAt(), revision.reason(), look.values().size(),
					ValuesJson.toJson(look.values()), PresetRefsJson.toJson(look.presets()));
		}
	}
}
