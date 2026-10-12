package de.vinzens.lumen.application.quicksetup;

import de.vinzens.lumen.application.ConflictException;
import de.vinzens.lumen.application.NotFoundException;
import de.vinzens.lumen.application.composition.PatchAttributes;
import de.vinzens.lumen.application.composition.port.in.ManageCompositionsUseCase;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.library.port.in.QueryFixtureLibraryUseCase;
import de.vinzens.lumen.application.look.port.out.LookRepository;
import de.vinzens.lumen.application.move.port.in.ManageMovesUseCase;
import de.vinzens.lumen.application.move.port.out.FigureRepository;
import de.vinzens.lumen.application.party.port.in.ManagePartyMatrixUseCase;
import de.vinzens.lumen.application.preset.port.out.PresetRepository;
import de.vinzens.lumen.domain.composition.Color;
import de.vinzens.lumen.domain.composition.FixtureColors;
import de.vinzens.lumen.domain.library.AttributeGroup;
import de.vinzens.lumen.domain.library.Mode;
import de.vinzens.lumen.domain.output.PresetRefs;
import de.vinzens.lumen.domain.preset.Preset;
import de.vinzens.lumen.domain.preset.PresetSpec;
import de.vinzens.lumen.application.project.port.out.FixtureRepository;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.application.quicksetup.port.in.QuickSetupUseCase;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.Folders;
import de.vinzens.lumen.domain.composition.Composition;
import de.vinzens.lumen.domain.composition.CompositionSpec;
import de.vinzens.lumen.domain.composition.CompositionStep;
import de.vinzens.lumen.domain.composition.IntensityMode;
import de.vinzens.lumen.domain.composition.KeyMode;
import de.vinzens.lumen.domain.composition.PlayMode;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.look.Look;
import de.vinzens.lumen.domain.look.LookSpec;
import de.vinzens.lumen.domain.look.NamedLook;
import de.vinzens.lumen.domain.move.Figure;
import de.vinzens.lumen.domain.move.FigurePoint;
import de.vinzens.lumen.domain.move.FigureSpec;
import de.vinzens.lumen.domain.move.Move;
import de.vinzens.lumen.domain.move.MoveFixture;
import de.vinzens.lumen.domain.move.MoveSpec;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.party.PartyCell;
import de.vinzens.lumen.domain.party.PartyFader;
import de.vinzens.lumen.domain.party.PartyMatrix;
import de.vinzens.lumen.domain.party.PartyPage;
import de.vinzens.lumen.domain.project.PatchedFixture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class QuickSetupService implements QuickSetupUseCase {

	/** Party compositions come in quickly and then stay. */
	private static final double FADE_SECONDS = 0.3;
	private static final double HOLD_SECONDS = 1;
	private static final int MAX_LABEL = 60;
	/** Points of the circle and the eight. */
	private static final int ROUND_POINTS = 24;
	/** A pan/tilt pad is two columns wide and two rows high. */
	private static final int PAD_SIZE = 2;

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	private static final Attribute PAN = Attribute.of(ChannelFunction.PAN);
	private static final Attribute TILT = Attribute.of(ChannelFunction.TILT);

	private static final List<Level> LEVELS = List.of(new Level("0 %", 0), new Level("50 %", 0.5),
			new Level("100 %", 1));
	private static final List<NamedColor> COLORS = List.of(
			new NamedColor("Red", 1, 0, 0),
			new NamedColor("Orange", 1, 0.3, 0),
			new NamedColor("Green", 0, 1, 0),
			new NamedColor("Blue", 0, 0, 1),
			new NamedColor("Violet", 0.55, 0, 1));
	private static final List<FigureSpec> FIGURES = List.of(line(), circle(), eight());

	private final ProjectRepository projects;
	private final FixtureRepository fixtures;
	private final QueryFixtureLibraryUseCase library;
	private final LookRepository looks;
	private final ManageCompositionsUseCase compositions;
	private final FigureRepository figures;
	private final ManageMovesUseCase moves;
	private final ManagePartyMatrixUseCase partyMatrices;
	private final PresetRepository presets;
	private final StateEvents events;

	public QuickSetupService(ProjectRepository projects, FixtureRepository fixtures, QueryFixtureLibraryUseCase library,
			LookRepository looks, ManageCompositionsUseCase compositions, FigureRepository figures,
			ManageMovesUseCase moves, ManagePartyMatrixUseCase partyMatrices, PresetRepository presets,
			StateEvents events) {
		this.projects = Objects.requireNonNull(projects, "projects");
		this.fixtures = Objects.requireNonNull(fixtures, "fixtures");
		this.library = Objects.requireNonNull(library, "library");
		this.looks = Objects.requireNonNull(looks, "looks");
		this.compositions = Objects.requireNonNull(compositions, "compositions");
		this.figures = Objects.requireNonNull(figures, "figures");
		this.moves = Objects.requireNonNull(moves, "moves");
		this.partyMatrices = Objects.requireNonNull(partyMatrices, "partyMatrices");
		this.presets = Objects.requireNonNull(presets, "presets");
		this.events = Objects.requireNonNull(events, "events");
	}

	@Override
	public Result setUp(long projectId, Request request) {
		if (projects.findById(projectId).isEmpty()) {
			throw new NotFoundException("Project " + projectId + " does not exist");
		}
		var patched = fixtures.findByProject(projectId);
		var byId = patched.stream().collect(Collectors.toMap(PatchedFixture::id, Function.identity()));
		var unknown = request.fixtureIds().stream().filter(id -> !byId.containsKey(id)).toList();
		if (!unknown.isEmpty()) {
			throw new DomainValidationException(List.of("fixtures " + unknown + " do not exist in this project"));
		}
		var chosen = request.fixtureIds().stream().map(byId::get).toList();
		var attributes = PatchAttributes.of(patched, library);
		var name = request.name().orElseGet(() -> baseName(chosen));
		var notes = new ArrayList<String>();

		var fixtureColors = new LinkedHashMap<Long, FixtureColors>();
		for (var fixture : chosen) {
			modeOf(fixture).map(FixtureColors::of).filter(FixtureColors::canShowColors)
					.ifPresent(colors -> fixtureColors.put(fixture.id(), colors));
		}
		// a gradient runs smoothly, so it needs mixed colors, not a wheel
		var mixingFixtures = fixtureColors.entrySet().stream()
				.filter(entry -> entry.getValue().mixing() != FixtureColors.Mixing.WHEEL)
				.map(Map.Entry::getKey)
				.toList();
		var movingFixtures = request.fixtureIds().stream()
				.filter(id -> attributes.has(id, PAN) && attributes.has(id, TILT))
				.toList();
		if (request.colors() && fixtureColors.isEmpty()) {
			notes.add("No colors: none of the fixtures mixes colors or has a color wheel with colored slots");
		} else if (request.gradients() && mixingFixtures.isEmpty()) {
			notes.add("No color gradient: it needs red, green and blue or cyan, magenta and yellow");
		}
		if (request.moves() && movingFixtures.isEmpty()) {
			notes.add("No moves: none of the fixtures has pan and tilt");
		}

		var madeLooks = new ArrayList<NamedLook>();
		var intensityButtons = new ArrayList<Button>();
		var colorButtons = new ArrayList<Button>();
		var moveButtons = new ArrayList<Button>();
		var madeCompositions = new ArrayList<Composition>();
		if (request.intensity()) {
			for (var level : LEVELS) {
				var values = new HashMap<Long, FixtureValues>();
				request.fixtureIds().forEach(id -> values.put(id, new FixtureValues(Map.of(DIMMER, level.value()))));
				var made = lookWithComposition(projectId, name + " – " + level.label(), Look.of(values));
				madeLooks.add(made.look());
				madeCompositions.add(made.composition());
				var grey = hex(0.15 + 0.35 * level.value(), 0.15 + 0.35 * level.value(), 0.15 + 0.35 * level.value());
				intensityButtons.add(Button.composition(level.label(), made.composition().id(), grey,
						hex(0.4 + 0.6 * level.value(), 0.4 + 0.6 * level.value(), 0.4 + 0.6 * level.value())));
			}
		}
		var madePresets = new ArrayList<Preset>();
		if (request.colors() && !fixtureColors.isEmpty()) {
			var palette = palette(name, fixtureColors);
			var room = Preset.MAX_PER_PROJECT - presets.findByProject(projectId).size();
			var withPresets = palette.size() <= room;
			if (!withPresets) {
				notes.add("No color palette: the project already has " + (Preset.MAX_PER_PROJECT - room)
						+ " presets, at most " + Preset.MAX_PER_PROJECT + " are possible");
			}
			for (var entry : palette) {
				var preset = withPresets
						? Optional.of(presets.create(projectId, new PresetSpec(entry.name(), AttributeGroup.COLOR),
								entry.values()))
						: Optional.<Preset>empty();
				preset.ifPresent(madePresets::add);
				if (entry.color().isEmpty()) {
					continue;
				}
				var color = entry.color().get();
				var refs = PresetRefs.NONE;
				for (var fixtureId : entry.values().keySet()) {
					refs = preset.isPresent() ? refs.with(fixtureId, AttributeGroup.COLOR, preset.get().id()) : refs;
				}
				var made = lookWithComposition(projectId, name + " – " + color.name(),
						new Look(entry.values(), refs));
				madeLooks.add(made.look());
				madeCompositions.add(made.composition());
				colorButtons.add(Button.composition(color.name(), made.composition().id(),
						hex(color.red() * 0.35, color.green() * 0.35, color.blue() * 0.35),
						hex(color.red(), color.green(), color.blue())));
			}
			if (!madePresets.isEmpty()) {
				events.publish(StateTopic.PRESETS);
			}
		}
		if (!madeLooks.isEmpty()) {
			events.publish(StateTopic.LOOKS);
		}
		var madeMoves = new ArrayList<Move>();
		if (request.moves() && !movingFixtures.isEmpty()) {
			var moveFixtures = movingFixtures.stream().map(id -> new MoveFixture(id, 0, 0, false, false, false))
					.toList();
			for (var figure : FIGURES) {
				var figureId = figureNamed(projectId, figure).id();
				var move = moves.create(projectId, MoveSpec.of(name + " – " + figure.name(), figureId, moveFixtures));
				madeMoves.add(move);
				moveButtons.add(Button.move(figure.name(), move.id()));
			}
		}

		var party = PartyRows.NONE;
		var gradients = request.gradients();
		if (request.partyButtons() || gradients) {
			party = addPartyRows(projectId, name, request.partyButtons() ? intensityButtons : List.of(),
					request.partyButtons() ? colorButtons : List.of(), request.partyButtons() ? moveButtons : List.of(),
					gradients ? request.fixtureIds() : List.of(), gradients ? mixingFixtures : List.of(),
					gradients ? movingFixtures : List.of(), request.partyPage());
		}
		return new Result(name, madeLooks, madeCompositions, madeMoves, madePresets, party.page(), party.rows(),
				notes);
	}

	private Optional<Mode> modeOf(PatchedFixture fixture) {
		return library.fixtureType(fixture.spec().fixtureTypeId()).flatMap(type -> type.mode(fixture.spec().modeName()));
	}

	/**
	 * The color palette: the five colors with the values each fixture needs for them (mixed or the closest wheel
	 * slot; a color no fixture can show is left out), then the slots of color wheels none of the five took, e.g.
	 * yellow or white – one entry per slot name.
	 */
	static List<PaletteEntry> palette(String name, Map<Long, FixtureColors> fixtureColors) {
		var entries = new ArrayList<PaletteEntry>();
		var taken = new HashMap<Long, Set<Double>>();
		for (var color : COLORS) {
			var values = new LinkedHashMap<Long, FixtureValues>();
			fixtureColors.forEach((fixtureId, colors) -> colors.valuesFor(color.color()).ifPresent(fixtureValues -> {
				values.put(fixtureId, fixtureValues);
				colors.slotFor(color.color()).ifPresent(slot -> taken.computeIfAbsent(fixtureId, id -> new HashSet<>())
						.add(slot.value()));
			}));
			if (!values.isEmpty()) {
				entries.add(new PaletteEntry(name + " – " + color.name(), values, Optional.of(color)));
			}
		}
		var slots = new LinkedHashMap<String, Map<Long, FixtureValues>>();
		var labels = new HashMap<String, String>();
		fixtureColors.forEach((fixtureId, colors) -> {
			for (var slot : colors.wheelSlots()) {
				if (taken.getOrDefault(fixtureId, Set.of()).contains(slot.value())) {
					continue;
				}
				var key = slot.label().toLowerCase(Locale.ROOT);
				labels.putIfAbsent(key, slot.label());
				slots.computeIfAbsent(key, k -> new LinkedHashMap<>()).putIfAbsent(fixtureId,
						new FixtureValues(Map.of(Attribute.of(ChannelFunction.COLOR_WHEEL), slot.value())));
			}
		});
		slots.forEach((key, values) -> entries.add(new PaletteEntry(name + " – " + labels.get(key), values,
				Optional.empty())));
		return entries;
	}

	private LookAndComposition lookWithComposition(long projectId, String name, Look content) {
		var look = looks.create(projectId, LookSpec.shared(name).withFolder(Folders.GENERATED), content);
		var composition = compositions.create(projectId, new CompositionSpec(name, FADE_SECONDS, HOLD_SECONDS,
				PlayMode.ONCE, IntensityMode.ABSOLUTE, Optional.empty(), CompositionSpec.GENERATED_FOLDER),
				List.of(CompositionStep.of(look.id())));
		return new LookAndComposition(look, composition);
	}

	/** A figure of the project with the name, made from the template if there is none yet. */
	private Figure figureNamed(long projectId, FigureSpec template) {
		return figures.findByProject(projectId).stream()
				.filter(figure -> figure.spec().name().equalsIgnoreCase(template.name()))
				.findFirst()
				.orElseGet(() -> {
					var figure = figures.create(projectId, template);
					events.publish(StateTopic.MOVES);
					return figure;
				});
	}

	/**
	 * Adds a row of buttons (name, intensities, colors, moves) and a row of gradients with a pan/tilt pad below the
	 * last used row of the chosen page – without a choice the first page with room, or a new page named after the
	 * setup if none has room. The buttons of each kind form
	 * a group with the gradient (or the pad, for moves) of the same kind, so only one of them is on. With a pad, the
	 * gradients are two rows high like the pad.
	 */
	private PartyRows addPartyRows(long projectId, String name, List<Button> intensity, List<Button> colors,
			List<Button> moveButtons, List<Long> dimmerFixtures, List<Long> colorFixtures, List<Long> padFixtures,
			PartyTarget target) {
		var buttonCount = intensity.size() + colors.size() + moveButtons.size();
		var withButtons = buttonCount > 0;
		var withGradients = !dimmerFixtures.isEmpty() || !colorFixtures.isEmpty() || !padFixtures.isEmpty();
		if (!withButtons && !withGradients) {
			return PartyRows.NONE;
		}
		var gradientHeight = padFixtures.isEmpty() ? 1 : PAD_SIZE;
		var rowCount = (withButtons ? 1 : 0) + (withGradients ? gradientHeight : 0);
		var dimmerSpan = dimmerFixtures.isEmpty() ? 0 : Math.max(intensity.size(), LEVELS.size());
		var colorSpan = colorFixtures.isEmpty() ? 0 : Math.max(colors.size(), COLORS.size());
		var padSpan = padFixtures.isEmpty() ? 0 : PAD_SIZE;
		var needed = 1 + Math.max(buttonCount, withGradients ? dimmerSpan + colorSpan + padSpan : 0);
		if (needed > PartyPage.MAX_SIZE) {
			throw new ConflictException("A party page has at most " + PartyPage.MAX_SIZE + " columns");
		}
		var matrix = partyMatrices.matrix(projectId);
		var label = name.length() > MAX_LABEL ? name.substring(0, MAX_LABEL) : name;
		var pageIndex = target.newPage() ? OptionalInt.empty() : IntStream.range(0, matrix.pages().size())
				.filter(index -> target.page().map(chosen -> chosen == index).orElse(true))
				.filter(index -> matrix.pages().get(index).firstFreeRow() + rowCount <= PartyPage.MAX_SIZE)
				.findFirst();
		if (target.page().isPresent() && pageIndex.isEmpty()) {
			var chosen = target.page().get();
			throw chosen < 0 || chosen >= matrix.pages().size()
					? new DomainValidationException(List.of("the party matrix has no page " + (chosen + 1)))
					: new ConflictException("The page \"" + matrix.pages().get(chosen).name() + "\" has no room for "
							+ rowCount + " more row" + (rowCount == 1 ? "" : "s")
							+ " – choose another page or a new one");
		}
		if (pageIndex.isEmpty() && matrix.pages().size() >= PartyMatrix.MAX_PAGES) {
			throw new ConflictException((target.newPage() ? "The party matrix"
					: "The party matrix has no room for " + rowCount + " more row" + (rowCount == 1 ? "" : "s") + " and")
					+ " already has " + PartyMatrix.MAX_PAGES + " pages – make room there first");
		}
		var index = pageIndex.orElse(matrix.pages().size());
		var page = pageIndex.isPresent() ? matrix.pages().get(index)
				: new PartyPage(name.length() > PartyPage.MAX_NAME ? name.substring(0, PartyPage.MAX_NAME) : name,
						1, 1, Set.of(), List.of());
		var start = page.firstFreeRow();
		var group = uniqueGroup(matrix, name);
		var cells = new ArrayList<>(page.cells());
		var rows = new ArrayList<Integer>();
		var row = start;
		if (withButtons) {
			cells.add(PartyCell.text(row, 0, label));
			var column = 1;
			for (var button : intensity) {
				cells.add(button.cell(row, column++, group + " – intensity"));
			}
			for (var button : colors) {
				cells.add(button.cell(row, column++, group + " – color"));
			}
			for (var button : moveButtons) {
				cells.add(button.cell(row, column++, group + " – move"));
			}
			rows.add(row++);
		}
		if (withGradients) {
			if (!withButtons) {
				cells.add(PartyCell.text(row, 0, label).withSize(1, gradientHeight));
			}
			var column = 1;
			if (dimmerSpan > 0) {
				cells.add(PartyCell.fader(row, column, dimmerSpan, gradientHeight,
						new PartyFader(PartyFader.Kind.DIMMER, dimmerFixtures)).inGroup(group + " – intensity"));
				column += dimmerSpan;
			}
			if (colorSpan > 0) {
				cells.add(PartyCell.fader(row, column, colorSpan, gradientHeight,
						new PartyFader(PartyFader.Kind.COLOR, colorFixtures)).inGroup(group + " – color"));
				column += colorSpan;
			}
			if (padSpan > 0) {
				cells.add(PartyCell.fader(row, column, padSpan, PAD_SIZE,
						new PartyFader(PartyFader.Kind.PAN_TILT, padFixtures)).inGroup(group + " – move"));
			}
			for (int i = 0; i < gradientHeight; i++) {
				rows.add(row++);
			}
		}
		var separators = new HashSet<>(page.separators());
		if (start > 0) {
			separators.add(start);
		}
		var filled = new PartyPage(page.name(), Math.max(page.rows(), row), Math.max(page.columns(), needed),
				separators, cells);
		partyMatrices.save(projectId, pageIndex.isPresent() ? matrix.withPage(index, filled)
				: matrix.withNewPage(filled));
		return new PartyRows(Optional.of(index), rows);
	}

	/** Where quick setup put its party fields. */
	private record PartyRows(Optional<Integer> page, List<Integer> rows) {

		static final PartyRows NONE = new PartyRows(Optional.empty(), List.of());
	}

	/** The name, or the name with a number if fields of the matrix already use it as their group. */
	private static String uniqueGroup(PartyMatrix matrix, String name) {
		var used = matrix.placedCells().flatMap(placed -> placed.cell().group().stream()).collect(Collectors.toSet());
		var candidate = name;
		for (int number = 2; taken(used, candidate); number++) {
			candidate = name + " " + number;
		}
		return candidate;
	}

	private static boolean taken(Set<String> groups, String base) {
		return groups.stream().anyMatch(group -> group.startsWith(base + " – "));
	}

	/**
	 * One fixture: its name. Several: what their names have in common without trailing numbers ("PAR 1", "PAR 2"
	 * → "PAR"), otherwise the first name.
	 */
	static String baseName(List<PatchedFixture> fixtures) {
		var first = fixtures.getFirst().spec().name();
		if (fixtures.size() == 1) {
			return first;
		}
		var prefix = first;
		for (var fixture : fixtures) {
			var other = fixture.spec().name();
			var length = 0;
			while (length < prefix.length() && length < other.length()
					&& prefix.charAt(length) == other.charAt(length)) {
				length++;
			}
			prefix = prefix.substring(0, length);
		}
		var trimmed = prefix.replaceAll("[\\s\\d#.\\-–]+$", "").strip();
		return trimmed.isEmpty() ? first : trimmed;
	}

	private static String hex(double red, double green, double blue) {
		return String.format(Locale.ROOT, "#%02x%02x%02x", channel(red), channel(green), channel(blue));
	}

	private static int channel(double value) {
		return (int) Math.round(Math.min(1, Math.max(0, value)) * 255);
	}

	// --- figures -------------------------------------------------------------------------------------------------

	/** Left and right and back, around the middle. */
	private static FigureSpec line() {
		return new FigureSpec("Line", true, List.of(new FigurePoint(0.35, 0.5), new FigurePoint(0.65, 0.5)));
	}

	private static FigureSpec circle() {
		var points = new ArrayList<FigurePoint>();
		for (int i = 0; i < ROUND_POINTS; i++) {
			var angle = 2 * Math.PI * i / ROUND_POINTS;
			points.add(new FigurePoint(0.5 + 0.15 * Math.cos(angle), 0.5 + 0.15 * Math.sin(angle)));
		}
		return new FigureSpec("Circle", true, points);
	}

	/** A lying eight. */
	private static FigureSpec eight() {
		var points = new ArrayList<FigurePoint>();
		for (int i = 0; i < ROUND_POINTS; i++) {
			var angle = 2 * Math.PI * i / ROUND_POINTS;
			points.add(new FigurePoint(0.5 + 0.2 * Math.sin(angle), 0.5 + 0.1 * Math.sin(2 * angle)));
		}
		return new FigureSpec("Eight", true, points);
	}

	private record Level(String label, double value) {
	}

	private record NamedColor(String name, double red, double green, double blue) {

		Color color() {
			return new Color(red, green, blue);
		}
	}

	/**
	 * A color preset to make.
	 *
	 * @param color one of the five colors that get a look and a button, empty for a further wheel slot
	 */
	record PaletteEntry(String name, Map<Long, FixtureValues> values, Optional<NamedColor> color) {
	}

	private record LookAndComposition(NamedLook look, Composition composition) {
	}

	/** A party button before it has its place. */
	private record Button(String label, Optional<Long> compositionId, Optional<Long> moveId, String color,
			String borderColor) {

		static Button composition(String label, long compositionId, String color, String borderColor) {
			return new Button(label, Optional.of(compositionId), Optional.empty(), color, borderColor);
		}

		static Button move(String label, long moveId) {
			return new Button(label, Optional.empty(), Optional.of(moveId), PartyCell.DEFAULT_COLOR,
					PartyCell.DEFAULT_BORDER);
		}

		PartyCell cell(int row, int column, String group) {
			return new PartyCell(row, column, Optional.of(label), Optional.empty(), compositionId, moveId, color,
					borderColor, KeyMode.TOGGLE, Optional.of(group));
		}
	}
}
