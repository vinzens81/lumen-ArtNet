package de.vinzens.lumen.application.composition.port.in;

import de.vinzens.lumen.domain.composition.Composition;
import de.vinzens.lumen.domain.composition.CompositionSpec;
import de.vinzens.lumen.domain.composition.CompositionStep;
import de.vinzens.lumen.domain.composition.GeneratorSettings;

import java.util.List;

/**
 * Compositions of a project: steps that play looks. A composition can be part of any number of scenes; a change
 * applies to all of them.
 */
public interface ManageCompositionsUseCase {

	/** By name. */
	List<Composition> compositions(long projectId);

	Composition composition(long projectId, long compositionId);

	/** Steps may play shared looks; own looks come from {@link #recordStep} or {@link #generate}. */
	Composition create(long projectId, CompositionSpec spec, List<CompositionStep> steps);

	/**
	 * Replaces properties and steps. Steps may play shared looks and the composition's own looks; own looks that no
	 * step plays any more are deleted.
	 */
	Composition update(long projectId, long compositionId, CompositionSpec spec, List<CompositionStep> steps);

	/** A copy with copies of its own looks; it is in no scene. */
	Composition duplicate(long projectId, long compositionId);

	/** Deletes a composition that is in no scene; otherwise a conflict names the scenes. */
	void delete(long projectId, long compositionId);

	/** Records the programmer as a new own look and adds it as the last step. Active project only. */
	Composition recordStep(long projectId, long compositionId);

	/**
	 * Replaces the steps with steps generated from an effect (chase, rainbow, …), each with an own look. Default
	 * times, play mode and intensity mode come from the effect; the settings are kept for generating again.
	 */
	Composition generate(long projectId, long compositionId, GeneratorSettings settings);
}
