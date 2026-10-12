package de.vinzens.lumen.adapter.web.live;

import de.vinzens.lumen.adapter.web.access.ConfigLock;
import de.vinzens.lumen.adapter.web.output.HealthResponse;
import de.vinzens.lumen.adapter.web.output.PresetRefsJson;
import de.vinzens.lumen.adapter.web.output.ValuesJson;
import de.vinzens.lumen.adapter.web.party.PartyDtos.PartyResponse;
import de.vinzens.lumen.adapter.web.playback.PlaybackDtos.PlaybackResponse;
import de.vinzens.lumen.application.NotFoundException;
import de.vinzens.lumen.application.composition.port.in.PreviewCompositionUseCase;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.output.port.in.ControlMasterUseCase;
import de.vinzens.lumen.application.output.port.in.ControlModeUseCase;
import de.vinzens.lumen.application.output.port.in.ControlProgrammerUseCase;
import de.vinzens.lumen.application.output.port.in.QueryHealthUseCase;
import de.vinzens.lumen.application.output.port.in.QueryOutputUseCase;
import de.vinzens.lumen.application.party.port.in.ControlPartyUseCase;
import de.vinzens.lumen.application.playback.port.in.ControlPlaybackUseCase;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.party.PartyPlace;
import de.vinzens.lumen.domain.party.PartyPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Keeps all connected clients in sync. State changes are collected and flushed at most every
 * {@value #FLUSH_INTERVAL_MS} ms, so fast fader moves do not flood the clients.
 *
 * <p>Server → client: {@code programmer} (all values), {@code master}, {@code playback}, {@code mode},
 * {@code party} (fields of the matrix that are on), {@code health} (every second), {@code partyMatrix}, {@code compositions},
 * {@code library}, {@code access}, {@code patch}, {@code scenes}, {@code nodes}, {@code projects}, {@code presets},
 * {@code faders} (the last ten ask the client to reload via REST). Client → server: {@code programmer.set},
 * {@code master.set}, {@code blackout.set}, {@code playback.go/back/pause/finish}, {@code playback.script} (with
 * {@code sceneId} and {@code line}), {@code keys.press/release} (with {@code slot}) and {@code fader.set} (with
 * {@code slot} and {@code level}) and {@code party.slide} (a gradient or pad field: {@code page}, {@code row},
 * {@code column}, {@code x} and – on a pad – {@code y}, both 0–1) for low latency.
 *
 * <p>Live output: after {@code {"type":"subscribe","topics":["output","dmx:1","preview:7"]}} a client gets
 * {@code output} (attribute values as sent, before the grand master), {@code dmx} (512 channel values of a universe)
 * and {@code preview} (a composition evaluated for the editor, not on stage) at most every
 * {@value #STREAM_INTERVAL_MS} ms, only when they changed.
 */
@Component
class LiveSocketHandler extends TextWebSocketHandler implements AutoCloseable {

	static final long FLUSH_INTERVAL_MS = 33;
	/** Live output for stage view and DMX monitor is sent at most this often (only when it changed). */
	static final long STREAM_INTERVAL_MS = 100;
	private static final int SEND_TIME_LIMIT_MS = 2_000;
	private static final int BUFFER_SIZE_LIMIT = 512 * 1024;
	private static final Logger log = LoggerFactory.getLogger(LiveSocketHandler.class);

	private final ControlProgrammerUseCase programmer;
	private final ControlMasterUseCase master;
	private final ControlPlaybackUseCase playback;
	private final ControlModeUseCase mode;
	private final QueryHealthUseCase health;
	private final QueryOutputUseCase output;
	private final PreviewCompositionUseCase preview;
	private final ControlPartyUseCase party;
	private final ConfigLock lock;
	private final JsonMapper json;
	private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
	private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();
	private final Set<StateTopic> dirty = EnumSet.noneOf(StateTopic.class);
	private final ScheduledExecutorService flusher = Executors.newSingleThreadScheduledExecutor(runnable -> {
		var thread = new Thread(runnable, "lumen-live-flush");
		thread.setDaemon(true);
		return thread;
	});

	LiveSocketHandler(ControlProgrammerUseCase programmer, ControlMasterUseCase master, ControlPlaybackUseCase playback,
			ControlModeUseCase mode, QueryHealthUseCase health, QueryOutputUseCase output,
			PreviewCompositionUseCase preview, ControlPartyUseCase party, ConfigLock lock,
			StateEvents events, JsonMapper json) {
		this.programmer = programmer;
		this.master = master;
		this.playback = playback;
		this.mode = mode;
		this.health = health;
		this.output = output;
		this.preview = preview;
		this.party = party;
		this.lock = lock;
		this.json = json;
		events.subscribe(this::markDirty);
		flusher.scheduleWithFixedDelay(this::flush, FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
		flusher.scheduleWithFixedDelay(this::stream, STREAM_INTERVAL_MS, STREAM_INTERVAL_MS, TimeUnit.MILLISECONDS);
	}

	/** Sends live output to the clients that subscribed to it, if it changed since they got it last. */
	private void stream() {
		if (subscriptions.isEmpty()) {
			return;
		}
		try {
			var texts = new HashMap<String, String>();
			subscriptions.forEach((sessionId, subscription) -> {
				var session = sessions.get(sessionId);
				if (session == null) {
					return;
				}
				for (var topic : subscription.topics()) {
					var text = texts.computeIfAbsent(topic, this::streamMessage);
					if (text != null && !text.equals(subscription.lastSent().put(topic, text))) {
						send(session, new TextMessage(text));
					}
				}
			});
		} catch (RuntimeException e) {
			log.warn("Live output stream failed", e);
		}
	}

	/** @return the message for a stream topic, or null if there is nothing to send (e.g. unknown universe) */
	private String streamMessage(String topic) {
		var message = new LinkedHashMap<String, Object>();
		if (topic.equals("output")) {
			message.put("type", "output");
			message.put("values", ValuesJson.toJson(output.outputValues()));
		} else if (topic.startsWith("dmx:")) {
			var universe = Integer.parseInt(topic.substring(4));
			var frame = output.lastFrame(universe);
			if (frame.isEmpty()) {
				return null;
			}
			message.put("type", "dmx");
			message.put("universe", universe);
			message.put("values", frame.get().toIntArray());
		} else if (topic.startsWith("preview:")) {
			var compositionId = Long.parseLong(topic.substring(8));
			try {
				message.put("type", "preview");
				message.put("compositionId", compositionId);
				message.put("values", ValuesJson.toJson(preview.previewValues(compositionId)));
			} catch (NotFoundException e) {
				return null;
			}
		} else {
			return null;
		}
		return json.writeValueAsString(message);
	}

	private void markDirty(StateTopic topic) {
		synchronized (dirty) {
			dirty.add(topic);
		}
	}

	private void flush() {
		Set<StateTopic> topics;
		synchronized (dirty) {
			if (dirty.isEmpty() || sessions.isEmpty()) {
				dirty.clear();
				return;
			}
			topics = EnumSet.copyOf(dirty);
			dirty.clear();
		}
		try {
			for (var topic : topics) {
				broadcast(message(topic));
			}
		} catch (RuntimeException e) {
			log.warn("Live update failed", e);
		}
	}

	private Map<String, Object> message(StateTopic topic) {
		var message = new LinkedHashMap<String, Object>();
		switch (topic) {
			case PROGRAMMER -> {
				message.put("type", "programmer");
				message.put("values", ValuesJson.toJson(programmer.programmerValues()));
				message.put("presets", PresetRefsJson.toJson(programmer.programmerPresets()));
			}
			case OUTPUT -> {
				var state = master.master();
				message.put("type", "master");
				message.put("level", state.level());
				message.put("blackout", state.blackout());
			}
			case PATCH -> message.put("type", "patch");
			case SCENES -> message.put("type", "scenes");
			case LOOKS -> message.put("type", "looks");
			case NODES -> message.put("type", "nodes");
			case PLAYBACK -> {
				message.put("type", "playback");
				message.put("status", PlaybackResponse.from(playback.status()));
			}
			case MODE -> {
				message.put("type", "mode");
				message.put("mode", mode.mode().name().toLowerCase(Locale.ROOT));
			}
			case PROJECTS -> message.put("type", "projects");
			case ACCESS -> message.put("type", "access");
			case LIBRARY -> message.put("type", "library");
			// the editor shows what is on stage with the compositions
			case COMPOSITIONS, STAGE_PREVIEW -> message.put("type", "compositions");
			case PARTY -> {
				message.put("type", "party");
				message.put("status", PartyResponse.from(party.status()));
			}
			case PARTY_MATRIX -> message.put("type", "partyMatrix");
			case PRESETS -> message.put("type", "presets");
			case FADERS -> message.put("type", "faders");
			case MOVES -> message.put("type", "moves");
			case HEALTH -> {
				message.put("type", "health");
				message.put("health", HealthResponse.from(health.health()));
			}
		}
		return message;
	}

	private void broadcast(Map<String, Object> message) {
		var text = new TextMessage(json.writeValueAsString(message));
		sessions.values().forEach(session -> send(session, text));
	}

	private void send(WebSocketSession session, TextMessage text) {
		try {
			session.sendMessage(text);
		} catch (IOException | IllegalStateException e) {
			log.debug("Dropping live update for session {}", session.getId(), e);
		}
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		var safe = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT);
		sessions.put(session.getId(), safe);
		for (var topic : List.of(StateTopic.PROGRAMMER, StateTopic.OUTPUT, StateTopic.PLAYBACK, StateTopic.MODE,
				StateTopic.PARTY, StateTopic.HEALTH)) {
			send(safe, new TextMessage(json.writeValueAsString(message(topic))));
		}
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		sessions.remove(session.getId());
		subscriptions.remove(session.getId());
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) {
		try {
			var command = json.readValue(message.getPayload(), Command.class);
			switch (command.type() == null ? "" : command.type()) {
				case "programmer.set" -> {
					lock.requireUnlocked(LiveConfiguration.HttpSessionReference.of(session.getAttributes()));
					programmer.setValues(command.fixtureIds() == null ? List.of() : command.fixtureIds(),
							ValuesJson.fromJson(command.values()));
				}
				case "master.set" -> {
					if (command.level() == null) {
						throw new DomainValidationException(List.of("level is missing"));
					}
					master.setMasterLevel(command.level());
				}
				case "blackout.set" -> master.setBlackout(Boolean.TRUE.equals(command.active()));
				case "subscribe" -> subscribe(session, command.topics());
				case "keys.press" -> playback.pressKey(requireSlot(command));
				case "keys.release" -> playback.releaseKey(requireSlot(command));
				case "fader.set" -> {
					if (command.level() == null) {
						throw new DomainValidationException(List.of("level is required"));
					}
					playback.setFader(requireSlot(command), command.level());
				}
				case "party.slide" -> {
					if (command.page() == null || command.row() == null || command.column() == null
							|| command.x() == null) {
						throw new DomainValidationException(List.of("page, row, column and x are required"));
					}
					lock.requireUnlocked(LiveConfiguration.HttpSessionReference.of(session.getAttributes()));
					party.slide(new PartyPlace(command.page(), command.row(), command.column()),
							new PartyPoint(command.x(), Objects.requireNonNullElse(command.y(), 0.5)));
				}
				case "playback.go" -> playback.go();
				case "playback.back" -> playback.back();
				case "playback.pause" -> playback.togglePause();
				case "playback.finish" -> playback.finish();
				case "playback.script" -> {
					if (command.sceneId() == null || command.line() == null) {
						throw new DomainValidationException(List.of("sceneId and line are required"));
					}
					playback.setScriptLine(command.sceneId(), command.line(), Optional.ofNullable(command.section()));
				}
				default -> throw new DomainValidationException(List.of("Unknown command '" + command.type() + "'"));
			}
		} catch (DomainValidationException e) {
			reply(session, String.join("; ", e.errors()));
		} catch (JacksonException e) {
			reply(session, "Invalid message: " + e.getOriginalMessage());
		} catch (RuntimeException e) {
			reply(session, e.getMessage());
		}
	}

	/** Replaces the live output topics of a client: {@code output} and {@code dmx:<universe number>}. */
	private void subscribe(WebSocketSession session, List<String> topics) {
		var valid = new LinkedHashSet<String>();
		for (var topic : topics == null ? List.<String>of() : topics) {
			if (!topic.equals("output") && !topic.matches("dmx:\\d{1,4}") && !topic.matches("preview:\\d{1,18}")) {
				throw new DomainValidationException(List.of("Unknown live topic '" + topic + "'"));
			}
			valid.add(topic);
		}
		if (valid.isEmpty()) {
			subscriptions.remove(session.getId());
		} else {
			subscriptions.put(session.getId(), new Subscription(Set.copyOf(valid), new ConcurrentHashMap<>()));
		}
	}

	private static int requireSlot(Command command) {
		if (command.slot() == null) {
			throw new DomainValidationException(List.of("slot is missing"));
		}
		return command.slot();
	}

	private void reply(WebSocketSession session, String error) {
		var target = sessions.getOrDefault(session.getId(), session);
		send(target, new TextMessage(json.writeValueAsString(Map.of("type", "error", "message", error))));
	}

	@Override
	public void close() {
		flusher.shutdownNow();
	}

	record Command(String type, List<Long> fixtureIds, Map<String, Double> values, Double level, Boolean active,
			List<String> topics, Integer slot, Long sceneId, Integer line, Integer section, Integer page,
			Integer row, Integer column, Double x, Double y) {
	}

	/** @param lastSent topic → message last sent to this client */
	private record Subscription(Set<String> topics, Map<String, String> lastSent) {
	}
}
