package de.vinzens.lumen.application.quicksetup;

import de.vinzens.lumen.application.ConflictException;
import de.vinzens.lumen.application.FakeProgrammer;
import de.vinzens.lumen.application.TestShow;
import de.vinzens.lumen.application.composition.CompositionService;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.library.port.in.QueryFixtureLibraryUseCase;
import de.vinzens.lumen.application.move.MoveService;
import de.vinzens.lumen.application.party.PartyMatrixService;
import de.vinzens.lumen.application.party.port.out.PartyMatrixRepository;
import de.vinzens.lumen.application.project.port.out.FixtureRepository;
import de.vinzens.lumen.application.project.port.out.GroupRepository;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.application.quicksetup.port.in.QuickSetupUseCase.Request;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.preset.PresetSpec;
import de.vinzens.lumen.domain.preset.Preset;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.library.ChannelRange;
import de.vinzens.lumen.domain.library.AttributeGroup;
import de.vinzens.lumen.application.preset.port.out.PresetRepository;
import de.vinzens.lumen.domain.library.ChannelDefinition;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.library.FixtureCategory;
import de.vinzens.lumen.domain.library.FixtureType;
import de.vinzens.lumen.domain.library.LibraryIssue;
import de.vinzens.lumen.domain.library.Mode;
import de.vinzens.lumen.domain.library.PhysicalProperties;
import de.vinzens.lumen.domain.look.NamedLook;
import de.vinzens.lumen.domain.party.PartyCell;
import de.vinzens.lumen.domain.party.PartyFader;
import de.vinzens.lumen.domain.party.PartyMatrix;
import de.vinzens.lumen.domain.project.FixtureSpec;
import de.vinzens.lumen.domain.project.Group;
import de.vinzens.lumen.domain.project.GroupSpec;
import de.vinzens.lumen.domain.project.PatchedFixture;
import de.vinzens.lumen.domain.project.Project;
import de.vinzens.lumen.domain.project.ProjectSpec;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** Quick setup on a project with three RGBW PARs (1–3), a dimmer (4) and a moving head (5). */
class QuickSetupServiceTest {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	private static final Attribute RED = Attribute.of(ChannelFunction.RED);
	private static final Attribute GREEN = Attribute.of(ChannelFunction.GREEN);
	private static final Attribute WHITE = Attribute.of(ChannelFunction.WHITE);
	private static final FixtureType PAR = type("par", FixtureCategory.PAR, ChannelFunction.DIMMER,
			ChannelFunction.RED, ChannelFunction.GREEN, ChannelFunction.BLUE, ChannelFunction.WHITE);
	private static final FixtureType DIMMER_TYPE = type("dimmer", FixtureCategory.DIMMER, ChannelFunction.DIMMER);
	private static final FixtureType HEAD = type("head", FixtureCategory.MOVING_HEAD, ChannelFunction.PAN,
			ChannelFunction.TILT, ChannelFunction.RED, ChannelFunction.GREEN, ChannelFunction.BLUE);
	/** A spot with a color wheel: white, red, blue, green, yellow and a rotation. */
	private static final FixtureType SPOT = new FixtureType("spot", "Test", "spot", FixtureCategory.MOVING_HEAD,
			PhysicalProperties.NONE, List.of(new Mode("std", List.of(
					new ChannelDefinition(ChannelFunction.DIMMER, Optional.empty(), 8, 0, false, List.of(),
							Optional.empty()),
					new ChannelDefinition(ChannelFunction.COLOR_WHEEL, Optional.empty(), 8, 0, false, List.of(
							slot(0, 9, "White", "#ffffff"), slot(10, 19, "Red", "#ff0000"),
							slot(20, 29, "Blue", "#0033ff"), slot(30, 39, "Green", "#00ff44"),
							slot(40, 49, "Yellow", "#ffdd00"),
							new ChannelRange(50, 255, "Rotation", Optional.empty())), Optional.empty())))));
	private static final Attribute WHEEL = Attribute.of(ChannelFunction.COLOR_WHEEL);

	private final List<PatchedFixture> patched = List.of(fixture(1, "PAR 1", "par"), fixture(2, "PAR 2", "par"),
			fixture(3, "PAR 3", "par"), fixture(4, "Dimmer", "dimmer"), fixture(5, "Head", "head"),
			fixture(6, "Spot", "spot"));
	private final TestShow show = new TestShow();
	private final StateEvents events = new StateEvents();
	private PartyMatrix stored = PartyMatrix.EMPTY;
	private final PartyMatrixRepository matrices = new PartyMatrixRepository() {
		public PartyMatrix find(long projectId) {
			return stored;
		}

		public void save(long projectId, PartyMatrix matrix) {
			stored = matrix;
		}
	};
	private final CompositionService compositions = new CompositionService(projects(), show.compositions, show.looks,
			show.scenes, fixtures(), groups(), library(), new FakeProgrammer(), events);
	private final MoveService moves = new MoveService(projects(), show.moves, show.figures, fixtures(), show.scenes,
			matrices, library(), new FakeProgrammer(), events);
	private final PartyMatrixService partyMatrices = new PartyMatrixService(projects(), show.scenes,
			show.compositions, show.moves, fixtures(), matrices, events);
	private final List<Preset> storedPresets = new ArrayList<>();
	private final PresetRepository presets = new PresetRepository() {
		public List<Preset> findByProject(long projectId) {
			return List.copyOf(storedPresets);
		}

		public Optional<Preset> findById(long id) {
			return storedPresets.stream().filter(p -> p.id() == id).findFirst();
		}

		public Preset create(long projectId, PresetSpec spec, Map<Long, FixtureValues> values) {
			var preset = new Preset(storedPresets.size() + 1, projectId, spec, values);
			storedPresets.add(preset);
			return preset;
		}

		public Preset updateSpec(long id, PresetSpec spec) {
			throw new UnsupportedOperationException();
		}

		public Preset replaceValues(long id, Map<Long, FixtureValues> values) {
			throw new UnsupportedOperationException();
		}

		public void delete(long id) {
			throw new UnsupportedOperationException();
		}
	};
	private final QuickSetupService quickSetup = new QuickSetupService(projects(), fixtures(), library(), show.looks,
			compositions, show.figures, moves, partyMatrices, presets, events);

	@Test
	void parsGetIntensitiesColorsAndARowOfButtonsAndOneOfGradients() {
		var result = quickSetup.setUp(1, new Request(List.of(1L, 2L, 3L), Optional.empty(), true, true, true,
				true, true));

		assertThat(result.name()).isEqualTo("PAR");
		assertThat(result.looks()).extracting(look -> look.spec().name()).containsExactly("PAR – 0 %", "PAR – 50 %",
				"PAR – 100 %", "PAR – Red", "PAR – Orange", "PAR – Green", "PAR – Blue", "PAR – Violet");
		assertThat(result.looks()).allMatch(look -> look.spec().isShared());
		var red = look(result.looks(), "PAR – Red").look().values().get(2L);
		assertThat(red.get(RED)).contains(1.0);
		assertThat(red.get(GREEN)).contains(0.0);
		assertThat(red.get(WHITE)).as("white off: the color stays pure").contains(0.0);
		assertThat(result.presets()).extracting(preset -> preset.spec().name()).containsExactly("PAR – Red",
				"PAR – Orange", "PAR – Green", "PAR – Blue", "PAR – Violet");
		var redPreset = result.presets().getFirst();
		assertThat(redPreset.spec().group()).isEqualTo(AttributeGroup.COLOR);
		assertThat(look(result.looks(), "PAR – Red").look().presets().presetOf(2L, AttributeGroup.COLOR))
				.as("the look follows the palette").contains(redPreset.id());
		assertThat(look(result.looks(), "PAR – 50 %").look().values().get(3L).get(DIMMER)).contains(0.5);
		assertThat(result.compositions()).hasSize(8)
				.allMatch(composition -> composition.steps().size() == 1);
		assertThat(result.moves()).isEmpty();
		assertThat(result.notes()).containsExactly("No moves: none of the fixtures has pan and tilt");

		assertThat(result.partyRows()).containsExactly(0, 1);
		assertThat(stored.columns()).isEqualTo(9);
		var buttons = stored.cells().stream().filter(cell -> cell.row() == 0).toList();
		assertThat(buttons).extracting(cell -> cell.label().orElse("")).containsExactly("PAR", "0 %", "50 %",
				"100 %", "Red", "Orange", "Green", "Blue", "Violet");
		assertThat(buttons.get(1).group()).contains("PAR – intensity");
		assertThat(buttons.get(4).group()).contains("PAR – color");
		assertThat(buttons.get(4).compositionId()).contains(result.compositions().get(3).id());
		var dimmerGradient = stored.cell(1, 1).orElseThrow();
		assertThat(dimmerGradient.span()).isEqualTo(3);
		assertThat(dimmerGradient.fader().orElseThrow())
				.isEqualTo(new PartyFader(PartyFader.Kind.DIMMER, List.of(1L, 2L, 3L)));
		assertThat(dimmerGradient.group()).contains("PAR – intensity");
		var colorGradient = stored.cell(1, 4).orElseThrow();
		assertThat(colorGradient.span()).isEqualTo(5);
		assertThat(colorGradient.group()).contains("PAR – color");
	}

	@Test
	void aMovingHeadGetsMovesAndColorsADimmerOnlyIntensities() {
		var result = quickSetup.setUp(1, new Request(List.of(4L, 5L), Optional.of("Mixed"), true, true, true, true,
				false));

		assertThat(result.moves()).extracting(move -> move.spec().name())
				.containsExactly("Mixed – Line", "Mixed – Circle", "Mixed – Eight");
		assertThat(result.moves()).allMatch(move -> move.spec().fixtureIds().equals(List.of(5L)));
		assertThat(show.figures.findByProject(1)).extracting(figure -> figure.spec().name())
				.containsExactlyInAnyOrder("Line", "Circle", "Eight");
		assertThat(look(result.looks(), "Mixed – Red").look().values()).containsOnlyKeys(5L);
		assertThat(look(result.looks(), "Mixed – 100 %").look().values()).containsOnlyKeys(4L, 5L);
		assertThat(stored.cells()).hasSize(1 + 3 + 5 + 3);
		assertThat(stored.columns()).isEqualTo(12);
		assertThat(stored.cell(0, 11).orElseThrow().moveId()).contains(result.moves().get(2).id());

		quickSetup.setUp(1, new Request(List.of(5L), Optional.of("Mixed"), false, false, true, true, false));

		assertThat(show.figures.findByProject(1)).as("the figures are used again").hasSize(3);
		assertThat(stored.cell(1, 1).orElseThrow().group()).as("a group of its own").contains("Mixed 2 – move");
		assertThat(stored.separators()).containsExactly(1);
	}

	@Test
	void aColorWheelGetsTheClosestSlotsAndItsOtherSlotsBecomePresets() {
		var result = quickSetup.setUp(1, new Request(List.of(6L, 1L), Optional.of("Mix"), false, true, false, true,
				true));

		var red = look(result.looks(), "Mix – Red").look().values();
		assertThat(red.get(6L).get(WHEEL).orElseThrow()).isCloseTo(14.5 / 255, within(1e-9));
		assertThat(red.get(1L).get(RED)).contains(1.0);
		assertThat(look(result.looks(), "Mix – Violet").look().values()).as("no violet on the wheel")
				.containsOnlyKeys(1L);
		assertThat(result.presets()).extracting(preset -> preset.spec().name())
				.containsExactly("Mix – Red", "Mix – Orange", "Mix – Green", "Mix – Blue", "Mix – Violet",
						"Mix – White", "Mix – Yellow");
		assertThat(result.presets().get(6).values()).containsOnlyKeys(6L);
		assertThat(stored.cell(1, 4).orElseThrow().fader().orElseThrow().fixtureIds())
				.as("a wheel cannot run along a gradient").containsExactly(1L);
	}

	@Test
	void aWheelWithoutColoredSlotsGetsNoColors() {
		var result = quickSetup.setUp(1, new Request(List.of(4L), Optional.empty(), false, true, false, false,
				false));

		assertThat(result.looks()).isEmpty();
		assertThat(result.presets()).isEmpty();
		assertThat(result.notes()).containsExactly(
				"No colors: none of the fixtures mixes colors or has a color wheel with colored slots");
	}

	@Test
	void theRowsGoBelowTheLastUsedRowAndTheMatrixMustHaveRoom() {
		stored = new PartyMatrix(12, 4, Set.of(), List.of(PartyCell.text(9, 0, "Taken")));

		var result = quickSetup.setUp(1, new Request(List.of(4L), Optional.empty(), true, false, false, true,
				true));
		assertThat(result.partyRows()).containsExactly(10, 11);
		assertThat(result.notes()).containsExactly(
				"No color gradient: it needs red, green and blue or cyan, magenta and yellow");
		assertThat(stored.cell(11, 4)).as("no color gradient without colors").isEmpty();

		assertThatThrownBy(() -> quickSetup.setUp(1, new Request(List.of(4L), Optional.empty(), true, false, false,
				true, false)))
				.isInstanceOf(ConflictException.class)
				.hasMessageContaining("no room");
	}

	@Test
	void withoutPartyRowsOnlyLooksAreMadeAndTheMatrixStays() {
		var result = quickSetup.setUp(1, new Request(List.of(1L), Optional.empty(), true, false, false, false,
				false));

		assertThat(result.name()).isEqualTo("PAR 1");
		assertThat(result.looks()).hasSize(3);
		assertThat(result.partyRows()).isEmpty();
		assertThat(stored).isEqualTo(PartyMatrix.EMPTY);
	}

	@Test
	void unknownFixturesAreRefused() {
		assertThatThrownBy(() -> quickSetup.setUp(1, new Request(List.of(99L), Optional.empty(), true, false, false,
				false, false)))
				.hasMessageContaining("do not exist");
	}

	@Test
	void theNameIsWhatTheFixturesHaveInCommon() {
		assertThat(QuickSetupService.baseName(List.of(fixture(1, "Front PAR 1", "par"),
				fixture(2, "Front PAR 2", "par")))).isEqualTo("Front PAR");
		assertThat(QuickSetupService.baseName(List.of(fixture(1, "Left", "par"), fixture(2, "Right", "par"))))
				.isEqualTo("Left");
	}

	private static NamedLook look(List<NamedLook> looks, String name) {
		return looks.stream().filter(look -> look.spec().name().equals(name)).findFirst().orElseThrow();
	}

	private static ChannelRange slot(int from, int to, String label, String color) {
		return new ChannelRange(from, to, label, Optional.of(color));
	}

	private static FixtureType type(String id, FixtureCategory category, ChannelFunction... functions) {
		var channels = Arrays.stream(functions).map(function -> new ChannelDefinition(function, Optional.empty(), 8,
				0, false, List.of(), Optional.empty())).toList();
		return new FixtureType(id, "Test", id, category, PhysicalProperties.NONE, List.of(new Mode("std", channels)));
	}

	private static PatchedFixture fixture(long id, String name, String type) {
		return new PatchedFixture(id, 1, new FixtureSpec(name, Optional.empty(), type, "std", 1, (int) id * 10,
				Optional.empty(), 0));
	}

	// --- in-memory project -------------------------------------------------------------------------------------

	private static ProjectRepository projects() {
		var project = new Project(1, new ProjectSpec("Show", Optional.empty(), Duration.ZERO, Duration.ZERO), true,
				Instant.EPOCH, Instant.EPOCH);
		return new ProjectRepository() {
			public List<Project> findAll() {
				return List.of(project);
			}

			public Optional<Project> findById(long id) {
				return Optional.of(project).filter(p -> p.id() == id);
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

	private FixtureRepository fixtures() {
		return new FixtureRepository() {
			public List<PatchedFixture> findByProject(long projectId) {
				return patched;
			}

			public Optional<PatchedFixture> findById(long id) {
				return patched.stream().filter(f -> f.id() == id).findFirst();
			}

			public PatchedFixture create(long projectId, FixtureSpec spec) {
				throw new UnsupportedOperationException();
			}

			public PatchedFixture update(long id, FixtureSpec spec) {
				throw new UnsupportedOperationException();
			}

			public void delete(long id) {
				throw new UnsupportedOperationException();
			}

			public int countByUniverse(long universeId) {
				return patched.size();
			}
		};
	}

	private static GroupRepository groups() {
		return new GroupRepository() {
			public List<Group> findByProject(long projectId) {
				return List.of();
			}

			public Optional<Group> findById(long id) {
				return Optional.empty();
			}

			public Group create(long projectId, GroupSpec spec) {
				throw new UnsupportedOperationException();
			}

			public Group update(long id, GroupSpec spec) {
				throw new UnsupportedOperationException();
			}

			public void delete(long id) {
				throw new UnsupportedOperationException();
			}
		};
	}

	private static QueryFixtureLibraryUseCase library() {
		var types = new ArrayList<>(List.of(PAR, DIMMER_TYPE, HEAD, SPOT));
		return new QueryFixtureLibraryUseCase() {
			public List<FixtureType> fixtureTypes() {
				return types;
			}

			public Optional<FixtureType> fixtureType(String id) {
				return types.stream().filter(type -> type.id().equals(id)).findFirst();
			}

			public List<LibraryIssue> issues() {
				return List.of();
			}
		};
	}
}
