package de.vinzens.lumen.application.project.port.out;

import de.vinzens.lumen.domain.project.StagePlan;

import java.util.Optional;

public interface StagePlanRepository {

	Optional<StagePlan> find(long projectId);

	void save(long projectId, StagePlan plan);

	void delete(long projectId);
}
