package de.vinzens.lumen.application.party;

import de.vinzens.lumen.application.ConflictException;
import de.vinzens.lumen.application.NotFoundException;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.output.port.in.ControlProgrammerUseCase;
import de.vinzens.lumen.application.party.port.in.ManagePartyButtonsUseCase;
import de.vinzens.lumen.application.party.port.out.PartyButtonRepository;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.composition.KeyMode;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.party.PartyButton;
import de.vinzens.lumen.domain.project.Project;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PartyButtonService implements ManagePartyButtonsUseCase {

	private final ProjectRepository projects;
	private final PartyButtonRepository buttons;
	private final ControlProgrammerUseCase programmer;
	private final StateEvents events;

	public PartyButtonService(ProjectRepository projects, PartyButtonRepository buttons,
			ControlProgrammerUseCase programmer, StateEvents events) {
		this.projects = Objects.requireNonNull(projects, "projects");
		this.buttons = Objects.requireNonNull(buttons, "buttons");
		this.programmer = Objects.requireNonNull(programmer, "programmer");
		this.events = Objects.requireNonNull(events, "events");
	}

	@Override
	public List<PartyButton> buttons(long projectId) {
		requireProject(projectId);
		return buttons.findByProject(projectId);
	}

	@Override
	public PartyButton record(long projectId, String name, KeyMode mode) {
		requireActive(projectId);
		if (buttons.findByProject(projectId).size() >= PartyButton.MAX_PER_PROJECT) {
			throw new ConflictException("A project has at most " + PartyButton.MAX_PER_PROJECT + " party buttons");
		}
		// validates name and mode before anything is stored
		new PartyButton(0, projectId, 0, name, mode, Map.of());
		return published(buttons.create(projectId, name, mode, programmerSnapshot()));
	}

	@Override
	public PartyButton update(long projectId, long buttonId, String name, KeyMode mode) {
		requireButton(projectId, buttonId);
		new PartyButton(buttonId, projectId, 0, name, mode, Map.of());
		return published(buttons.update(buttonId, name, mode));
	}

	@Override
	public PartyButton rerecord(long projectId, long buttonId) {
		requireActive(projectId);
		requireButton(projectId, buttonId);
		return published(buttons.replaceValues(buttonId, programmerSnapshot()));
	}

	@Override
	public void loadIntoProgrammer(long projectId, long buttonId) {
		requireActive(projectId);
		programmer.loadValues(requireButton(projectId, buttonId).values());
	}

	@Override
	public List<PartyButton> reorder(long projectId, List<Long> buttonIds) {
		var existing = buttons(projectId).stream().map(PartyButton::id).toList();
		if (buttonIds.size() != existing.size() || !new HashSet<>(buttonIds).equals(new HashSet<>(existing))) {
			throw new DomainValidationException(List.of("The new order must contain every party button once"));
		}
		buttons.reorder(buttonIds);
		events.publish(StateTopic.PARTY_BUTTONS);
		return buttons.findByProject(projectId);
	}

	@Override
	public void delete(long projectId, long buttonId) {
		requireButton(projectId, buttonId);
		buttons.delete(buttonId);
		events.publish(StateTopic.PARTY_BUTTONS);
	}

	/** Programmer values without fixtures that have no values left; a button without values makes no sense. */
	private Map<Long, FixtureValues> programmerSnapshot() {
		var snapshot = new HashMap<Long, FixtureValues>();
		programmer.programmerValues().forEach((fixtureId, values) -> {
			if (!values.isEmpty()) {
				snapshot.put(fixtureId, values);
			}
		});
		if (snapshot.isEmpty()) {
			throw new ConflictException("The programmer is empty – set the values of the button there first");
		}
		return snapshot;
	}

	private PartyButton requireButton(long projectId, long buttonId) {
		requireProject(projectId);
		return buttons.findById(buttonId)
				.filter(button -> button.projectId() == projectId)
				.orElseThrow(() -> new NotFoundException("Party button " + buttonId + " does not exist in project "
						+ projectId));
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

	private PartyButton published(PartyButton button) {
		events.publish(StateTopic.PARTY_BUTTONS);
		return button;
	}
}
