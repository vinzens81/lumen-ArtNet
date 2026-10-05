package de.vinzens.lumen.application.scene.port.in;

import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.library.AttributeGroup;
import de.vinzens.lumen.domain.scene.Scene;
import de.vinzens.lumen.domain.scene.SceneNumber;
import de.vinzens.lumen.domain.scene.SceneRevision;
import de.vinzens.lumen.domain.scene.SceneTiming;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The scene list (cue list) of a project. Recording and loading work with the programmer and therefore only
 * for the active project.
 */
public interface ManageScenesUseCase {

	/** Scenes ordered by number. */
	List<Scene> scenes(long projectId);

	/**
	 * Creates a scene.
	 *
	 * @param number                 the cue number, or empty for the next whole number after the last scene
	 * @param keyOnly                only played by extra keys, GO skips it
	 * @param timing                 fade times of the GO into the scene
	 * @param script                 text spoken on stage during the scene, empty if none
	 * @param recordFromProgrammer   take the current programmer values, otherwise the scene starts empty (dark)
	 */
	Scene create(long projectId, Optional<SceneNumber> number, String name, Optional<String> cueNote,
			Optional<Duration> autoFollow, boolean keyOnly, SceneTiming timing, String script,
			boolean recordFromProgrammer);

	Scene update(long projectId, long sceneId, SceneNumber number, String name, Optional<String> cueNote,
			Optional<Duration> autoFollow, boolean keyOnly, SceneTiming timing, String script);

	/**
	 * Copies a scene with its look, compositions and extra keys. Copied compositions keep their library origin, so a
	 * chase runs on seamlessly from the scene into its copy.
	 *
	 * @param number the cue number of the copy, or empty for the next whole number after the last scene
	 */
	Scene duplicate(long projectId, long sceneId, Optional<SceneNumber> number);

	/** Overwrites the values of the scene with the current programmer. */
	default Scene record(long projectId, long sceneId) {
		return record(projectId, sceneId, RecordOptions.REPLACE);
	}

	/**
	 * Takes the current programmer into the scene. The look before is kept as a revision, see {@link #revisions}.
	 */
	Scene record(long projectId, long sceneId, RecordOptions options);

	/** Earlier looks of the scene, newest first. */
	List<SceneRevision> revisions(long projectId, long sceneId);

	/** Puts an earlier look back; the current look becomes a revision, so the restore can be undone as well. */
	Scene restore(long projectId, long sceneId, long revisionId);

	/** Replaces the programmer with the values of the scene, to preview and edit it. */
	void loadIntoProgrammer(long projectId, long sceneId);

	void delete(long projectId, long sceneId);

	/**
	 * What {@link #record(long, long, RecordOptions)} takes from the programmer.
	 *
	 * @param merge      {@code false}: the programmer replaces the whole look; {@code true}: only the attributes
	 *                   the programmer sets are taken, everything else in the scene stays
	 * @param fixtureIds merge only these fixtures, empty = all fixtures in the programmer
	 * @param groups     merge only attributes of these groups, empty = all
	 */
	record RecordOptions(boolean merge, Optional<Set<Long>> fixtureIds, Set<AttributeGroup> groups) {

		public static final RecordOptions REPLACE = new RecordOptions(false, Optional.empty(), Set.of());

		public RecordOptions {
			Objects.requireNonNull(fixtureIds, "fixtureIds");
			groups = Set.copyOf(groups);
			fixtureIds = fixtureIds.map(Set::copyOf);
			if (!merge && (fixtureIds.isPresent() || !groups.isEmpty())) {
				throw new DomainValidationException(List.of(
						"fixtureIds and groups only apply when merging – replacing takes the whole programmer"));
			}
		}
	}
}
