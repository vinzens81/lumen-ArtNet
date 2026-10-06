package de.vinzens.lumen.application.look;

import de.vinzens.lumen.application.ConflictException;
import de.vinzens.lumen.application.NotFoundException;
import de.vinzens.lumen.application.composition.port.out.CompositionRepository;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.look.port.in.ManageLooksUseCase;
import de.vinzens.lumen.application.look.port.out.LookRepository;
import de.vinzens.lumen.application.output.port.in.ControlProgrammerUseCase;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.domain.look.Look;
import de.vinzens.lumen.domain.look.LookRevision;
import de.vinzens.lumen.domain.look.LookSpec;
import de.vinzens.lumen.domain.look.NamedLook;
import de.vinzens.lumen.domain.project.Project;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class LookService implements ManageLooksUseCase {

	private final ProjectRepository projects;
	private final LookRepository looks;
	private final CompositionRepository compositions;
	private final ControlProgrammerUseCase programmer;
	private final StateEvents events;
	private final Clock clock;

	public LookService(ProjectRepository projects, LookRepository looks, CompositionRepository compositions,
			ControlProgrammerUseCase programmer, StateEvents events, Clock clock) {
		this.projects = Objects.requireNonNull(projects, "projects");
		this.looks = Objects.requireNonNull(looks, "looks");
		this.compositions = Objects.requireNonNull(compositions, "compositions");
		this.programmer = Objects.requireNonNull(programmer, "programmer");
		this.events = Objects.requireNonNull(events, "events");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Override
	public List<NamedLook> looks(long projectId) {
		requireProject(projectId);
		return looks.findByProject(projectId);
	}

	@Override
	public NamedLook look(long projectId, long lookId) {
		requireProject(projectId);
		return looks.findById(lookId)
				.filter(look -> look.projectId() == projectId)
				.orElseThrow(() -> new NotFoundException("Look " + lookId + " does not exist in project " + projectId));
	}

	@Override
	public NamedLook create(long projectId, String name, boolean recordFromProgrammer) {
		if (recordFromProgrammer) {
			requireActive(projectId);
		} else {
			requireProject(projectId);
		}
		var content = recordFromProgrammer ? programmer.programmerLook() : Look.EMPTY;
		return published(looks.create(projectId, LookSpec.shared(name), content));
	}

	@Override
	public NamedLook update(long projectId, long lookId, String name, boolean shared) {
		var look = look(projectId, lookId);
		if (!shared && look.spec().isShared()) {
			throw new ConflictException("A shared look cannot be given back to a composition");
		}
		var spec = new LookSpec(name, shared ? Optional.empty() : look.spec().owner());
		return spec.equals(look.spec()) ? look : published(looks.updateSpec(lookId, spec));
	}

	@Override
	public NamedLook duplicate(long projectId, long lookId) {
		var original = look(projectId, lookId);
		return published(looks.create(projectId, LookSpec.shared(original.spec().name() + " (copy)"),
				original.look()));
	}

	@Override
	public NamedLook record(long projectId, long lookId, RecordOptions options) {
		requireActive(projectId);
		var look = look(projectId, lookId);
		var content = options.merge()
				? look.look().merge(programmer.programmerLook(), options.fixtureIds(), options.groups())
				: programmer.programmerLook();
		return replace(look, content, options.merge() ? "Merge from programmer" : "Update from programmer");
	}

	@Override
	public List<LookRevision> revisions(long projectId, long lookId) {
		look(projectId, lookId);
		return looks.findRevisions(lookId);
	}

	@Override
	public NamedLook restore(long projectId, long lookId, long revisionId) {
		var look = look(projectId, lookId);
		var revision = looks.findRevision(revisionId)
				.filter(r -> r.lookId() == lookId)
				.orElseThrow(() -> new NotFoundException("Revision " + revisionId + " does not exist for look "
						+ lookId));
		return replace(look, revision.look(), "Restore");
	}

	/** Replaces the content of a look, keeping the old content as a revision. */
	private NamedLook replace(NamedLook look, Look content, String reason) {
		if (look.look().equals(content)) {
			return look;
		}
		looks.saveRevision(look.id(), clock.instant(), reason, look.look(), LookRevision.KEEP);
		return published(looks.replaceLook(look.id(), content));
	}

	@Override
	public void loadIntoProgrammer(long projectId, long lookId) {
		requireActive(projectId);
		programmer.loadLook(look(projectId, lookId).look());
	}

	@Override
	public void delete(long projectId, long lookId) {
		look(projectId, lookId);
		var users = compositions.findByProject(projectId).stream()
				.filter(composition -> composition.lookIds().contains(lookId))
				.map(composition -> "\"" + composition.spec().name() + "\"")
				.collect(Collectors.joining(", "));
		if (!users.isEmpty()) {
			throw new ConflictException("The look is played by " + users + " – remove it from there first");
		}
		looks.delete(lookId);
		events.publish(StateTopic.LOOKS);
	}

	private Project requireProject(long projectId) {
		return projects.findById(projectId)
				.orElseThrow(() -> new NotFoundException("Project " + projectId + " does not exist"));
	}

	private void requireActive(long projectId) {
		if (!requireProject(projectId).active()) {
			throw new ConflictException("The programmer belongs to the active project – activate project "
					+ projectId + " first");
		}
	}

	private NamedLook published(NamedLook look) {
		events.publish(StateTopic.LOOKS);
		return look;
	}
}
