package de.vinzens.lumen.domain.party;

import de.vinzens.lumen.domain.composition.KeyMode;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.library.Mode;
import de.vinzens.lumen.domain.output.FixtureValues;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The live look of party mode: a palette and an intensity per fixture, with the active party buttons on top.
 * Button values win over the palettes (the button pressed last first), only the intensity mixes HTP.
 * Not thread-safe – callers synchronize.
 */
public final class PartyLook {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);

	private final Map<Long, Palette> palettes = new HashMap<>();
	private final Map<Long, Double> intensities = new HashMap<>();
	private Map<Long, PartyButton> buttons = Map.of();
	/** ids of the buttons that are on, in the order they were switched on */
	private final LinkedHashSet<Long> active = new LinkedHashSet<>();

	/** Takes over the buttons of the project; buttons that no longer exist go off. */
	public void useButtons(Collection<PartyButton> projectButtons) {
		var byId = new HashMap<Long, PartyButton>();
		projectButtons.forEach(button -> byId.put(button.id(), button));
		buttons = Map.copyOf(byId);
		active.retainAll(buttons.keySet());
	}

	/** Gives the fixtures a palette; fixtures that are dark come on at full. */
	public void setPalette(Collection<Long> fixtureIds, Palette palette) {
		for (var id : fixtureIds) {
			palettes.put(id, palette);
			if (intensities.getOrDefault(id, 0.0) <= 0) {
				intensities.put(id, 1.0);
			}
		}
	}

	public void setIntensity(Collection<Long> fixtureIds, double level) {
		var clamped = Math.max(0, Math.min(1, level));
		fixtureIds.forEach(id -> intensities.put(id, clamped));
	}

	/** Toggle buttons switch, flash buttons go on until {@link #release}. */
	public void press(long buttonId) {
		var button = requireButton(buttonId);
		if (button.mode() == KeyMode.TOGGLE && active.contains(buttonId)) {
			active.remove(buttonId);
		} else {
			active.remove(buttonId);
			active.add(buttonId);
		}
	}

	/** Ends a flash; toggle buttons ignore it. */
	public void release(long buttonId) {
		if (requireButton(buttonId).mode() == KeyMode.FLASH) {
			active.remove(buttonId);
		}
	}

	/** Everything off: palettes, intensities and buttons. */
	public void clear() {
		palettes.clear();
		intensities.clear();
		active.clear();
	}

	/** Fixtures with a palette or an intensity. */
	public Set<Long> touchedFixtures() {
		var fixtures = new LinkedHashSet<>(palettes.keySet());
		fixtures.addAll(intensities.keySet());
		return fixtures;
	}

	public Optional<Palette> palette(long fixtureId) {
		return Optional.ofNullable(palettes.get(fixtureId));
	}

	public double intensity(long fixtureId) {
		return intensities.getOrDefault(fixtureId, 0.0);
	}

	public boolean isOn(long buttonId) {
		return active.contains(buttonId);
	}

	public List<Long> activeButtons() {
		return List.copyOf(active);
	}

	/** The values of the look for the patched fixtures. */
	public Map<Long, FixtureValues> output(Map<Long, Mode> modes) {
		var result = new HashMap<Long, FixtureValues>();
		for (var id : touchedFixtures()) {
			var mode = modes.get(id);
			if (mode == null) {
				continue;
			}
			var values = new HashMap<Attribute, Double>();
			var palette = palettes.get(id);
			if (palette != null) {
				values.putAll(PaletteColors.of(palette, mode).values());
			}
			values.put(DIMMER, intensities.getOrDefault(id, 0.0));
			result.put(id, new FixtureValues(values));
		}
		for (var buttonId : new ArrayList<>(active)) {
			buttons.get(buttonId).values().forEach((fixtureId, buttonValues) -> {
				if (!modes.containsKey(fixtureId)) {
					return;
				}
				var values = new HashMap<>(result.getOrDefault(fixtureId, FixtureValues.EMPTY).values());
				buttonValues.values().forEach((attribute, value) -> values.merge(attribute, value,
						(old, pressed) -> attribute.equals(DIMMER) ? Math.max(old, pressed) : pressed));
				result.put(fixtureId, new FixtureValues(values));
			});
		}
		return result;
	}

	private PartyButton requireButton(long buttonId) {
		var button = buttons.get(buttonId);
		if (button == null) {
			throw new IllegalArgumentException("Party button " + buttonId + " does not exist");
		}
		return button;
	}
}
