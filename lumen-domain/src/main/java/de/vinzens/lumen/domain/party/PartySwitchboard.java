package de.vinzens.lumen.domain.party;

import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.composition.ActiveComposition;
import de.vinzens.lumen.domain.composition.CompositionMixer;
import de.vinzens.lumen.domain.composition.FixtureAttributes;
import de.vinzens.lumen.domain.composition.FixtureColors;
import de.vinzens.lumen.domain.composition.KeyMode;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.output.FixtureValues;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The fields of the party matrix that are on, and the light they make. Any number of fields can be on at the same
 * time, except within a group: switching one on switches the others of its group off. Where fields set the same
 * attribute of a fixture, the highest value wins – for every attribute, not only intensity. Each field plays its
 * compositions from the moment it went on; a gradient or pad field sets the values of the point last touched. Fields
 * are addressed by page, row and column; groups reach over all pages. Not thread-safe – callers synchronize.
 */
public final class PartySwitchboard {

	/**
	 * A field ready to play: what it plays as compositions (a scene: all of its compositions, in their order).
	 *
	 * @param page index of the page the field is on
	 */
	public record PlayableCell(int page, PartyCell cell, List<ActiveComposition> compositions) {

		public PlayableCell {
			Objects.requireNonNull(cell, "cell");
			compositions = List.copyOf(compositions);
		}

		/** A field on the first page. */
		public PlayableCell(PartyCell cell, List<ActiveComposition> compositions) {
			this(0, cell, compositions);
		}

		public PartyPlace place() {
			return new PartyPlace(page, cell.row(), cell.column());
		}
	}

	private static final Attribute CYAN = Attribute.of(ChannelFunction.CYAN);

	private Map<PartyPlace, PlayableCell> cells = Map.of();
	/** fields that are on, with the moment they went on */
	private final Map<PartyPlace, Instant> on = new LinkedHashMap<>();
	/** the point last touched on each gradient or pad field, kept while it is off */
	private final Map<PartyPlace, PartyPoint> faderPositions = new HashMap<>();

	/**
	 * Replaces the matrix. A field stays on if it still plays the same scene, composition, move, gradient or pad at
	 * the same place; a gradient or pad keeps its point while it sets the same.
	 */
	public void configure(List<PlayableCell> playable) {
		var byPosition = new HashMap<PartyPlace, PlayableCell>();
		playable.forEach(cell -> byPosition.put(cell.place(), cell));
		on.keySet().removeIf(position -> {
			var now = byPosition.get(position);
			var before = cells.get(position);
			return now == null || before == null || !now.cell().playsSomething()
					|| !now.cell().sceneId().equals(before.cell().sceneId())
					|| !now.cell().compositionId().equals(before.cell().compositionId())
					|| !now.cell().moveId().equals(before.cell().moveId())
					|| !now.cell().fader().equals(before.cell().fader());
		});
		faderPositions.keySet().removeIf(position -> {
			var now = byPosition.get(position);
			var before = cells.get(position);
			return now == null || now.cell().fader().isEmpty() || before == null
					|| !now.cell().fader().map(PartyFader::kind).equals(before.cell().fader().map(PartyFader::kind));
		});
		cells = Map.copyOf(byPosition);
	}

	/**
	 * Toggle fields switch, flash fields go on until released. Switching on ends the others of the group. A gradient
	 * or pad field switches like a toggle field, at the point last touched (at first the right end of a gradient, the
	 * middle of a pad).
	 */
	public void press(PartyPlace place, Instant now) {
		var cell = require(place);
		if (cell.mode() == KeyMode.TOGGLE && on.containsKey(place)) {
			on.remove(place);
			return;
		}
		switchOn(place, cell, now);
	}

	/**
	 * Touches a gradient or pad field at a point: it goes on (ending the others of its group) or stays on, and sets
	 * the values of that point.
	 */
	public void slide(PartyPlace place, PartyPoint point, Instant now) {
		var cell = require(place);
		if (cell.fader().isEmpty()) {
			throw new DomainValidationException(List.of(place.describe() + " has no gradient or pad"));
		}
		faderPositions.put(place, point);
		switchOn(place, cell, now);
	}

	private void switchOn(PartyPlace place, PartyCell cell, Instant now) {
		cell.group().ifPresent(group -> on.keySet().removeIf(other -> !other.equals(place)
				&& cells.get(other).cell().group().equals(cell.group())));
		on.putIfAbsent(place, now);
	}

	/** Ends a flash field; toggle fields ignore it. */
	public void release(PartyPlace place) {
		var cell = require(place);
		if (cell.mode() == KeyMode.FLASH) {
			on.remove(place);
		}
	}

	public void clear() {
		on.clear();
	}

	public boolean isOn(PartyPlace place) {
		return on.containsKey(place);
	}

	public Set<PartyPlace> onPlaces() {
		return Set.copyOf(on.keySet());
	}

	/** The point of every gradient and pad field that has been touched, also of those that are off. */
	public Map<PartyPlace, PartyPoint> faderPoints() {
		return Map.copyOf(faderPositions);
	}

	/** The light of all fields that are on: per attribute the highest value of any field. */
	public Map<Long, FixtureValues> output(Instant now, FixtureAttributes attributes) {
		var merged = new HashMap<Long, Map<Attribute, Double>>();
		on.forEach((place, since) -> {
			var seconds = Math.max(0, Duration.between(since, now).toNanos() / 1e9);
			var cell = cells.get(place);
			Map<Long, FixtureValues> look = Map.of();
			for (var composition : cell.compositions()) {
				look = CompositionMixer.apply(look, composition, seconds, attributes);
			}
			if (cell.cell().fader().isPresent()) {
				var fader = cell.cell().fader().get();
				look = faderLook(fader, faderPositions.getOrDefault(place, fader.firstPoint()), attributes);
			}
			look.forEach((fixtureId, values) -> {
				var target = merged.computeIfAbsent(fixtureId, id -> new HashMap<>());
				values.values().forEach((attribute, value) -> target.merge(attribute, value, Math::max));
			});
		});
		var result = new HashMap<Long, FixtureValues>();
		merged.forEach((fixtureId, values) -> result.put(fixtureId, new FixtureValues(values)));
		return result;
	}

	/**
	 * The values of a gradient or pad at a point for those of its fixtures that have the attributes. A color gradient
	 * mixes red, green and blue, or – on fixtures without them – cyan, magenta and yellow.
	 */
	private static Map<Long, FixtureValues> faderLook(PartyFader fader, PartyPoint point,
			FixtureAttributes attributes) {
		var rgb = fader.valuesAt(point).values();
		var cmy = FixtureColors.cmy(PartyFader.colorAt(point.x())).values();
		var look = new HashMap<Long, FixtureValues>();
		for (var fixtureId : fader.fixtureIds()) {
			var values = fader.kind() == PartyFader.Kind.COLOR && !attributes.has(fixtureId, PartyFader.RED)
					&& attributes.has(fixtureId, CYAN) ? cmy : rgb;
			var supported = new HashMap<Attribute, Double>();
			values.forEach((attribute, value) -> {
				if (attributes.has(fixtureId, attribute)) {
					supported.put(attribute, value);
				}
			});
			if (!supported.isEmpty()) {
				look.put(fixtureId, new FixtureValues(supported));
			}
		}
		return look;
	}

	private PartyCell require(PartyPlace place) {
		var playable = cells.get(place);
		if (playable == null || !playable.cell().playsSomething()) {
			throw new DomainValidationException(List.of(place.describe() + " plays nothing"));
		}
		return playable.cell();
	}
}
