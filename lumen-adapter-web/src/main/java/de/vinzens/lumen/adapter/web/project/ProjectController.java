package de.vinzens.lumen.adapter.web.project;

import de.vinzens.lumen.adapter.web.project.ProjectDtos.ProjectRequest;
import de.vinzens.lumen.adapter.web.project.ProjectDtos.ProjectResponse;
import de.vinzens.lumen.application.project.port.in.ManageProjectsUseCase;
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

import java.util.List;

@RestController
@RequestMapping("/api/projects")
class ProjectController {

	private final ManageProjectsUseCase projects;

	ProjectController(ManageProjectsUseCase projects) {
		this.projects = projects;
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
