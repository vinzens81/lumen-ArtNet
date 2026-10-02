package de.vinzens.lumen.adapter.web.live;

import de.vinzens.lumen.adapter.web.output.HealthResponse;
import de.vinzens.lumen.adapter.web.output.ValuesJson;
import de.vinzens.lumen.adapter.web.playback.PlaybackDtos.PlaybackResponse;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.output.port.in.ControlMasterUseCase;
import de.vinzens.lumen.application.output.port.in.ControlModeUseCase;
import de.vinzens.lumen.application.output.port.in.ControlProgrammerUseCase;
import de.vinzens.lumen.application.output.port.in.QueryHealthUseCase;
import de.vinzens.lumen.application.playback.port.in.ControlPlaybackUseCase;
import de.vinzens.lumen.domain.DomainValidationException;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * {@code health} (every second), {@code patch}, {@code scenes},
 * {@code nodes}, {@code projects} (the last four ask the client to reload via REST). Client → server: {@code programmer.set},
 * {@code master.set}, {@code blackout.set} and {@code playback.go/back/pause/finish} for low latency.
 */
@Component
class LiveSocketHandler extends TextWebSocketHandler implements AutoCloseable {

	static final long FLUSH_INTERVAL_MS = 33;
	private static final int SEND_TIME_LIMIT_MS = 2_000;
	private static final int BUFFER_SIZE_LIMIT = 512 * 1024;
	private static final Logger log = LoggerFactory.getLogger(LiveSocketHandler.class);

	private final ControlProgrammerUseCase programmer;
	private final ControlMasterUseCase master;
	private final ControlPlaybackUseCase playback;
	private final ControlModeUseCase mode;
	private final QueryHealthUseCase health;
	private final JsonMapper json;
	private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
	private final Set<StateTopic> dirty = EnumSet.noneOf(StateTopic.class);
	private final ScheduledExecutorService flusher = Executors.newSingleThreadScheduledExecutor(runnable -> {
		var thread = new Thread(runnable, "lumen-live-flush");
		thread.setDaemon(true);
		return thread;
	});

	LiveSocketHandler(ControlProgrammerUseCase programmer, ControlMasterUseCase master, ControlPlaybackUseCase playback,
			ControlModeUseCase mode, QueryHealthUseCase health, StateEvents events, JsonMapper json) {
		this.programmer = programmer;
		this.master = master;
		this.playback = playback;
		this.mode = mode;
		this.health = health;
		this.json = json;
		events.subscribe(this::markDirty);
		flusher.scheduleWithFixedDelay(this::flush, FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
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
			}
			case OUTPUT -> {
				var state = master.master();
				message.put("type", "master");
				message.put("level", state.level());
				message.put("blackout", state.blackout());
			}
			case PATCH -> message.put("type", "patch");
			case SCENES -> message.put("type", "scenes");
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
				StateTopic.HEALTH)) {
			send(safe, new TextMessage(json.writeValueAsString(message(topic))));
		}
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		sessions.remove(session.getId());
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) {
		try {
			var command = json.readValue(message.getPayload(), Command.class);
			switch (command.type() == null ? "" : command.type()) {
				case "programmer.set" -> programmer.setValues(
						command.fixtureIds() == null ? List.of() : command.fixtureIds(),
						ValuesJson.fromJson(command.values()));
				case "master.set" -> {
					if (command.level() == null) {
						throw new DomainValidationException(List.of("level is missing"));
					}
					master.setMasterLevel(command.level());
				}
				case "blackout.set" -> master.setBlackout(Boolean.TRUE.equals(command.active()));
				case "playback.go" -> playback.go();
				case "playback.back" -> playback.back();
				case "playback.pause" -> playback.togglePause();
				case "playback.finish" -> playback.finish();
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

	private void reply(WebSocketSession session, String error) {
		var target = sessions.getOrDefault(session.getId(), session);
		send(target, new TextMessage(json.writeValueAsString(Map.of("type", "error", "message", error))));
	}

	@Override
	public void close() {
		flusher.shutdownNow();
	}

	record Command(String type, List<Long> fixtureIds, Map<String, Double> values, Double level, Boolean active) {
	}
}
