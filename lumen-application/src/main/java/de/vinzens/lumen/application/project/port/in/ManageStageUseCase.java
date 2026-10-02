package de.vinzens.lumen.application.project.port.in;

import de.vinzens.lumen.domain.project.PatchedFixture;
import de.vinzens.lumen.domain.project.Position;
import de.vinzens.lumen.domain.project.StagePlan;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stage view: where fixtures hang and an optional floor plan image behind them.
 */
public interface ManageStageUseCase {

	/**
	 * Moves fixtures on the stage in one step; an empty position takes a fixture off the stage.
	 *
	 * @return the changed fixtures
	 */
	List<PatchedFixture> placeFixtures(long projectId, Map<Long, Optional<Position>> positions);

	Optional<StagePlan> stagePlan(long projectId);

	void setStagePlan(long projectId, StagePlan plan);

	void removeStagePlan(long projectId);
}
