package de.vinzens.lumen.application.party;

import de.vinzens.lumen.application.ConflictException;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.output.port.in.ControlModeUseCase;
import de.vinzens.lumen.application.party.port.out.PartyButtonRepository;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.domain.composition.KeyMode;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelDefinition;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.library.Mode;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.output.OperatingMode;
import de.vinzens.lumen.domain.party.Palette;
import de.vinzens.lumen.domain.party.PartyButton;
import de.vinzens.lumen.domain.project.Project;
import de.vinzens.lumen.domain.project.ProjectSpec;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PartyServiceTest {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	private static final Attribute GOBO = Attribute.of(ChannelFunction.GOBO);
	private static final Mode SPOT = new Mode("spot", List.of(
			new ChannelDefinition(ChannelFunction.DIMMER, Optional.empty(), 8, 0, false, List.of(), Optional.empty()),
			new ChannelDefinition(ChannelFunction.GOBO, Optional.empty(), 8, 0, false, List.of(), Optional.empty())));

	private OperatingMode currentMode = OperatingMode.PARTY;
	private long activeProject = 1;
	private final List<PartyButton> stored = new ArrayList<>(List.of(new PartyButton(5, 1, 1, "Gobo", KeyMode.TOGGLE,
			Map.of(3L, new FixtureValues(Map.of(GOBO, 0.4))))));
	private final StateEvents events = new StateEvents();
	private final PartyService party = new PartyService(projects(), buttons(), mode(),
			Clock.fixed(Instant.parse("2026-10-05T22:00:00Z"), ZoneOffset.UTC), events);

	@Test
	void layerAboveThePlaybackOnlyInPartyMode() {
		party.setIntensity(List.of(3L), 0.5);
		party.pressButton(5);

		var layer = party.layer(Instant.now(), Map.of(3L, SPOT));
		assertThat(layer.priority()).isEqualTo(60);
		assertThat(layer.fixtures().get(3L).values()).containsEntry(DIMMER, 0.5).containsEntry(GOBO, 0.4);
		assertThat(party.activeIn(OperatingMode.PARTY)).isTrue();
		assertThat(party.activeIn(OperatingMode.SHOW)).isFalse();
		assertThat(party.status().buttons()).singleElement().satisfies(b -> assertThat(b.on()).isTrue());
	}

	@Test
	void commandsNeedPartyMode() {
		currentMode = OperatingMode.SETUP;

		assertThatThrownBy(() -> party.setPalette(List.of(3L), Palette.RED)).isInstanceOf(ConflictException.class);
		assertThatThrownBy(party::tap).isInstanceOf(ConflictException.class);
	}

	@Test
	void statusReportsTempoAndTouchedFixtures() {
		party.setBpm(128);
		party.setPalette(List.of(3L), Palette.CYAN);

		var status = party.status();
		assertThat(status.bpm()).isEqualTo(128);
		assertThat(status.beatLength()).isEqualTo(Duration.ofNanos(468_750_000));
		assertThat(status.fixtures().get(3L).palette()).contains(Palette.CYAN);
		assertThat(status.fixtures().get(3L).intensity()).isEqualTo(1.0);
	}

	@Test
	void anotherProjectStartsDarkAndDeletedButtonsGoOff() {
		party.pressButton(5);
		stored.clear();
		events.publish(StateTopic.PARTY_BUTTONS);
		assertThat(party.status().buttons()).isEmpty();

		party.setIntensity(List.of(3L), 1);
		activeProject = 2;
		events.publish(StateTopic.PROJECTS);
		assertThat(party.status().fixtures()).isEmpty();
	}

	private ControlModeUseCase mode() {
		return new ControlModeUseCase() {
			@Override
			public OperatingMode mode() {
				return currentMode;
			}

			@Override
			public OperatingMode setMode(OperatingMode mode) {
				currentMode = mode;
				return mode;
			}
		};
	}

	private ProjectRepository projects() {
		return new ProjectRepository() {
			public List<Project> findAll() {
				return List.of(project(1), project(2));
			}

			private Project project(long id) {
				return new Project(id, new ProjectSpec("P" + id, Optional.empty(), Duration.ZERO, Duration.ZERO),
						id == activeProject, Instant.EPOCH, Instant.EPOCH);
			}

			public Optional<Project> findById(long id) {
				return findAll().stream().filter(p -> p.id() == id).findFirst();
			}

			public Project create(ProjectSpec spec, Instant now) {
				throw new UnsupportedOperationException();
			}

			public Project update(long id, ProjectSpec spec, Instant now) {
				throw new UnsupportedOperationException();
			}

			public void delete(long id) {
				throw new UnsupportedOperationException();
			}

			public void activate(long id) {
				throw new UnsupportedOperationException();
			}
		};
	}

	private PartyButtonRepository buttons() {
		return new PartyButtonRepository() {
			public List<PartyButton> findByProject(long projectId) {
				return stored.stream().filter(b -> b.projectId() == projectId).toList();
			}

			public Optional<PartyButton> findById(long id) {
				return stored.stream().filter(b -> b.id() == id).findFirst();
			}

			public PartyButton create(long projectId, String name, KeyMode mode, Map<Long, FixtureValues> values) {
				throw new UnsupportedOperationException();
			}

			public PartyButton update(long id, String name, KeyMode mode) {
				throw new UnsupportedOperationException();
			}

			public PartyButton replaceValues(long id, Map<Long, FixtureValues> values) {
				throw new UnsupportedOperationException();
			}

			public void reorder(List<Long> ids) {
				throw new UnsupportedOperationException();
			}

			public void delete(long id) {
				throw new UnsupportedOperationException();
			}
		};
	}
}
