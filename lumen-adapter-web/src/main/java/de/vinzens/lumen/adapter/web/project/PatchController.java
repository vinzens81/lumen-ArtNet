package de.vinzens.lumen.adapter.web.project;

import de.vinzens.lumen.adapter.web.project.PatchDtos.FixtureRequest;
import de.vinzens.lumen.adapter.web.project.PatchDtos.FixtureResponse;
import de.vinzens.lumen.adapter.web.project.PatchDtos.GroupRequest;
import de.vinzens.lumen.adapter.web.project.PatchDtos.GroupResponse;
import de.vinzens.lumen.adapter.web.project.PatchDtos.PatchIssueResponse;
import de.vinzens.lumen.adapter.web.project.PatchDtos.UniverseRequest;
import de.vinzens.lumen.adapter.web.project.PatchDtos.UniverseResponse;
import de.vinzens.lumen.application.library.port.in.QueryFixtureLibraryUseCase;
import de.vinzens.lumen.application.project.port.in.ManagePatchUseCase;
import de.vinzens.lumen.domain.library.Mode;
import de.vinzens.lumen.domain.project.PatchedFixture;
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
import java.util.Optional;

@RestController
@RequestMapping("/api/projects/{projectId}")
class PatchController {

	private final ManagePatchUseCase patch;
	private final QueryFixtureLibraryUseCase library;

	PatchController(ManagePatchUseCase patch, QueryFixtureLibraryUseCase library) {
		this.patch = patch;
		this.library = library;
	}

	// --- Universen -------------------------------------------------------------------------------------------

	@GetMapping("/universes")
	List<UniverseResponse> universes(@PathVariable long projectId) {
		return patch.universes(projectId).stream().map(UniverseResponse::from).toList();
	}

	@PostMapping("/universes")
	@ResponseStatus(HttpStatus.CREATED)
	UniverseResponse createUniverse(@PathVariable long projectId, @RequestBody UniverseRequest request) {
		return UniverseResponse.from(patch.createUniverse(projectId, request.toSpec()));
	}

	@PutMapping("/universes/{universeId}")
	UniverseResponse updateUniverse(@PathVariable long projectId, @PathVariable long universeId,
			@RequestBody UniverseRequest request) {
		return UniverseResponse.from(patch.updateUniverse(projectId, universeId, request.toSpec()));
	}

	@DeleteMapping("/universes/{universeId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteUniverse(@PathVariable long projectId, @PathVariable long universeId) {
		patch.deleteUniverse(projectId, universeId);
	}

	// --- Geräte ----------------------------------------------------------------------------------------------

	@GetMapping("/fixtures")
	List<FixtureResponse> fixtures(@PathVariable long projectId) {
		return patch.fixtures(projectId).stream().map(this::toResponse).toList();
	}

	@PostMapping("/fixtures")
	@ResponseStatus(HttpStatus.CREATED)
	FixtureResponse createFixture(@PathVariable long projectId, @RequestBody FixtureRequest request) {
		return toResponse(patch.createFixture(projectId, request.toSpec()));
	}

	@PutMapping("/fixtures/{fixtureId}")
	FixtureResponse updateFixture(@PathVariable long projectId, @PathVariable long fixtureId,
			@RequestBody FixtureRequest request) {
		return toResponse(patch.updateFixture(projectId, fixtureId, request.toSpec()));
	}

	@DeleteMapping("/fixtures/{fixtureId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteFixture(@PathVariable long projectId, @PathVariable long fixtureId) {
		patch.deleteFixture(projectId, fixtureId);
	}

	private FixtureResponse toResponse(PatchedFixture fixture) {
		var spec = fixture.spec();
		Optional<Integer> channelCount = library.fixtureType(spec.fixtureTypeId())
				.flatMap(type -> type.mode(spec.modeName()))
				.map(Mode::dmxChannelCount);
		return FixtureResponse.from(fixture, channelCount);
	}

	// --- Gruppen ---------------------------------------------------------------------------------------------

	@GetMapping("/groups")
	List<GroupResponse> groups(@PathVariable long projectId) {
		return patch.groups(projectId).stream().map(GroupResponse::from).toList();
	}

	@PostMapping("/groups")
	@ResponseStatus(HttpStatus.CREATED)
	GroupResponse createGroup(@PathVariable long projectId, @RequestBody GroupRequest request) {
		return GroupResponse.from(patch.createGroup(projectId, request.toSpec()));
	}

	@PutMapping("/groups/{groupId}")
	GroupResponse updateGroup(@PathVariable long projectId, @PathVariable long groupId,
			@RequestBody GroupRequest request) {
		return GroupResponse.from(patch.updateGroup(projectId, groupId, request.toSpec()));
	}

	@DeleteMapping("/groups/{groupId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteGroup(@PathVariable long projectId, @PathVariable long groupId) {
		patch.deleteGroup(projectId, groupId);
	}

	// --- Prüfung ---------------------------------------------------------------------------------------------

	@GetMapping("/patch/issues")
	List<PatchIssueResponse> issues(@PathVariable long projectId) {
		return patch.issues(projectId).stream().map(PatchIssueResponse::from).toList();
	}
}
