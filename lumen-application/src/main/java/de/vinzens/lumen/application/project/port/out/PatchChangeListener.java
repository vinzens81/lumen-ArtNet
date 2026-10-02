package de.vinzens.lumen.application.project.port.out;

/**
 * Notified after the active project or its patch may have changed, e.g. to reload the output engine.
 */
@FunctionalInterface
public interface PatchChangeListener {

	PatchChangeListener NONE = () -> {
	};

	void patchChanged();
}
