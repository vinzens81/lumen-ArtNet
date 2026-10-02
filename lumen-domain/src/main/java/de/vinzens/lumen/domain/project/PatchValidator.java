package de.vinzens.lumen.domain.project;

import de.vinzens.lumen.domain.library.FixtureType;
import de.vinzens.lumen.domain.library.Mode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Prüft den Patch eines Projekts gegen die Gerätebibliothek: fehlende Typen/Modi, Adressbereich und
 * Überschneidungen. Ein fehlerhafter Patch darf gespeichert werden; die Probleme werden angezeigt.
 */
public final class PatchValidator {

	private PatchValidator() {
	}

	public static List<PatchIssue> validate(List<Universe> universes, List<PatchedFixture> fixtures,
			Function<String, Optional<FixtureType>> library) {
		var issues = new ArrayList<PatchIssue>();
		issues.addAll(duplicatePortAddresses(universes));

		var universesById = new HashMap<Long, Universe>();
		universes.forEach(u -> universesById.put(u.id(), u));

		var footprints = new ArrayList<Footprint>();
		for (var fixture : fixtures) {
			var spec = fixture.spec();
			var universe = universesById.get(spec.universeId());
			if (universe == null) {
				issues.add(PatchIssue.fixtureError("'" + spec.name() + "': Universe existiert nicht", fixture.id()));
				continue;
			}
			var mode = resolveMode(fixture, library, issues);
			if (mode.isEmpty()) {
				continue;
			}
			var last = spec.address() + mode.get().dmxChannelCount() - 1;
			if (last > FixtureSpec.MAX_ADDRESS) {
				issues.add(PatchIssue.fixtureError("'" + spec.name() + "': belegt Kanäle " + spec.address() + "–"
						+ last + " und überschreitet 512 in " + universe.displayName(), fixture.id()));
			}
			footprints.add(new Footprint(fixture, universe, spec.address(), Math.min(last, FixtureSpec.MAX_ADDRESS)));
		}
		issues.addAll(overlaps(footprints));
		return issues;
	}

	private static Optional<Mode> resolveMode(PatchedFixture fixture, Function<String, Optional<FixtureType>> library,
			List<PatchIssue> issues) {
		var spec = fixture.spec();
		var type = library.apply(spec.fixtureTypeId());
		if (type.isEmpty()) {
			issues.add(PatchIssue.fixtureError("'" + spec.name() + "': Gerätetyp '" + spec.fixtureTypeId()
					+ "' ist nicht in der Gerätebibliothek", fixture.id()));
			return Optional.empty();
		}
		var mode = type.get().mode(spec.modeName());
		if (mode.isEmpty()) {
			issues.add(PatchIssue.fixtureError("'" + spec.name() + "': Modus '" + spec.modeName()
					+ "' gibt es bei '" + spec.fixtureTypeId() + "' nicht", fixture.id()));
		}
		return mode;
	}

	private static List<PatchIssue> overlaps(List<Footprint> footprints) {
		var issues = new ArrayList<PatchIssue>();
		var byUniverse = new HashMap<Long, List<Footprint>>();
		footprints.forEach(f -> byUniverse.computeIfAbsent(f.universe().id(), id -> new ArrayList<>()).add(f));

		for (var inUniverse : byUniverse.values()) {
			var sorted = inUniverse.stream().sorted(Comparator.comparingInt(Footprint::first)).toList();
			for (int i = 0; i < sorted.size(); i++) {
				for (int j = i + 1; j < sorted.size() && sorted.get(j).first() <= sorted.get(i).last(); j++) {
					var a = sorted.get(i);
					var b = sorted.get(j);
					issues.add(PatchIssue.fixtureError("'" + a.fixture().spec().name() + "' und '"
							+ b.fixture().spec().name() + "' überschneiden sich in " + a.universe().displayName()
							+ " (Kanäle " + b.first() + "–" + Math.min(a.last(), b.last()) + ")",
							a.fixture().id(), b.fixture().id()));
				}
			}
		}
		return issues;
	}

	private static List<PatchIssue> duplicatePortAddresses(List<Universe> universes) {
		var issues = new ArrayList<PatchIssue>();
		Map<Integer, Universe> byPortAddress = new HashMap<>();
		for (var universe : universes) {
			var artNet = universe.spec().artNet();
			var existing = byPortAddress.putIfAbsent(artNet.portAddress(), universe);
			if (existing != null) {
				issues.add(PatchIssue.universeWarning(existing.displayName() + " und " + universe.displayName()
						+ " senden beide auf Art-Net " + artNet, existing.id(), universe.id()));
			}
		}
		return issues;
	}

	private record Footprint(PatchedFixture fixture, Universe universe, int first, int last) {
	}
}
