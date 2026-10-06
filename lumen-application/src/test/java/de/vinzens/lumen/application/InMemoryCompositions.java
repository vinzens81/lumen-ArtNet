package de.vinzens.lumen.application;

import de.vinzens.lumen.application.composition.port.out.CompositionRepository;
import de.vinzens.lumen.domain.composition.Composition;
import de.vinzens.lumen.domain.composition.CompositionSpec;
import de.vinzens.lumen.domain.composition.CompositionStep;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Compositions in memory, for tests. */
public class InMemoryCompositions implements CompositionRepository {

	private final Map<Long, Composition> compositions = new LinkedHashMap<>();
	private final InMemoryLooks looks;
	private long nextId = 1;

	/** @param looks own looks of a deleted composition are deleted there */
	public InMemoryCompositions(InMemoryLooks looks) {
		this.looks = looks;
	}

	@Override
	public List<Composition> findByProject(long projectId) {
		return compositions.values().stream().filter(c -> c.projectId() == projectId)
				.sorted(Comparator.comparing(c -> c.spec().name())).toList();
	}

	@Override
	public Optional<Composition> findById(long id) {
		return Optional.ofNullable(compositions.get(id));
	}

	@Override
	public Composition create(long projectId, CompositionSpec spec, List<CompositionStep> steps) {
		var created = new Composition(nextId++, projectId, spec, steps);
		compositions.put(created.id(), created);
		return created;
	}

	@Override
	public Composition update(long id, CompositionSpec spec, List<CompositionStep> steps) {
		var updated = new Composition(id, compositions.get(id).projectId(), spec, steps);
		compositions.put(id, updated);
		return updated;
	}

	@Override
	public void delete(long id) {
		compositions.remove(id);
		looks.deleteOwnedBy(id);
	}
}
