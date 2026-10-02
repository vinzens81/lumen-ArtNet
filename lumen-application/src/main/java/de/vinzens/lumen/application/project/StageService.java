package de.vinzens.lumen.application.project;

import de.vinzens.lumen.application.NotFoundException;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.project.port.in.ManageStageUseCase;
import de.vinzens.lumen.application.project.port.out.FixtureRepository;
import de.vinzens.lumen.application.project.port.out.PatchChangeListener;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.application.project.port.out.StagePlanRepository;
import de.vinzens.lumen.domain.project.FixtureSpec;
import de.vinzens.lumen.domain.project.PatchedFixture;
import de.vinzens.lumen.domain.project.Position;
import de.vinzens.lumen.domain.project.StagePlan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class StageService implements ManageStageUseCase {

	private final ProjectRepository projects;
	private final FixtureRepository fixtures;
	private final StagePlanRepository plans;
	private final PatchChangeListener listener;
	private final StateEvents events;

	public StageService(ProjectRepository projects, FixtureRepository fixtures, StagePlanRepository plans,
			PatchChangeListener listener, StateEvents events) {
		this.projects = Objects.requireNonNull(projects, "projects");
		this.fixtures = Objects.requireNonNull(fixtures, "fixtures");
		this.plans = Objects.requireNonNull(plans, "plans");
		this.listener = Objects.requireNonNull(listener, "listener");
		this.events = Objects.requireNonNull(events, "events");
	}

	@Override
	public List<PatchedFixture> placeFixtures(long projectId, Map<Long, Optional<Position>> positions) {
		requireProject(projectId);
		var targets = positions.keySet().stream()
				.map(id -> fixtures.findById(id).filter(f -> f.projectId() == projectId)
						.orElseThrow(() -> new NotFoundException("Fixture " + id + " does not exist in project "
								+ projectId)))
				.toList();
		var changed = new ArrayList<PatchedFixture>();
		for (var fixture : targets) {
			var spec = fixture.spec();
			var position = positions.get(fixture.id());
			if (position.equals(spec.position())) {
				continue;
			}
			changed.add(fixtures.update(fixture.id(), new FixtureSpec(spec.name(), spec.shortName(),
					spec.fixtureTypeId(), spec.modeName(), spec.universeId(), spec.address(), position,
					spec.sortOrder())));
		}
		if (!changed.isEmpty()) {
			listener.patchChanged();
		}
		return changed;
	}

	@Override
	public Optional<StagePlan> stagePlan(long projectId) {
		requireProject(projectId);
		return plans.find(projectId);
	}

	@Override
	public void setStagePlan(long projectId, StagePlan plan) {
		requireProject(projectId);
		plans.save(projectId, plan);
		events.publish(StateTopic.PATCH);
	}

	@Override
	public void removeStagePlan(long projectId) {
		requireProject(projectId);
		plans.delete(projectId);
		events.publish(StateTopic.PATCH);
	}

	private void requireProject(long projectId) {
		projects.findById(projectId).orElseThrow(() -> ProjectService.notFound(projectId));
	}
}
