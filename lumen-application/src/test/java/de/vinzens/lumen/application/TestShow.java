package de.vinzens.lumen.application;

import de.vinzens.lumen.application.composition.CompositionResolver;
import de.vinzens.lumen.domain.composition.Composition;
import de.vinzens.lumen.domain.composition.CompositionSpec;
import de.vinzens.lumen.domain.composition.CompositionStep;
import de.vinzens.lumen.domain.composition.FixtureAttributes;
import de.vinzens.lumen.domain.composition.Generator;
import de.vinzens.lumen.domain.composition.Pattern;
import de.vinzens.lumen.domain.composition.PlayMode;
import de.vinzens.lumen.domain.composition.StepSequence;
import de.vinzens.lumen.domain.look.Look;
import de.vinzens.lumen.domain.look.LookSpec;
import de.vinzens.lumen.domain.output.FixtureValues;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Looks, compositions and scenes in memory, with shortcuts to build a show for tests. */
public final class TestShow {

	public final InMemoryLooks looks = new InMemoryLooks();
	public final InMemoryCompositions compositions = new InMemoryCompositions(looks);
	public final InMemoryScenes scenes = new InMemoryScenes();
	public final InMemoryFigures figures = new InMemoryFigures();
	public final InMemoryMoves moves = new InMemoryMoves();

	/** A composition with one step: fixed values, like a recorded scene. */
	public Composition fixed(long projectId, String name, Map<Long, FixtureValues> values) {
		var composition = compositions.create(projectId, CompositionSpec.of(name), List.of());
		var look = looks.create(projectId, new LookSpec(name, Optional.of(composition.id())), Look.of(values));
		return compositions.update(composition.id(), composition.spec(), List.of(CompositionStep.of(look.id())));
	}

	/** A composition whose steps the generator made from an effect, each step with a look of its own. */
	public Composition generated(long projectId, String name, Pattern effect, List<Long> fixtureIds,
			FixtureAttributes attributes) {
		var composition = compositions.create(projectId, CompositionSpec.of(name), List.of());
		var generated = Generator.generate(effect, fixtureIds, false, attributes);
		var steps = new ArrayList<CompositionStep>();
		for (var step : generated.steps()) {
			var look = looks.create(projectId, new LookSpec(name + " – Step " + (steps.size() + 1),
					Optional.of(composition.id())), Look.of(step.values()));
			steps.add(new CompositionStep(look.id(), step.fadeSeconds(), step.holdSeconds()));
		}
		var spec = new CompositionSpec(name, generated.fadeSeconds(), generated.holdSeconds(),
				generated.playMode() == StepSequence.PlayMode.PING_PONG ? PlayMode.PING_PONG : PlayMode.LOOP,
				generated.intensityMode(), Optional.empty());
		return compositions.update(composition.id(), spec, steps);
	}

	public CompositionResolver resolver() {
		return new CompositionResolver(compositions, looks, scenes, moves, figures);
	}
}
