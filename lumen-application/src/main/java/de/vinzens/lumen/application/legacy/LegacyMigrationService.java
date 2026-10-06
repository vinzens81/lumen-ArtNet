package de.vinzens.lumen.application.legacy;

import de.vinzens.lumen.application.composition.PatchAttributes;
import de.vinzens.lumen.application.composition.port.out.CompositionRepository;
import de.vinzens.lumen.application.legacy.port.out.LegacyShowStore;
import de.vinzens.lumen.application.library.port.in.QueryFixtureLibraryUseCase;
import de.vinzens.lumen.application.look.port.out.LookRepository;
import de.vinzens.lumen.application.playback.port.out.FaderRepository;
import de.vinzens.lumen.application.project.ShowContentWriter;
import de.vinzens.lumen.application.project.port.out.FixtureRepository;
import de.vinzens.lumen.application.project.port.out.GroupRepository;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.application.scene.port.out.SceneRepository;
import de.vinzens.lumen.domain.look.LookRevision;

import java.lang.System.Logger.Level;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Converts the shows of a database from before iteration S into looks, compositions and scenes, once, at startup –
 * before the playback loads the active project. The old data stays in the database untouched.
 */
public class LegacyMigrationService {

	private static final System.Logger LOG = System.getLogger(LegacyMigrationService.class.getName());

	private final LegacyShowStore legacy;
	private final ProjectRepository projects;
	private final FixtureRepository fixtures;
	private final GroupRepository groups;
	private final LookRepository looks;
	private final CompositionRepository compositions;
	private final SceneRepository scenes;
	private final FaderRepository faders;
	private final QueryFixtureLibraryUseCase library;
	private final Clock clock;

	public LegacyMigrationService(LegacyShowStore legacy, ProjectRepository projects, FixtureRepository fixtures,
			GroupRepository groups, LookRepository looks, CompositionRepository compositions, SceneRepository scenes,
			FaderRepository faders, QueryFixtureLibraryUseCase library, Clock clock) {
		this.legacy = Objects.requireNonNull(legacy, "legacy");
		this.projects = Objects.requireNonNull(projects, "projects");
		this.fixtures = Objects.requireNonNull(fixtures, "fixtures");
		this.groups = Objects.requireNonNull(groups, "groups");
		this.looks = Objects.requireNonNull(looks, "looks");
		this.compositions = Objects.requireNonNull(compositions, "compositions");
		this.scenes = Objects.requireNonNull(scenes, "scenes");
		this.faders = Objects.requireNonNull(faders, "faders");
		this.library = Objects.requireNonNull(library, "library");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/** @return what changed or got lost per project id (empty if there was nothing to convert) */
	public Map<Long, List<String>> convertPending() {
		var reports = new HashMap<Long, List<String>>();
		for (var projectId : legacy.pendingProjects()) {
			reports.put(projectId, convert(projectId));
		}
		return reports;
	}

	private List<String> convert(long projectId) {
		var name = projects.findById(projectId).map(p -> p.spec().name()).orElse("#" + projectId);
		var show = legacy.load(projectId);
		var members = new HashMap<Long, List<Long>>();
		groups.findByProject(projectId).forEach(group -> members.put(group.id(), group.spec().fixtureIds()));
		var result = LegacyConverter.convert(show, members,
				PatchAttributes.of(fixtures.findByProject(projectId), library));
		var sceneIds = new HashMap<Long, Long>();
		show.scenes().forEach(scene -> sceneIds.put(scene.id(), scene.id()));
		var writer = new ShowContentWriter(looks, compositions, scenes, faders);
		var written = writer.write(projectId, result.content(), sceneIds);

		// the history of a scene's values goes to the look that now holds them
		for (var scene : show.scenes()) {
			if (scene.look().values().isEmpty()) {
				continue;
			}
			var base = result.content().sceneCompositions().getOrDefault(scene.id(), List.of());
			if (base.isEmpty()) {
				continue;
			}
			var lookId = result.content().compositions().stream()
					.filter(c -> c.id() == base.getFirst().compositionId())
					.flatMap(c -> c.lookIds().stream())
					.findFirst()
					.map(written.lookIds()::get);
			lookId.ifPresent(id -> legacy.revisions(scene.id()).reversed().forEach(revision ->
					looks.saveRevision(id, revision.createdAt(), revision.reason(), revision.look(), LookRevision.KEEP)));
		}
		writer.removeScenes(result.content(), sceneIds);
		legacy.markConverted(projectId, clock.instant());
		LOG.log(Level.INFO, "Converted the show of \"{0}\" into looks, compositions and scenes", name);
		result.report().forEach(line -> LOG.log(Level.WARNING, "\"{0}\": {1}", name, line));
		return result.report();
	}
}
