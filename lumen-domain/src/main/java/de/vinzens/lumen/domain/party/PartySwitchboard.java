package de.vinzens.lumen.domain.party;

import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.composition.ActiveComposition;
import de.vinzens.lumen.domain.composition.CompositionMixer;
import de.vinzens.lumen.domain.composition.FixtureAttributes;
import de.vinzens.lumen.domain.composition.KeyMode;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.party.PartyCell.Position;

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
 * compositions from the moment it went on. Not thread-safe – callers synchronize.
 */
public final class PartySwitchboard {

	/** A field ready to play: what it plays as compositions (a scene: all of its compositions, in their order). */
	public record PlayableCell(PartyCell cell, List<ActiveComposition> compositions) {

		public PlayableCell {
			Objects.requireNonNull(cell, "cell");
			compositions = List.copyOf(compositions);
		}
	}

	private Map<Position, PlayableCell> cells = Map.of();
	/** fields that are on, with the moment they went on */
	private final Map<Position, Instant> on = new LinkedHashMap<>();

	/**
	 * Replaces the matrix. A field stays on if it still plays the same scene or composition at the same place.
	 */
	public void configure(List<PlayableCell> playable) {
		var byPosition = new HashMap<Position, PlayableCell>();
		playable.forEach(cell -> byPosition.put(cell.cell().position(), cell));
		on.keySet().removeIf(position -> {
			var now = byPosition.get(position);
			var before = cells.get(position);
			return now == null || before == null || !now.cell().playsSomething()
					|| !now.cell().sceneId().equals(before.cell().sceneId())
					|| !now.cell().compositionId().equals(before.cell().compositionId());
		});
		cells = Map.copyOf(byPosition);
	}

	/** Toggle fields switch, flash fields go on until released. Switching on ends the others of the group. */
	public void press(int row, int column, Instant now) {
		var cell = require(row, column);
		var position = cell.position();
		if (cell.mode() == KeyMode.TOGGLE && on.containsKey(position)) {
			on.remove(position);
			return;
		}
		cell.group().ifPresent(group -> on.keySet().removeIf(other -> !other.equals(position)
				&& cells.get(other).cell().group().equals(cell.group())));
		on.putIfAbsent(position, now);
	}

	/** Ends a flash field; toggle fields ignore it. */
	public void release(int row, int column) {
		var cell = require(row, column);
		if (cell.mode() == KeyMode.FLASH) {
			on.remove(cell.position());
		}
	}

	public void clear() {
		on.clear();
	}

	public boolean isOn(int row, int column) {
		return on.containsKey(new Position(row, column));
	}

	public Set<Position> onPositions() {
		return Set.copyOf(on.keySet());
	}

	/** The light of all fields that are on: per attribute the highest value of any field. */
	public Map<Long, FixtureValues> output(Instant now, FixtureAttributes attributes) {
		var merged = new HashMap<Long, Map<Attribute, Double>>();
		on.forEach((position, since) -> {
			var seconds = Math.max(0, Duration.between(since, now).toNanos() / 1e9);
			Map<Long, FixtureValues> look = Map.of();
			for (var composition : cells.get(position).compositions()) {
				look = CompositionMixer.apply(look, composition, seconds, attributes);
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

	private PartyCell require(int row, int column) {
		var playable = cells.get(new Position(row, column));
		if (playable == null || !playable.cell().playsSomething()) {
			throw new DomainValidationException(List.of("Field " + (row + 1) + "/" + (column + 1)
					+ " plays nothing"));
		}
		return playable.cell();
	}
}
