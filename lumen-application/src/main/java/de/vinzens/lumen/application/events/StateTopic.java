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
	/** Playback position, fades or pause changed. */
	PLAYBACK,
	/** Setup/show mode changed. */
	MODE,
	/** Grand master or blackout changed. */
	OUTPUT,
	/** New measurement of render loop and Art-Net output (every second). */
	HEALTH,
	/** PIN or network access changed, or all clients were locked. */
	ACCESS,
	/** The fixture library was reloaded. */
	LIBRARY,
	/** Compositions in the library or in scenes changed. */
	COMPOSITIONS,
	/** The stage preview of the composition editor started or stopped. */
	STAGE_PREVIEW,
	/** Tempo, palettes, intensities or buttons of party mode changed. */
	PARTY,
	/** Party buttons of the active project were added, changed or removed. */
	PARTY_BUTTONS
}
