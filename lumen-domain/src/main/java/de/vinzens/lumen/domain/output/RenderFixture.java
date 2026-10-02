package de.vinzens.lumen.domain.output;

import de.vinzens.lumen.domain.library.Mode;

import java.util.Objects;

/**
 * Everything the renderer needs to know about a patched fixture.
 *
 * @param address DMX start address (1–512)
 */
public record RenderFixture(long fixtureId, long universeId, int address, Mode mode) {

	public RenderFixture {
		Objects.requireNonNull(mode, "mode");
	}
}
