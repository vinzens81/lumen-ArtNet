package de.vinzens.lumen.application.quicksetup.port.in;

import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.composition.Composition;
import de.vinzens.lumen.domain.look.NamedLook;
import de.vinzens.lumen.domain.move.Move;
import de.vinzens.lumen.domain.preset.Preset;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Quick setup: makes the usual looks, compositions, moves and party fields for freshly patched fixtures in one go,
 * so they can be played right away. Everything it makes is ordinary and can be changed or deleted afterwards.
 */
public interface QuickSetupUseCase {

	/**
	 * Sets up the fixtures together: every look, composition and move covers all of them that can do it, so one
	 * party button switches them all.
	 *
	 * @throws de.vinzens.lumen.application.ConflictException when the party matrix has no room for the new rows
	 */
	Result setUp(long projectId, Request request);

	/**
	 * @param fixtureIds   the fixtures, at least one
	 * @param name         what everything is named after, empty = from the names of the fixtures
	 * @param intensity    looks for 0, 50 and 100 % intensity
	 * @param colors       looks for red, orange, green, blue and violet – mixed, or with the closest slot of a color
	 *                     wheel – and a color palette (presets) they refer to, with the other wheel slots as well
	 * @param moves        line, circle and eight (fixtures with pan and tilt)
	 * @param partyButtons a row in the party matrix with a button per look and move
	 * @param gradients    a row in the party matrix with an intensity and a color gradient
	 */
	record Request(List<Long> fixtureIds, Optional<String> name, boolean intensity, boolean colors, boolean moves,
			boolean partyButtons, boolean gradients) {

		public Request {
			Objects.requireNonNull(fixtureIds, "fixtureIds");
			Objects.requireNonNull(name, "name");
			fixtureIds = List.copyOf(fixtureIds);
			name = name.map(String::strip).filter(text -> !text.isEmpty());
			if (fixtureIds.isEmpty()) {
				throw new DomainValidationException(List.of("choose at least one fixture"));
			}
			if (!intensity && !colors && !moves && !gradients) {
				throw new DomainValidationException(List.of("choose at least one thing to set up"));
			}
		}
	}

	/**
	 * What was made.
	 *
	 * @param presets   the color palette
	 * @param partyRows the rows of the party matrix that were filled (0-based)
	 * @param notes     what was left out and why, e.g. colors for fixtures without red, green and blue
	 */
	record Result(String name, List<NamedLook> looks, List<Composition> compositions, List<Move> moves,
			List<Preset> presets, List<Integer> partyRows, List<String> notes) {

		public Result {
			presets = List.copyOf(presets);
			looks = List.copyOf(looks);
			compositions = List.copyOf(compositions);
			moves = List.copyOf(moves);
			partyRows = List.copyOf(partyRows);
			notes = List.copyOf(notes);
		}
	}
}
