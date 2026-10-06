package de.vinzens.lumen.application.party;

import de.vinzens.lumen.application.ConflictException;
import de.vinzens.lumen.application.TestShow;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.output.port.in.ControlModeUseCase;
import de.vinzens.lumen.application.party.port.out.PartyMatrixRepository;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelDefinition;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.library.Mode;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.output.OperatingMode;
import de.vinzens.lumen.domain.party.PartyCell;
import de.vinzens.lumen.domain.party.PartyMatrix;
import de.vinzens.lumen.domain.project.Project;
import de.vinzens.lumen.domain.project.ProjectSpec;
import de.vinzens.lumen.domain.scene.SceneComposition;
import de.vinzens.lumen.domain.scene.SceneNumber;
import de.vinzens.lumen.domain.scene.SceneSpec;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PartyServiceTest {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	private static final Attribute GOBO = Attribute.of(ChannelFunction.GOBO);
	private static final Mode SPOT = new Mode("spot", List.of(
			new ChannelDefinition(ChannelFunction.DIMMER, Optional.empty(), 8, 0, false, List.of(), Optional.empty()),
			new ChannelDefinition(ChannelFunction.GOBO, Optional.empty(), 8, 0, false, List.of(), Optional.empty())));
	private static final Instant NOW = Instant.parse("2026-10-06T22:00:00Z");

	private OperatingMode currentMode = OperatingMode.PARTY;
	private long activeProject = 1;
	private final Map<Long, PartyMatrix> stored = new HashMap<>();
	private final StateEvents events = new StateEvents();
	private final TestShow show = new TestShow();

	@Test
	void fieldsPlayScenesAndCompositionsAboveThePlaybackOnlyInPartyMode() {
		var gobo = show.fixed(1, "Gobo", Map.of(3L, new FixtureValues(Map.of(GOBO, 0.4))));
		var half = show.fixed(1, "Half", Map.of(3L, new FixtureValues(Map.of(DIMMER, 0.5))));
		var scene = show.scenes.create(1, new SceneSpec(SceneNumber.of(1), "Half", Optional.empty(),
				Optional.empty()), List.of(SceneComposition.of(half.id())), List.of());
		stored.put(1L, new PartyMatrix(2, 2, Set.of(1), List.of(PartyCell.composition(0, 0, gobo.id()),
				PartyCell.scene(1, 0, scene.id()), PartyCell.text(0, 1, "Looks"))));
		var party = party();

		party.press(0, 0);
		party.press(1, 0);

		var layer = party.layer(NOW, Map.of(3L, SPOT));
		assertThat(layer.priority()).isEqualTo(60);
		assertThat(layer.fixtures().get(3L).values()).containsEntry(DIMMER, 0.5).containsEntry(GOBO, 0.4);
		assertThat(party.activeIn(OperatingMode.PARTY)).isTrue();
		assertThat(party.activeIn(OperatingMode.SHOW)).isFalse();
		assertThat(party.status().on()).containsExactlyInAnyOrder(new PartyCell.Position(0, 0),
				new PartyCell.Position(1, 0));
	}

	@Test
	void commandsNeedPartyMode() {
		var party = party();
		currentMode = OperatingMode.SETUP;

		assertThatThrownBy(() -> party.press(0, 0)).isInstanceOf(ConflictException.class);
		assertThatThrownBy(party::clear).isInstanceOf(ConflictException.class);
	}

	@Test
	void anotherProjectStartsWithEverythingOffAndRemovedFieldsGoOff() {
		var gobo = show.fixed(1, "Gobo", Map.of(3L, new FixtureValues(Map.of(GOBO, 0.4))));
		stored.put(1L, new PartyMatrix(1, 2, Set.of(), List.of(PartyCell.composition(0, 0, gobo.id()),
				PartyCell.composition(0, 1, gobo.id()))));
		var party = party();
		party.press(0, 0);
		party.press(0, 1);

		stored.put(1L, new PartyMatrix(1, 2, Set.of(), List.of(PartyCell.composition(0, 0, gobo.id()))));
		events.publish(StateTopic.PARTY_MATRIX);
		assertThat(party.status().on()).containsExactly(new PartyCell.Position(0, 0));

		activeProject = 2;
		events.publish(StateTopic.PROJECTS);
		assertThat(party.status().on()).isEmpty();
	}

	private PartyService party() {
		return new PartyService(projects(), matrices(), show.resolver(), mode(), Clock.fixed(NOW, ZoneOffset.UTC),
				events);
	}

	private PartyMatrixRepository matrices() {
		return new PartyMatrixRepository() {
			public PartyMatrix find(long projectId) {
				return stored.getOrDefault(projectId, PartyMatrix.EMPTY);
			}

			public void save(long projectId, PartyMatrix matrix) {
				stored.put(projectId, matrix);
			}
		};
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
}
