package de.vinzens.lumen.application.party;

import de.vinzens.lumen.application.ConflictException;
import de.vinzens.lumen.application.composition.CompositionResolver;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.output.OutputLayerSource;
import de.vinzens.lumen.application.output.port.in.ControlModeUseCase;
import de.vinzens.lumen.application.party.port.in.ControlPartyUseCase;
import de.vinzens.lumen.application.party.port.out.PartyMatrixRepository;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.domain.composition.ActiveComposition;
import de.vinzens.lumen.domain.composition.FixtureAttributes;
import de.vinzens.lumen.domain.library.Mode;
import de.vinzens.lumen.domain.output.LayerOutput;
import de.vinzens.lumen.domain.output.OperatingMode;
import de.vinzens.lumen.domain.party.PartySwitchboard;
import de.vinzens.lumen.domain.party.PartySwitchboard.PlayableCell;
import de.vinzens.lumen.domain.project.Project;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Runs party mode: the fields of the party matrix of the active project that are on form an output layer above the
 * playback, only in party mode. Fields stay on while switching modes; another project starts with all fields off.
 */
public class PartyService implements ControlPartyUseCase, OutputLayerSource {

	static final int PARTY_PRIORITY = 60;
	private static final Set<StateTopic> RELOAD_ON = Set.of(StateTopic.PROJECTS, StateTopic.PARTY_MATRIX,
			StateTopic.SCENES, StateTopic.COMPOSITIONS, StateTopic.LOOKS, StateTopic.MOVES);

	private final ProjectRepository projects;
	private final PartyMatrixRepository matrices;
	private final CompositionResolver resolver;
	private final ControlModeUseCase mode;
	private final Clock clock;
	private final StateEvents events;

	private PartySwitchboard board = new PartySwitchboard();
	private Long projectId;

	public PartyService(ProjectRepository projects, PartyMatrixRepository matrices, CompositionResolver resolver,
			ControlModeUseCase mode, Clock clock, StateEvents events) {
		this.projects = Objects.requireNonNull(projects, "projects");
		this.matrices = Objects.requireNonNull(matrices, "matrices");
		this.resolver = Objects.requireNonNull(resolver, "resolver");
		this.mode = Objects.requireNonNull(mode, "mode");
		this.clock = Objects.requireNonNull(clock, "clock");
		this.events = Objects.requireNonNull(events, "events");
		events.subscribe(topic -> {
			if (RELOAD_ON.contains(topic)) {
				reload();
			}
		});
		reload();
	}

	/** Loads the matrix of the active project with what its fields play (read outside the lock). */
	public void reload() {
		var active = projects.findAll().stream().filter(Project::active).findFirst();
		var playable = active.map(project -> playable(project.id())).orElse(List.of());
		synchronized (this) {
			var id = active.map(Project::id).orElse(null);
			if (!Objects.equals(id, projectId)) {
				board = new PartySwitchboard();
				projectId = id;
			}
			board.configure(playable);
		}
		events.publish(StateTopic.PARTY);
	}

	private List<PlayableCell> playable(long projectId) {
		var scenes = resolver.scenes(projectId);
		var compositions = resolver.compositions(projectId);
		var moves = resolver.moves(projectId);
		return matrices.find(projectId).cells().stream()
				.map(cell -> new PlayableCell(cell, cell.sceneId().map(id -> scenes.getOrDefault(id, List.of()))
						.or(() -> cell.compositionId().map(id -> compositionList(compositions.get(id))))
						.or(() -> cell.moveId().map(id -> compositionList(moves.get(id))))
						.orElse(List.of())))
				.toList();
	}

	private static List<ActiveComposition> compositionList(ActiveComposition composition) {
		return composition == null ? List.of() : List.of(composition);
	}

	@Override
	public synchronized PartyStatus status() {
		return new PartyStatus(board.onPositions());
	}

	@Override
	public void press(int row, int column) {
		synchronized (this) {
			requirePartyMode();
			board.press(row, column, clock.instant());
		}
		events.publish(StateTopic.PARTY);
	}

	@Override
	public void release(int row, int column) {
		synchronized (this) {
			board.release(row, column);
		}
		events.publish(StateTopic.PARTY);
	}

	@Override
	public void clear() {
		synchronized (this) {
			requirePartyMode();
			board.clear();
		}
		events.publish(StateTopic.PARTY);
	}

	@Override
	public boolean activeIn(OperatingMode current) {
		return current == OperatingMode.PARTY;
	}

	@Override
	public synchronized LayerOutput layer(Instant now, Map<Long, Mode> modes) {
		return new LayerOutput(PARTY_PRIORITY, board.output(now, FixtureAttributes.of(modes)));
	}

	private void requirePartyMode() {
		if (mode.mode() != OperatingMode.PARTY) {
			throw new ConflictException("Switch to party mode first");
		}
	}
}
