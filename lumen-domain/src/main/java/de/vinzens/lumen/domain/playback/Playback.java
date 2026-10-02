package de.vinzens.lumen.domain.playback;

import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.scene.Scene;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Plays a cue list: GO starts the next scene, fading out what is on stage while fading in the new scene.
 *
 * <p>Positions: {@code -1} = before the first scene (dark), {@code 0..n-1} = a scene, {@code n} = end of the show
 * (dark). All timing runs on a virtual clock that stands still while the playback is paused, so fades and the
 * auto-follow countdown freeze and continue seamlessly.
 *
 * <p>A GO during a running fade freezes the current output as the outgoing look, so even a fast double GO
 * never jumps. Not thread-safe – callers synchronize.
 */
public final class Playback {

	public static final int READY = -1;

	private List<Scene> scenes;
	private Duration fadeIn;
	private Duration fadeOut;

	private int position = READY;
	private Integer pendingNext;
	private Transition transition;
	/** Virtual time at which the stay of the current scene began (fade-in complete). */
	private Duration stayStart = Duration.ZERO;

	private Instant pausedAt;
	private Duration pausedTotal = Duration.ZERO;

	public Playback(List<Scene> scenes, Duration fadeIn, Duration fadeOut) {
		this.scenes = List.copyOf(scenes);
		this.fadeIn = Objects.requireNonNull(fadeIn, "fadeIn");
		this.fadeOut = Objects.requireNonNull(fadeOut, "fadeOut");
	}

	/**
	 * Replaces the cue list, e.g. after scenes were edited. The current scene is kept by id; if it was deleted the
	 * playback continues from its former position.
	 */
	public void reload(List<Scene> newScenes, Duration newFadeIn, Duration newFadeOut) {
		var currentId = currentScene().map(Scene::id);
		var pendingId = Optional.ofNullable(pendingNext).filter(this::isScene).map(i -> scenes.get(i).id());
		this.scenes = List.copyOf(newScenes);
		this.fadeIn = newFadeIn;
		this.fadeOut = newFadeOut;
		if (currentId.isPresent()) {
			var index = indexOf(currentId.get());
			position = index >= 0 ? index : Math.min(position, scenes.size());
		} else if (position != READY) {
			position = Math.min(position, scenes.size());
		}
		pendingNext = pendingId.map(this::indexOf).filter(i -> i >= 0).orElse(null);
		if (transition != null && transition.target() >= 0 && transition.target() < scenes.size()) {
			transition = transition.withTarget(position);
		}
	}

	// --- commands --------------------------------------------------------------------------------------------

	/** Starts the next scene (or the scene chosen with {@link #setNext}). At the end of the show it does nothing. */
	public boolean go(Instant now, FadeBehaviors behaviors) {
		var target = nextPosition();
		if (target == null) {
			return false;
		}
		startTransition(now, target, behaviors);
		return true;
	}

	/** Returns to the previous scene with the same fades; before the first scene it does nothing. */
	public boolean back(Instant now, FadeBehaviors behaviors) {
		if (position == READY) {
			return false;
		}
		startTransition(now, position - 1, behaviors);
		return true;
	}

	/** Chooses the scene that the next GO starts. */
	public void setNext(long sceneId) {
		var index = indexOf(sceneId);
		if (index < 0) {
			throw new IllegalArgumentException("Scene " + sceneId + " is not in the cue list");
		}
		pendingNext = index;
	}

	/** Pauses or resumes fades and the auto-follow countdown. */
	public void togglePause(Instant now) {
		if (pausedAt == null) {
			pausedAt = now;
		} else {
			pausedTotal = pausedTotal.plus(Duration.between(pausedAt, now));
			pausedAt = null;
		}
	}

	/** Completes the running fade at once. */
	public boolean finish(Instant now) {
		if (transition == null) {
			return false;
		}
		transition = null;
		var v = virtualNow(now);
		if (stayStart.compareTo(v) > 0) {
			stayStart = v;
		}
		return true;
	}

	/**
	 * Advances time: ends finished fades and fires auto-follow.
	 *
	 * @return whether the state changed (for notifying clients)
	 */
	public boolean tick(Instant now, FadeBehaviors behaviors) {
		var v = virtualNow(now);
		var changed = false;
		if (transition != null && v.minus(transition.start()).compareTo(longestFade()) >= 0) {
			transition = null;
			changed = true;
		}
		var autoFollow = autoFollowAt();
		if (autoFollow.isPresent() && pausedAt == null && v.compareTo(autoFollow.get()) >= 0) {
			pendingNext = null;
			go(now, behaviors);
			changed = true;
		}
		return changed;
	}

	// --- output ----------------------------------------------------------------------------------------------

	/** The values of the playback at the given time. */
	public Map<Long, FixtureValues> output(Instant now, FadeBehaviors behaviors) {
		if (transition == null) {
			return valuesAt(position);
		}
		var elapsed = virtualNow(now).minus(transition.start());
		return Crossfade.mix(transition.from(), valuesAt(transition.target()), elapsed, fadeIn, fadeOut, behaviors);
	}

	// --- state -----------------------------------------------------------------------------------------------

	public int position() {
		return position;
	}

	public int sceneCount() {
		return scenes.size();
	}

	public List<Scene> scenes() {
		return scenes;
	}

	public boolean isPaused() {
		return pausedAt != null;
	}

	public Optional<Scene> currentScene() {
		return isScene(position) ? Optional.of(scenes.get(position)) : Optional.empty();
	}

	/** The scene the next GO starts, empty at the end. */
	public Optional<Scene> nextScene() {
		var next = nextPosition();
		return next != null && isScene(next) ? Optional.of(scenes.get(next)) : Optional.empty();
	}

	/** Whether the next GO fades to black at the end of the show. */
	public boolean nextIsEnd() {
		var next = nextPosition();
		return next != null && next == scenes.size();
	}

	public Optional<FadeStatus> fade(Instant now) {
		if (transition == null) {
			return Optional.empty();
		}
		return Optional.of(new FadeStatus(transition.fromSceneId(), virtualNow(now).minus(transition.start()),
				fadeIn, fadeOut));
	}

	/** Time left until auto-follow fires, if the current scene has one. */
	public Optional<Duration> autoFollowRemaining(Instant now) {
		return autoFollowAt().map(at -> {
			var remaining = at.minus(virtualNow(now));
			return remaining.isNegative() ? Duration.ZERO : remaining;
		});
	}

	public Duration fadeIn() {
		return fadeIn;
	}

	public Duration fadeOut() {
		return fadeOut;
	}

	/**
	 * @param fromSceneId scene that is fading out, empty if it was the dark start or an unrecorded look
	 * @param elapsed     time since GO (paused time excluded)
	 */
	public record FadeStatus(Optional<Long> fromSceneId, Duration elapsed, Duration fadeIn, Duration fadeOut) {
	}

	// --- internals -------------------------------------------------------------------------------------------

	private void startTransition(Instant now, int target, FadeBehaviors behaviors) {
		var v = virtualNow(now);
		var snapshot = output(now, behaviors);
		var fromSceneId = currentScene().map(Scene::id);
		transition = new Transition(snapshot, fromSceneId, target, v);
		position = target;
		pendingNext = null;
		stayStart = v.plus(fadeIn);
	}

	private Integer nextPosition() {
		if (pendingNext != null) {
			return pendingNext;
		}
		return position < scenes.size() ? position + 1 : null;
	}

	private Optional<Duration> autoFollowAt() {
		var isLastScene = position >= scenes.size() - 1;
		return currentScene()
				.filter(scene -> !isLastScene)
				.flatMap(scene -> scene.spec().autoFollow())
				.map(stayStart::plus);
	}

	private Map<Long, FixtureValues> valuesAt(int index) {
		return isScene(index) ? scenes.get(index).values() : Map.of();
	}

	private boolean isScene(int index) {
		return index >= 0 && index < scenes.size();
	}

	private int indexOf(long sceneId) {
		for (int i = 0; i < scenes.size(); i++) {
			if (scenes.get(i).id() == sceneId) {
				return i;
			}
		}
		return -1;
	}

	private Duration longestFade() {
		return fadeIn.compareTo(fadeOut) >= 0 ? fadeIn : fadeOut;
	}

	/** Time since an arbitrary origin that does not advance while paused. */
	private Duration virtualNow(Instant now) {
		var paused = pausedAt == null ? pausedTotal : pausedTotal.plus(Duration.between(pausedAt, now));
		return Duration.ofSeconds(now.getEpochSecond(), now.getNano()).minus(paused);
	}

	private record Transition(Map<Long, FixtureValues> from, Optional<Long> fromSceneId, int target, Duration start) {

		Transition withTarget(int newTarget) {
			return new Transition(from, fromSceneId, newTarget, start);
		}
	}
}
