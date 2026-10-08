package de.vinzens.lumen.adapter.web.project;

import de.vinzens.lumen.domain.project.Project;
import de.vinzens.lumen.domain.project.ProjectSpec;
import de.vinzens.lumen.domain.scene.FadeCurve;
import de.vinzens.lumen.domain.scene.MoveInDark;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

final class ProjectDtos {

	private ProjectDtos() {
	}

	/**
	 * @param fadeCurve "linear", "s-curve" or "square", {@code null} = linear
	 * @param dimOutMs  dim-out of heads moving in the dark, {@code null} = 500 ms
	 * @param travelMs  time heads get to move in the dark, {@code null} = 1500 ms
	 */
	record ProjectRequest(String name, String venue, Long fadeInMs, Long fadeOutMs, String fadeCurve, Long dimOutMs,
			Long travelMs) {

		ProjectRequest(String name, String venue, Long fadeInMs, Long fadeOutMs, String fadeCurve) {
			this(name, venue, fadeInMs, fadeOutMs, fadeCurve, null, null);
		}

		ProjectSpec toSpec() {
			return new ProjectSpec(name, Optional.ofNullable(venue),
					Duration.ofMillis(Objects.requireNonNullElse(fadeInMs, ProjectSpec.DEFAULT_FADE.toMillis())),
					Duration.ofMillis(Objects.requireNonNullElse(fadeOutMs, ProjectSpec.DEFAULT_FADE.toMillis())),
					fadeCurve == null ? FadeCurve.LINEAR : FadeCurve.parse(fadeCurve),
					Duration.ofMillis(Objects.requireNonNullElse(dimOutMs, MoveInDark.DEFAULT_DIM_OUT.toMillis())),
					Duration.ofMillis(Objects.requireNonNullElse(travelMs, MoveInDark.DEFAULT_TRAVEL.toMillis())));
		}
	}

	record ProjectResponse(long id, String name, String venue, long fadeInMs, long fadeOutMs, String fadeCurve,
			long dimOutMs, long travelMs, boolean active, Instant createdAt, Instant updatedAt) {

		static ProjectResponse from(Project project) {
			var spec = project.spec();
			return new ProjectResponse(project.id(), spec.name(), spec.venue().orElse(null), spec.fadeIn().toMillis(),
					spec.fadeOut().toMillis(), spec.fadeCurve().id(), spec.dimOut().toMillis(),
					spec.travel().toMillis(), project.active(), project.createdAt(), project.updatedAt());
		}
	}
}
