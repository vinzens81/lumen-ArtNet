package de.vinzens.lumen.adapter.web.project;

import de.vinzens.lumen.adapter.web.project.ProjectDtos.ProjectRequest;
import de.vinzens.lumen.adapter.web.project.ProjectDtos.ProjectResponse;
import de.vinzens.lumen.application.project.port.in.ManageProjectsUseCase;
import de.vinzens.lumen.application.project.port.in.TransferProjectUseCase;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
class ProjectController {

	private final ManageProjectsUseCase projects;
	private final TransferProjectUseCase transfer;
	private final JsonMapper json;

	ProjectController(ManageProjectsUseCase projects, TransferProjectUseCase transfer, JsonMapper json) {
		this.projects = projects;
		this.transfer = transfer;
		this.json = json;
	}

	/** The project with patch, groups, looks, compositions and scenes as a file to download. */
	@GetMapping("/{id}/export")
	ResponseEntity<ProjectFile> export(@PathVariable long id) {
		var file = ProjectFile.from(transfer.export(id), Instant.now(), json);
		var fileName = file.project().name().replaceAll("[^\\p{L}\\p{N} ._-]", "_").strip() + ".lumen.json";
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString())
				.body(file);
	}

	/** Creates a new project from an exported file (any supported version). */
	@PostMapping("/import")
	@ResponseStatus(HttpStatus.CREATED)
	ProjectResponse importProject(@RequestBody JsonNode file) {
		return ProjectResponse.from(transfer.importProject(ProjectFile.read(file, json)));
	}

	@GetMapping
	List<ProjectResponse> projects() {
		return projects.projects().stream().map(ProjectResponse::from).toList();
	}

	@GetMapping("/active")
	ResponseEntity<ProjectResponse> activeProject() {
		return ResponseEntity.of(projects.activeProject().map(ProjectResponse::from));
	}

	@GetMapping("/{id}")
	ProjectResponse project(@PathVariable long id) {
		return ProjectResponse.from(projects.project(id));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	ProjectResponse create(@RequestBody ProjectRequest request) {
		return ProjectResponse.from(projects.create(request.toSpec()));
	}

	@PutMapping("/{id}")
	ProjectResponse update(@PathVariable long id, @RequestBody ProjectRequest request) {
		return ProjectResponse.from(projects.update(id, request.toSpec()));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable long id) {
		projects.delete(id);
	}

	@PostMapping("/{id}/activate")
	ProjectResponse activate(@PathVariable long id) {
		return ProjectResponse.from(projects.activate(id));
	}
}
