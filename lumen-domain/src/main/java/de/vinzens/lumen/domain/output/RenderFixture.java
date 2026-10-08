package de.vinzens.lumen.domain.output;

import de.vinzens.lumen.domain.library.Mode;
import de.vinzens.lumen.domain.project.PositionLimits;

import java.util.Objects;
import java.util.Optional;

/**
 * Everything the renderer needs to know about a patched fixture.
 *
 * @param address DMX start address (1–512)
 * @param limits  allowed range of pan and tilt in the show (not for the programmer)
 */
public record RenderFixture(long fixtureId, long universeId, int address, Mode mode, Optional<PositionLimits> limits) {

	public RenderFixture {
		Objects.requireNonNull(mode, "mode");
		Objects.requireNonNull(limits, "limits");
	}

	public RenderFixture(long fixtureId, long universeId, int address, Mode mode) {
		this(fixtureId, universeId, address, mode, Optional.empty());
	}
}
