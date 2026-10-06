package de.vinzens.lumen.application;

import de.vinzens.lumen.application.look.port.out.LookRepository;
import de.vinzens.lumen.domain.look.Look;
import de.vinzens.lumen.domain.look.LookRevision;
import de.vinzens.lumen.domain.look.LookSpec;
import de.vinzens.lumen.domain.look.NamedLook;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Looks in memory, for tests. */
public class InMemoryLooks implements LookRepository {

	private final Map<Long, NamedLook> looks = new LinkedHashMap<>();
	private final List<LookRevision> revisions = new ArrayList<>();
	private long nextId = 1;

	@Override
	public List<NamedLook> findByProject(long projectId) {
		return looks.values().stream().filter(l -> l.projectId() == projectId)
				.sorted(Comparator.comparing(l -> l.spec().name())).toList();
	}

	@Override
	public Optional<NamedLook> findById(long id) {
		return Optional.ofNullable(looks.get(id));
	}

	@Override
	public NamedLook create(long projectId, LookSpec spec, Look look) {
		var created = new NamedLook(nextId++, projectId, spec, look);
		looks.put(created.id(), created);
		return created;
	}

	@Override
	public NamedLook updateSpec(long id, LookSpec spec) {
		var look = looks.get(id);
		return put(new NamedLook(id, look.projectId(), spec, look.look()));
	}

	@Override
	public NamedLook replaceLook(long id, Look content) {
		var look = looks.get(id);
		return put(new NamedLook(id, look.projectId(), look.spec(), content));
	}

	@Override
	public void delete(long id) {
		looks.remove(id);
	}

	/** Own looks of a deleted composition go with it, as in the database. */
	public void deleteOwnedBy(long compositionId) {
		looks.values().removeIf(look -> look.spec().owner().equals(Optional.of(compositionId)));
	}

	@Override
	public LookRevision saveRevision(long lookId, Instant createdAt, String reason, Look look, int keep) {
		var revision = new LookRevision(nextId++, lookId, createdAt, reason, look);
		revisions.add(revision);
		var own = findRevisions(lookId);
		own.stream().skip(keep).forEach(revisions::remove);
		return revision;
	}

	@Override
	public List<LookRevision> findRevisions(long lookId) {
		return revisions.stream().filter(r -> r.lookId() == lookId)
				.sorted(Comparator.comparing(LookRevision::createdAt).reversed()).toList();
	}

	@Override
	public Optional<LookRevision> findRevision(long id) {
		return revisions.stream().filter(r -> r.id() == id).findFirst();
	}

	private NamedLook put(NamedLook look) {
		looks.put(look.id(), look);
		return look;
	}
}
