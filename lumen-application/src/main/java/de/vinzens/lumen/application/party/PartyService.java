package de.vinzens.lumen.application.party;

import de.vinzens.lumen.application.ConflictException;
import de.vinzens.lumen.application.NotFoundException;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.output.OutputLayerSource;
import de.vinzens.lumen.application.output.port.in.ControlModeUseCase;
import de.vinzens.lumen.application.party.port.in.ControlPartyUseCase;
import de.vinzens.lumen.application.party.port.out.PartyButtonRepository;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.domain.library.Mode;
import de.vinzens.lumen.domain.output.LayerOutput;
import de.vinzens.lumen.domain.output.OperatingMode;
import de.vinzens.lumen.domain.party.Palette;
import de.vinzens.lumen.domain.party.PartyButton;
import de.vinzens.lumen.domain.party.PartyLook;
import de.vinzens.lumen.domain.party.TapTempo;
import de.vinzens.lumen.domain.project.Project;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runs party mode: the {@link PartyLook} of the active project is an output layer above the playback, only in
 * party mode. The look stays while switching modes and is cleared when another project becomes active.
 */
public class PartyService implements ControlPartyUseCase, OutputLayerSource {

	static final int PARTY_PRIORITY = 60;

	private final ProjectRepository projects;
	private final PartyButtonRepository buttons;
	private final ControlModeUseCase mode;
	private final Clock clock;
	private final StateEvents events;

	private final PartyLook look = new PartyLook();
	private final TapTempo tempo;
	private List<PartyButton> projectButtons = List.of();
	private Long projectId;

	public PartyService(ProjectRepository projects, PartyButtonRepository buttons, ControlModeUseCase mode,
			Clock clock, StateEvents events) {
		this.projects = Objects.requireNonNull(projects, "projects");
		this.buttons = Objects.requireNonNull(buttons, "buttons");
		this.mode = Objects.requireNonNull(mode, "mode");
		this.clock = Objects.requireNonNull(clock, "clock");
		this.events = Objects.requireNonNull(events, "events");
		this.tempo = new TapTempo(clock.instant());
		events.subscribe(topic -> {
			if (topic == StateTopic.PROJECTS || topic == StateTopic.PARTY_BUTTONS) {
				reload();
			}
		});
		reload();
	}

	/** Loads the buttons of the active project; a different project starts with a dark look. */
	public void reload() {
		var active = projects.findAll().stream().filter(Project::active).findFirst();
		var loaded = active.map(p -> buttons.findByProject(p.id())).orElse(List.of());
		synchronized (this) {
			var id = active.map(Project::id).orElse(null);
			if (!Objects.equals(id, projectId)) {
				look.clear();
				projectId = id;
			}
			projectButtons = loaded;
			look.useButtons(loaded);
		}
		events.publish(StateTopic.PARTY);
	}

	@Override
	public synchronized PartyStatus status() {
		var now = clock.instant();
		var fixtures = new HashMap<Long, FixtureState>();
		look.touchedFixtures().forEach(id -> fixtures.put(id,
				new FixtureState(look.palette(id), look.intensity(id))));
		return new PartyStatus(tempo.bpm(), tempo.lastBeat(now), tempo.beatLength(), fixtures,
				projectButtons.stream()
						.map(b -> new ButtonStatus(b.id(), b.name(), b.mode(), look.isOn(b.id())))
						.toList());
	}

	@Override
	public void tap() {
		synchronized (this) {
			requirePartyMode();
			tempo.tap(clock.instant());
		}
		events.publish(StateTopic.PARTY);
	}

	@Override
	public void setBpm(double bpm) {
		synchronized (this) {
			requirePartyMode();
			tempo.setBpm(bpm);
		}
		events.publish(StateTopic.PARTY);
	}

	@Override
	public void setPalette(Collection<Long> fixtureIds, Palette palette) {
		synchronized (this) {
			requirePartyMode();
			look.setPalette(fixtureIds, palette);
		}
		events.publish(StateTopic.PARTY);
	}

	@Override
	public void setIntensity(Collection<Long> fixtureIds, double level) {
		synchronized (this) {
			requirePartyMode();
			look.setIntensity(fixtureIds, level);
		}
		events.publish(StateTopic.PARTY);
	}

	@Override
	public void pressButton(long buttonId) {
		synchronized (this) {
			requirePartyMode();
			requireButton(buttonId);
			look.press(buttonId);
		}
		events.publish(StateTopic.PARTY);
	}

	@Override
	public void releaseButton(long buttonId) {
		synchronized (this) {
			requireButton(buttonId);
			look.release(buttonId);
		}
		events.publish(StateTopic.PARTY);
	}

	@Override
	public void clear() {
		synchronized (this) {
			requirePartyMode();
			look.clear();
		}
		events.publish(StateTopic.PARTY);
	}

	@Override
	public boolean activeIn(OperatingMode current) {
		return current == OperatingMode.PARTY;
	}

	@Override
	public synchronized LayerOutput layer(Instant now, Map<Long, Mode> modes) {
		return new LayerOutput(PARTY_PRIORITY, look.output(modes));
	}

	private void requirePartyMode() {
		if (mode.mode() != OperatingMode.PARTY) {
			throw new ConflictException("Switch to party mode first");
		}
	}

	private void requireButton(long buttonId) {
		if (projectButtons.stream().noneMatch(b -> b.id() == buttonId)) {
			throw new NotFoundException("Party button " + buttonId + " does not exist");
		}
	}
}
