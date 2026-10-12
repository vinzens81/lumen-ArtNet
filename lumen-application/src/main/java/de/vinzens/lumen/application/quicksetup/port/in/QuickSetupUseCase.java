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
	 * @throws de.vinzens.lumen.application.ConflictException when the chosen page (or, choosing none, every page) of
	 *                                                        the party matrix has no room for the new rows and no
	 *                                                        page can be added
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
	 * @param gradients    a row in the party matrix with an intensity and a color gradient, and a pan/tilt pad for
	 *                     fixtures with pan and tilt (the row is two rows high then)
	 * @param partyPage    where the party rows go
	 */
	record Request(List<Long> fixtureIds, Optional<String> name, boolean intensity, boolean colors, boolean moves,
			boolean partyButtons, boolean gradients, PartyTarget partyPage) {

		public Request {
			Objects.requireNonNull(fixtureIds, "fixtureIds");
			Objects.requireNonNull(name, "name");
			Objects.requireNonNull(partyPage, "partyPage");
			fixtureIds = List.copyOf(fixtureIds);
			name = name.map(String::strip).filter(text -> !text.isEmpty());
			if (fixtureIds.isEmpty()) {
				throw new DomainValidationException(List.of("choose at least one fixture"));
			}
			if (!intensity && !colors && !moves && !gradients) {
				throw new DomainValidationException(List.of("choose at least one thing to set up"));
			}
		}

		/** The party rows go on the first page with room. */
		public Request(List<Long> fixtureIds, Optional<String> name, boolean intensity, boolean colors, boolean moves,
				boolean partyButtons, boolean gradients) {
			this(fixtureIds, name, intensity, colors, moves, partyButtons, gradients, PartyTarget.FIRST_WITH_ROOM);
		}
	}

	/**
	 * The page of the party matrix the rows go on.
	 *
	 * @param page    an existing page (index from 0); empty = the first page with room, or a new one if none has
	 * @param newPage a new page at the end, named after the setup
	 */
	record PartyTarget(Optional<Integer> page, boolean newPage) {

		public static final PartyTarget FIRST_WITH_ROOM = new PartyTarget(Optional.empty(), false);
		public static final PartyTarget NEW_PAGE = new PartyTarget(Optional.empty(), true);

		public PartyTarget {
			Objects.requireNonNull(page, "page");
			if (page.isPresent() && newPage) {
				throw new DomainValidationException(List.of("choose an existing page or a new one, not both"));
			}
		}

		public static PartyTarget page(int index) {
			return new PartyTarget(Optional.of(index), false);
		}
	}

	/**
	 * What was made.
	 *
	 * @param presets   the color palette
	 * @param partyPage the page of the party matrix the rows went to (0-based), empty if none
	 * @param partyRows the rows of that page that were filled (0-based)
	 * @param notes     what was left out and why, e.g. colors for fixtures without red, green and blue
	 */
	record Result(String name, List<NamedLook> looks, List<Composition> compositions, List<Move> moves,
			List<Preset> presets, Optional<Integer> partyPage, List<Integer> partyRows, List<String> notes) {

		public Result {
			Objects.requireNonNull(partyPage, "partyPage");
			presets = List.copyOf(presets);
			looks = List.copyOf(looks);
			compositions = List.copyOf(compositions);
			moves = List.copyOf(moves);
			partyRows = List.copyOf(partyRows);
			notes = List.copyOf(notes);
		}
	}
}
