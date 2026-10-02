package de.vinzens.lumen.application.library;

import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.library.port.in.ReloadFixtureLibraryUseCase;
import de.vinzens.lumen.application.project.port.out.PatchChangeListener;

import java.lang.System.Logger.Level;
import java.util.Objects;

/**
 * Reloads the fixture library while Lumen is running, e.g. after a profile was edited: the output picks up the
 * new channel layout and all clients refresh the library.
 */
public class FixtureLibraryReloader implements ReloadFixtureLibraryUseCase {

	private static final System.Logger LOG = System.getLogger(FixtureLibraryReloader.class.getName());

	private final FixtureLibraryService library;
	private final PatchChangeListener patch;
	private final StateEvents events;

	public FixtureLibraryReloader(FixtureLibraryService library, PatchChangeListener patch, StateEvents events) {
		this.library = Objects.requireNonNull(library, "library");
		this.patch = Objects.requireNonNull(patch, "patch");
		this.events = Objects.requireNonNull(events, "events");
	}

	@Override
	public synchronized void reload() {
		library.reload();
		LOG.log(Level.INFO, "Fixture library reloaded: {0} fixture types, {1} issues", library.fixtureTypes().size(),
				library.issues().size());
		patch.patchChanged();
		events.publish(StateTopic.LIBRARY);
	}
}
