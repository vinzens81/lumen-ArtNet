package de.vinzens.lumen.domain.scene;

/**
 * A move used in a scene (or a part of it): where its fixtures point while the scene is on stage.
 */
public record SceneMove(long moveId) {

	public static SceneMove of(long moveId) {
		return new SceneMove(moveId);
	}
}
