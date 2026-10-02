package de.vinzens.lumen.application.events;

import java.lang.System.Logger.Level;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Publishes state changes to subscribers, e.g. the WebSocket adapter that keeps all clients in sync.
 * Subscribers are called synchronously and must return quickly; failures are logged and isolated.
 */
public class StateEvents {

	private static final System.Logger LOG = System.getLogger(StateEvents.class.getName());

	private final List<Consumer<StateTopic>> subscribers = new CopyOnWriteArrayList<>();

	public void subscribe(Consumer<StateTopic> subscriber) {
		subscribers.add(subscriber);
	}

	public void publish(StateTopic topic) {
		for (var subscriber : subscribers) {
			try {
				subscriber.accept(topic);
			} catch (RuntimeException e) {
				LOG.log(Level.WARNING, "State subscriber failed for " + topic, e);
			}
		}
	}
}
