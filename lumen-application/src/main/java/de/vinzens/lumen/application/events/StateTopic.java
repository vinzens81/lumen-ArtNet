package de.vinzens.lumen.application.events;

/**
 * Areas of the application state that clients may need to refresh.
 */
public enum StateTopic {
	/** Project list or project settings changed. */
	PROJECTS,
	/** Universes, fixtures or groups of the active project changed. */
	PATCH,
	/** Scenes of the active project changed. */
	SCENES,
	/** Programmer values changed. */
	PROGRAMMER,
	/** Art-Net nodes appeared, changed or went offline. */
	NODES,
	/** Grand master or blackout changed. */
	OUTPUT
}
