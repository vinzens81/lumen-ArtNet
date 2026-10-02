package de.vinzens.lumen.application.events;

/**
 * Areas of the application state that clients may need to refresh.
 */
public enum StateTopic {
	/** Project list or project settings changed. */
	PROJECTS,
	/** Universes, fixtures or groups of the active project changed. */
	PATCH,
	/** Programmer values changed. */
	PROGRAMMER,
	/** Grand master or blackout changed. */
	OUTPUT
}
