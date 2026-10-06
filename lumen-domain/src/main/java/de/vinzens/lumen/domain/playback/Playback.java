package de.vinzens.lumen.domain.playback;

import de.vinzens.lumen.domain.composition.ActiveComposition;
import de.vinzens.lumen.domain.composition.CompositionMixer;
import de.vinzens.lumen.domain.composition.FixtureAttributes;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.scene.FadeCurve;
import de.vinzens.lumen.domain.scene.Scene;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Plays a cue list: GO starts the next scene, fading out what is on stage while fading in the new scene.
 *
 * <p>Positions: {@code -1} = before the first scene (dark), {@code 0..n-1} = a scene, {@code n} = end of the show
 * (dark). All timing runs on a virtual clock that stands still while the playback is paused, so fades and the
 * auto-follow countdown freeze and continue seamlessly.
 *
 * <p>A GO during a running fade freezes the current output as the outgoing look, so even a fast double GO
 * never jumps. Not thread-safe – callers synchronize.
 *
 * <p>Each transition uses the fade times of the scene it goes to ({@link de.vinzens.lumen.domain.scene.SceneTiming}),
 * falling back to the project's times; the fade to black at the end always uses the project's.
 *
 * <p>A scene is a set of compositions. They run on the virtual clock from the GO of their scene and keep running
 * while their scene fades out. A composition that is in the next scene too continues with the same phase: it is not
 * faded out and in, only its level and values glide over the fade-in time.
 *
 * <p>Light markers in the script split a scene into parts ({@link #goToSection}): each part has compositions of its
 * own, reached with a fade of the scene's times inside the same scene. What is in both parts continues, so identical
 * light does not move.
 */
public final class Playback {

	public static final int READY = -1;

	private List<Scene> scenes;
	private Map<Long, List<ActiveComposition>> compositions;
	/** Per scene id the compositions of the parts after its markers (index 0 = after the first marker). */
	private Map<Long, List<List<ActiveComposition>>> parts = Map.of();
	/** The part of the current scene on stage: 0 = its start, 1… = after marker 1… */
	private int section;
	private Duration fadeIn;
	private Duration fadeOut;
	private FadeCurve fadeCurve = FadeCurve.LINEAR;
	private FixtureAttributes attributes = FixtureAttributes.ALL;
	/** Compositions of the scene at the current position, with their start time. */
	private List<Running> running = List.of();

	private int position = READY;
	private Integer pendingNext;
	private Transition transition;
	/** A scene deleted while on stage: kept in {@link #scenes} until the playback moves on, so nothing jumps. */
	private Long deletedOnStage;
	/** Virtual time at which the stay of the current scene began (fade-in complete). */
	private Duration stayStart = Duration.ZERO;

	private Instant pausedAt;
	private Duration pausedTotal = Duration.ZERO;

	public Playback(List<Scene> scenes, Duration fadeIn, Duration fadeOut) {
		this(scenes, Map.of(), fadeIn, fadeOut);
	}

	/** @param compositions resolved compositions per scene id, in their order within the scene */
	public Playback(List<Scene> scenes, Map<Long, List<ActiveComposition>> compositions, Duration fadeIn,
			Duration fadeOut) {
		this.scenes = List.copyOf(scenes);
		this.compositions = Map.copyOf(compositions);
		this.fadeIn = Objects.requireNonNull(fadeIn, "fadeIn");
		this.fadeOut = Objects.requireNonNull(fadeOut, "fadeOut");
	}

	/** The compositions of the parts after the light markers of the scenes, by scene id. */
	public void useSections(Map<Long, List<List<ActiveComposition>>> sectionParts) {
		this.parts = Map.copyOf(sectionParts);
		section = Math.min(section, sectionCount());
	}

	/** Which attributes the patched fixtures have; compositions only set those. */
	public void useFixtures(FixtureAttributes fixtureAttributes) {
		this.attributes = Objects.requireNonNull(fixtureAttributes, "fixtureAttributes");
	}

	/** The project's fade curve, for transitions into scenes without their own and the fade to black at the end. */
	public void useFadeCurve(FadeCurve curve) {
		this.fadeCurve = Objects.requireNonNull(curve, "curve");
	}

	/**
	 * Replaces the cue list and the compositions of the scenes, e.g. after scenes were edited. Nothing on stage
	 * changes: the current scene is kept by id, the end of the show stays dark, and a scene deleted while on stage
	 * stays there until the next GO, which plays the scene that followed it. Compositions that are still in the
	 * current scene keep running with their phase, new ones start now.
	 */
	public void reload(List<Scene> newScenes, Map<Long, List<ActiveComposition>> newCompositions, Duration newFadeIn,
			Duration newFadeOut, Instant now) {
		var current = currentScene();
		var pendingId = Optional.ofNullable(pendingNext).filter(this::isScene).map(i -> scenes.get(i).id());
		var cueList = new ArrayList<>(newScenes);
		var cueCompositions = new HashMap<>(newCompositions);
		deletedOnStage = null;
		if (current.isPresent() && newScenes.stream().noneMatch(scene -> scene.id() == current.get().id())) {
			var deleted = current.get();
			var number = deleted.spec().number();
			var index = 0;
			while (index < cueList.size() && cueList.get(index).spec().number().compareTo(number) < 0) {
				index++;
			}
			cueList.add(index, deleted);
			cueCompositions.put(deleted.id(), compositions.getOrDefault(deleted.id(), List.of()));
			deletedOnStage = deleted.id();
		}
		this.scenes = List.copyOf(cueList);
		this.compositions = Map.copyOf(cueCompositions);
		this.fadeIn = newFadeIn;
		this.fadeOut = newFadeOut;
		if (current.isPresent()) {
			position = indexOf(current.get().id());
		} else if (position != READY) {
			position = scenes.size(); // the end of the show
		}
		pendingNext = pendingId.map(this::indexOf).filter(i -> i >= 0).orElse(null);
		if (transition != null) {
			transition = transition.withTarget(position);
		}
		var started = new HashMap<Long, Duration>();
		running.forEach(r -> started.put(r.composition().id(), r.startedAt()));
		var v = virtualNow(now);
		// the current scene stays with its part, as far as the part still exists
		section = current.isEmpty() ? 0 : Math.min(section, sectionCount());
		running = compositionsAt(position).stream()
				.map(c -> new Running(c, started.getOrDefault(c.id(), v)))
				.toList();
		if (transition != null) {
			var ids = new HashSet<Long>();
			running.forEach(r -> ids.add(r.composition().id()));
			var continuing = new LinkedHashMap<>(transition.continuing());
			continuing.keySet().retainAll(ids);
			transition = transition.withContinuing(continuing);
		}
	}

	// --- commands --------------------------------------------------------------------------------------------

	/** Starts the next scene (or the scene chosen with {@link #setNext}). At the end of the show it does nothing. */
	public boolean go(Instant now, FadeBehaviors behaviors) {
		var target = nextPosition();
		if (target == null) {
			return false;
		}
		startTransition(now, target, 0, behaviors);
		return true;
	}

	/**
	 * Fades to another part of the current scene (0 = its start, n = after the n-th light marker), with the fade times
	 * of the scene. Compositions in both parts continue. Does nothing outside a scene or for the part on stage.
	 */
	public boolean goToSection(int target, Instant now, FadeBehaviors behaviors) {
		if (!isScene(position) || target == section || target < 0 || target > sectionCount()) {
			return false;
		}
		startTransition(now, position, target, behaviors);
		return true;
	}

	/** The part of the current scene on stage: 0 = its start, n = after the n-th light marker. */
	public int section() {
		return section;
	}

	/** Returns to the previous scene with the same fades; before the first scene it does nothing. */
	public boolean back(Instant now, FadeBehaviors behaviors) {
		if (position == READY) {
			return false;
		}
		startTransition(now, position - 1, 0, behaviors);
		return true;
	}

	/**
	 * Puts a scene on stage at once, without a fade, e.g. to resume the show after a restart. Its auto-follow
	 * countdown starts now.
	 */
	public void restore(long sceneId, Instant now) {
		var index = indexOf(sceneId);
		if (index < 0) {
			throw new IllegalArgumentException("Scene " + sceneId + " is not in the cue list");
		}
		position = index;
		section = 0;
		transition = null;
		pendingNext = null;
		stayStart = virtualNow(now);
		var v = stayStart;
		running = compositionsAt(index).stream().map(c -> new Running(c, v)).toList();
		dropDeletedScene();
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
		if (transition != null && v.minus(transition.start()).compareTo(transition.times().longest()) >= 0) {
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
		var v = virtualNow(now);
		if (transition == null) {
			return look(valuesAt(position), running, v);
		}
		var elapsed = v.minus(transition.start());
		var continuing = transition.continuing();
		var from = look(transition.from(), transition.fromRunning(), v);
		var incoming = running.stream().filter(r -> !continuing.containsKey(r.composition().id())).toList();
		var to = look(valuesAt(transition.target()), incoming, v);
		var times = transition.times();
		var mixed = Crossfade.mix(from, to, elapsed, times.fadeIn(), times.fadeOut(), times.delay(), times.curve(),
				behaviors, transition.darkAtGo());

		// continuing compositions are not faded out and in: they glide from their old to their new values
		var progress = times.curve().progress(Crossfade.progress(elapsed.minus(times.delay()), times.fadeIn()));
		for (var current : running) {
			var previous = continuing.get(current.composition().id());
			if (previous == null) {
				continue;
			}
			var blended = blend(contribution(previous, v), contribution(current, v), progress);
			var level = previous.composition().level()
					+ (current.composition().level() - previous.composition().level()) * progress;
			mixed = CompositionMixer.apply(mixed, blended, current.composition().pattern(), level, attributes);
		}
		return mixed;
	}

	/** Names of the compositions running in the current scene, for display. */
	public List<ActiveComposition> runningCompositions() {
		return running.stream().map(Running::composition).toList();
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
		var times = transition.times();
		return Optional.of(new FadeStatus(transition.fromSceneId(), virtualNow(now).minus(transition.start()),
				times.fadeIn(), times.fadeOut(), times.delay()));
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
	 * @param fadeIn      fade-in time of this transition, starting after the delay
	 * @param fadeOut     fade-out time of this transition, starting at GO
	 */
	public record FadeStatus(Optional<Long> fromSceneId, Duration elapsed, Duration fadeIn, Duration fadeOut,
			Duration delay) {
	}

	// --- internals -------------------------------------------------------------------------------------------

	private void startTransition(Instant now, int target, int targetSection, FadeBehaviors behaviors) {
		var v = virtualNow(now);
		var current = output(now, behaviors);
		var darkAtGo = new HashSet<Long>();
		current.forEach((fixtureId, values) -> {
			if (Crossfade.isDark(values)) {
				darkAtGo.add(fixtureId);
			}
		});
		// a single GO lets the outgoing compositions run on; a GO during a fade freezes everything
		var fadeRunning = transition == null;
		var from = fadeRunning ? valuesAt(position) : current;

		var next = new ArrayList<Running>();
		var continuing = new LinkedHashMap<Long, Running>();
		var taken = new HashSet<Running>();
		for (var composition : compositionsAt(target, targetSection)) {
			var match = running.stream()
					.filter(r -> !taken.contains(r) && composition.continues(r.composition()))
					.findFirst();
			match.ifPresent(taken::add);
			match.ifPresent(r -> continuing.put(composition.id(), r));
			next.add(new Running(composition, match.map(Running::startedAt).orElse(v)));
		}
		var fromRunning = fadeRunning ? running.stream().filter(r -> !taken.contains(r)).toList() : List.<Running>of();

		var fromSceneId = currentScene().map(Scene::id);
		var times = timesInto(target);
		transition = new Transition(from, fromRunning, continuing, Set.copyOf(darkAtGo), fromSceneId, target, v,
				times);
		running = List.copyOf(next);
		position = target;
		section = targetSection;
		pendingNext = null;
		stayStart = v.plus(times.delay()).plus(times.fadeIn());
		dropDeletedScene();
	}

	/** Removes a scene deleted while on stage once the playback has left it. */
	private void dropDeletedScene() {
		if (deletedOnStage == null) {
			return;
		}
		var index = indexOf(deletedOnStage);
		if (index == position) {
			return;
		}
		var remaining = new ArrayList<>(scenes);
		remaining.remove(index);
		scenes = List.copyOf(remaining);
		var remainingCompositions = new HashMap<>(compositions);
		remainingCompositions.remove(deletedOnStage);
		compositions = Map.copyOf(remainingCompositions);
		deletedOnStage = null;
		if (position > index) {
			position--;
			if (transition != null) {
				transition = transition.withTarget(position);
			}
		}
	}

	/** Compositions on stage at a position: of the current part for the current scene, else of the start. */
	private List<ActiveComposition> compositionsAt(int index) {
		return compositionsAt(index, index == position ? section : 0);
	}

	private List<ActiveComposition> compositionsAt(int index, int part) {
		if (!isScene(index)) {
			return List.of();
		}
		var id = scenes.get(index).id();
		if (part == 0) {
			return compositions.getOrDefault(id, List.of());
		}
		var sceneParts = parts.getOrDefault(id, List.of());
		return part <= sceneParts.size() ? sceneParts.get(part - 1) : List.of();
	}

	/** Number of light markers of the current scene. */
	private int sectionCount() {
		return isScene(position) ? parts.getOrDefault(scenes.get(position).id(), List.of()).size() : 0;
	}

	/** Static values with the running compositions laid over them. */
	private Map<Long, FixtureValues> look(Map<Long, FixtureValues> base, List<Running> compositionsToApply,
			Duration v) {
		var result = base;
		for (var r : compositionsToApply) {
			result = CompositionMixer.apply(result, r.composition(), seconds(r, v), attributes);
		}
		return result;
	}

	private Map<Long, FixtureValues> contribution(Running r, Duration v) {
		return CompositionMixer.contribution(r.composition(), seconds(r, v), attributes);
	}

	private static double seconds(Running r, Duration v) {
		return Math.max(0, v.minus(r.startedAt()).toNanos() / 1e9);
	}

	/** Attribute-wise linear blend of two contributions; fixtures only one side has keep their values. */
	private static Map<Long, FixtureValues> blend(Map<Long, FixtureValues> from, Map<Long, FixtureValues> to,
			double progress) {
		var result = new HashMap<Long, FixtureValues>();
		var fixtures = new HashSet<>(from.keySet());
		fixtures.addAll(to.keySet());
		for (var fixtureId : fixtures) {
			var a = from.getOrDefault(fixtureId, FixtureValues.EMPTY);
			var b = to.getOrDefault(fixtureId, FixtureValues.EMPTY);
			var values = new HashMap<>(a.values());
			b.values().forEach((attribute, value) -> values.merge(attribute, value,
					(old, target) -> old + (target - old) * progress));
			result.put(fixtureId, new FixtureValues(values));
		}
		return result;
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

	/** Scenes have no static values of their own: everything comes from their compositions. */
	private Map<Long, FixtureValues> valuesAt(int index) {
		return Map.of();
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

	/** Fade times of the transition to a position: the scene's own, otherwise the project's. */
	private Times timesInto(int target) {
		if (!isScene(target)) {
			return new Times(fadeIn, fadeOut, Duration.ZERO, fadeCurve);
		}
		var timing = scenes.get(target).spec().timing();
		return new Times(timing.fadeInOr(fadeIn), timing.fadeOutOr(fadeOut), timing.delay(),
				timing.curveOr(fadeCurve));
	}

	/** Time since an arbitrary origin that does not advance while paused. */
	private Duration virtualNow(Instant now) {
		var paused = pausedAt == null ? pausedTotal : pausedTotal.plus(Duration.between(pausedAt, now));
		return Duration.ofSeconds(now.getEpochSecond(), now.getNano()).minus(paused);
	}

	/** Fade times and curve of one transition, fixed at GO. */
	private record Times(Duration fadeIn, Duration fadeOut, Duration delay, FadeCurve curve) {

		Duration longest() {
			var in = delay.plus(fadeIn);
			return in.compareTo(fadeOut) >= 0 ? in : fadeOut;
		}
	}

	/** A composition playing since {@code startedAt} (virtual time). */
	private record Running(ActiveComposition composition, Duration startedAt) {
	}

	/**
	 * @param from        static outgoing look (the old scene, or a frozen snapshot after a double GO)
	 * @param fromRunning compositions of the old scene that fade out while they keep running
	 * @param continuing  new composition id → the old running composition it continues
	 * @param darkAtGo    fixtures that were dark at GO: their color jumps instead of fading
	 */
	private record Transition(Map<Long, FixtureValues> from, List<Running> fromRunning,
			Map<Long, Running> continuing, Set<Long> darkAtGo, Optional<Long> fromSceneId, int target,
			Duration start, Times times) {

		Transition withTarget(int newTarget) {
			return new Transition(from, fromRunning, continuing, darkAtGo, fromSceneId, newTarget, start, times);
		}

		Transition withContinuing(Map<Long, Running> newContinuing) {
			return new Transition(from, fromRunning, newContinuing, darkAtGo, fromSceneId, target, start, times);
		}
	}
}
