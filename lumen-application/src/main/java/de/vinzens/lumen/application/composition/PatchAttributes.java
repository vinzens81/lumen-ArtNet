package de.vinzens.lumen.application.composition;

import de.vinzens.lumen.application.library.port.in.QueryFixtureLibraryUseCase;
import de.vinzens.lumen.domain.composition.FixtureAttributes;
import de.vinzens.lumen.domain.library.Mode;
import de.vinzens.lumen.domain.project.PatchedFixture;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** What the patched fixtures of a project can do, from the fixture library. */
public final class PatchAttributes {

	private PatchAttributes() {
	}

	public static FixtureAttributes of(List<PatchedFixture> fixtures, QueryFixtureLibraryUseCase library) {
		var modes = new HashMap<Long, Mode>();
		for (var fixture : fixtures) {
			library.fixtureType(fixture.spec().fixtureTypeId())
					.flatMap(type -> type.mode(fixture.spec().modeName()))
					.ifPresent(mode -> modes.put(fixture.id(), mode));
		}
		return FixtureAttributes.of(Map.copyOf(modes));
	}
}
