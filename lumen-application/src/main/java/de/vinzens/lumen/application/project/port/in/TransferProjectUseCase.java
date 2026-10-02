package de.vinzens.lumen.application.project.port.in;

import de.vinzens.lumen.domain.project.Group;
import de.vinzens.lumen.domain.project.PatchedFixture;
import de.vinzens.lumen.domain.project.Project;
import de.vinzens.lumen.domain.project.ProjectSpec;
import de.vinzens.lumen.domain.project.StagePlan;
import de.vinzens.lumen.domain.project.Universe;
import de.vinzens.lumen.domain.scene.Scene;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Exports a project with patch, groups and scenes, e.g. as a backup or to take a show to another computer,
 * and imports it again as a new project.
 */
public interface TransferProjectUseCase {

	ProjectExport export(long projectId);

	/** Creates a new project from an export; ids in the export are only used to connect its parts. */
	Project importProject(ProjectExport export);

	/**
	 * Everything that belongs to a project. Fixtures refer to universes, groups and scene values to fixtures by
	 * the ids in this export.
	 */
	record ProjectExport(ProjectSpec project, List<Universe> universes, List<PatchedFixture> fixtures,
			List<Group> groups, List<Scene> scenes, Optional<StagePlan> stagePlan) {

		public ProjectExport {
			Objects.requireNonNull(stagePlan, "stagePlan");
			universes = List.copyOf(universes);
			fixtures = List.copyOf(fixtures);
			groups = List.copyOf(groups);
			scenes = List.copyOf(scenes);
		}
	}
}
