package de.vinzens.lumen.application.project.port.in;

import de.vinzens.lumen.application.legacy.LegacyShow;
import de.vinzens.lumen.domain.composition.Composition;
import de.vinzens.lumen.domain.composition.Fader;
import de.vinzens.lumen.domain.look.NamedLook;
import de.vinzens.lumen.domain.move.Figure;
import de.vinzens.lumen.domain.move.Move;
import de.vinzens.lumen.domain.party.PartyMatrix;
import de.vinzens.lumen.domain.preset.Preset;
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
 * Exports a project with patch, groups, looks, compositions and scenes, e.g. as a backup or to take a show to another
 * computer, and imports it again as a new project.
 */
public interface TransferProjectUseCase {

	ProjectExport export(long projectId);

	/** Creates a new project from an export; ids in the export are only used to connect its parts. */
	Project importProject(ProjectExport export);

	/**
	 * Everything that belongs to a project, connected by the ids in this export: fixtures refer to universes, groups
	 * and looks to fixtures, looks to presets and their composition, compositions to looks (and generator targets to
	 * groups and fixtures), moves to figures and fixtures, scenes, faders and the fields of the party matrix to
	 * compositions and moves (fields also to scenes).
	 *
	 * @param legacy scenes, compositions and keys of a file from before iteration S; then {@code looks},
	 *               {@code compositions} and {@code scenes} are empty and the import converts the old show
	 */
	record ProjectExport(ProjectSpec project, List<Universe> universes, List<PatchedFixture> fixtures,
			List<Group> groups, List<NamedLook> looks, List<Composition> compositions, List<Scene> scenes,
			Optional<StagePlan> stagePlan, PartyMatrix partyMatrix, List<Preset> presets, List<Fader> faders,
			List<Figure> figures, List<Move> moves, LegacyShow legacy) {

		public ProjectExport {
			Objects.requireNonNull(stagePlan, "stagePlan");
			Objects.requireNonNull(legacy, "legacy");
			Objects.requireNonNull(partyMatrix, "partyMatrix");
			universes = List.copyOf(universes);
			fixtures = List.copyOf(fixtures);
			groups = List.copyOf(groups);
			looks = List.copyOf(looks);
			compositions = List.copyOf(compositions);
			scenes = List.copyOf(scenes);
			presets = List.copyOf(presets);
			faders = List.copyOf(faders);
			figures = List.copyOf(figures);
			moves = List.copyOf(moves);
		}

		/** An export without moves (files before version 4). */
		public ProjectExport(ProjectSpec project, List<Universe> universes, List<PatchedFixture> fixtures,
				List<Group> groups, List<NamedLook> looks, List<Composition> compositions, List<Scene> scenes,
				Optional<StagePlan> stagePlan, PartyMatrix partyMatrix, List<Preset> presets, List<Fader> faders,
				LegacyShow legacy) {
			this(project, universes, fixtures, groups, looks, compositions, scenes, stagePlan, partyMatrix, presets,
					faders, List.of(), List.of(), legacy);
		}
	}
}
