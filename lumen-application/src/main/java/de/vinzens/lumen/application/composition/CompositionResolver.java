package de.vinzens.lumen.application.composition;

import de.vinzens.lumen.application.composition.port.out.CompositionRepository;
import de.vinzens.lumen.application.project.port.out.GroupRepository;
import de.vinzens.lumen.domain.composition.ActiveComposition;
import de.vinzens.lumen.domain.composition.Composition;
import de.vinzens.lumen.domain.composition.Target;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Turns stored compositions into playable ones: group targets become the current members of the group, in order.
 */
public class CompositionResolver {

	private final CompositionRepository compositions;
	private final GroupRepository groups;

	public CompositionResolver(CompositionRepository compositions, GroupRepository groups) {
		this.compositions = Objects.requireNonNull(compositions, "compositions");
		this.groups = Objects.requireNonNull(groups, "groups");
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
