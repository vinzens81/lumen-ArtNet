package de.vinzens.lumen.application.composition;

import de.vinzens.lumen.application.composition.port.out.CompositionRepository;
import de.vinzens.lumen.application.project.port.out.GroupRepository;
import de.vinzens.lumen.application.scene.port.out.SceneRepository;
import de.vinzens.lumen.domain.composition.ActiveComposition;
import de.vinzens.lumen.domain.composition.Composition;
import de.vinzens.lumen.domain.composition.ExtraKey;
import de.vinzens.lumen.domain.composition.ExtraKeys;
import de.vinzens.lumen.domain.composition.ExtraKeys.PlayableKey;
import de.vinzens.lumen.domain.scene.Scene;
import de.vinzens.lumen.domain.composition.Target;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Turns stored compositions into playable ones: group targets become the current members of the group, in order.
 */
public class CompositionResolver {

	private final CompositionRepository compositions;
	private final GroupRepository groups;
	private final SceneRepository scenes;

	public CompositionResolver(CompositionRepository compositions, GroupRepository groups) {
		this(compositions, groups, null);
	}

	/** @param scenes for extra keys that play a scene; without, such keys play nothing */
	public CompositionResolver(CompositionRepository compositions, GroupRepository groups, SceneRepository scenes) {
		this.compositions = Objects.requireNonNull(compositions, "compositions");
		this.groups = Objects.requireNonNull(groups, "groups");
		this.scenes = scenes;
	}

	/** Compositions of every scene of the project, by scene id, in their order. */
	public Map<Long, List<ActiveComposition>> scenes(long projectId) {
		var members = groupMembers(projectId);
		var result = new HashMap<Long, List<ActiveComposition>>();
		compositions.findByScenes(projectId).forEach((sceneId, copies) -> result.put(sceneId, copies.stream()
				.map(copy -> ActiveComposition.of(copy, targetFixtures(copy, members)))
				.toList()));
		return result;
	}

	/** Assigned extra keys of the project, ready to play. A key whose scene no longer exists is left out. */
	public List<PlayableKey> keys(long projectId) {
		var members = groupMembers(projectId);
		var result = new ArrayList<PlayableKey>();
		for (var key : compositions.findKeys(projectId)) {
			switch (key.content()) {
				case ExtraKey.Content.PlaysComposition plays -> result.add(new PlayableKey(key.slot(), key.mode(),
						key.fade(), plays.composition().spec().name(),
						ActiveComposition.of(plays.composition(), targetFixtures(plays.composition(), members))));
				case ExtraKey.Content.PlaysScene plays -> findScene(plays.sceneId()).ifPresent(scene -> {
					var sceneCompositions = compositions.findByScene(scene.id()).stream()
							.map(copy -> ActiveComposition.of(copy, targetFixtures(copy, members)))
							.toList();
					result.add(new PlayableKey(key.slot(), key.mode(), key.fade(), scene.spec().name(),
							new ExtraKeys.SceneLook(scene.id(), scene.values(), sceneCompositions)));
				});
			}
		}
		return result;
	}

	private Optional<Scene> findScene(long sceneId) {
		return scenes == null ? Optional.empty() : scenes.findById(sceneId);
	}

	/** Extra keys usable per scene: scene id → slots. */
	public Map<Long, Set<Integer>> sceneKeys(long projectId) {
		return compositions.findSceneKeys(projectId);
	}

	public ActiveComposition resolve(Composition composition) {
		return ActiveComposition.of(composition, targetFixtures(composition, groupMembers(composition.projectId())));
	}

	private Map<Long, List<Long>> groupMembers(long projectId) {
		var members = new HashMap<Long, List<Long>>();
		groups.findByProject(projectId).forEach(group -> members.put(group.id(), group.spec().fixtureIds()));
		return members;
	}

	private static List<Long> targetFixtures(Composition composition, Map<Long, List<Long>> groupMembers) {
		return switch (composition.spec().target()) {
			case Target.Group group -> groupMembers.getOrDefault(group.groupId(), List.of());
			case Target.Fixtures fixtures -> fixtures.fixtureIds();
			case Target.Steps steps -> List.of();
		};
	}
}
