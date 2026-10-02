package de.vinzens.lumen.adapter.web.project;

import de.vinzens.lumen.domain.project.Project;
import de.vinzens.lumen.domain.project.ProjectSpec;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

final class ProjectDtos {

	private ProjectDtos() {
	}

	record ProjectRequest(String name, String venue, Long fadeInMs, Long fadeOutMs) {

		ProjectSpec toSpec() {
			return new ProjectSpec(name, Optional.ofNullable(venue),
					Duration.ofMillis(Objects.requireNonNullElse(fadeInMs, ProjectSpec.DEFAULT_FADE.toMillis())),
					Duration.ofMillis(Objects.requireNonNullElse(fadeOutMs, ProjectSpec.DEFAULT_FADE.toMillis())));
		}
	}

	record ProjectResponse(long id, String name, String venue, long fadeInMs, long fadeOutMs, boolean active,
			Instant createdAt, Instant updatedAt) {

		static ProjectResponse from(Project project) {
			var spec = project.spec();
			return new ProjectResponse(project.id(), spec.name(), spec.venue().orElse(null), spec.fadeIn().toMillis(),
					spec.fadeOut().toMillis(), project.active(), project.createdAt(), project.updatedAt());
		}
	}
}
