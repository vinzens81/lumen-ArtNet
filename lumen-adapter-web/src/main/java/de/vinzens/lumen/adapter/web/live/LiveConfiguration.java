package de.vinzens.lumen.adapter.web.live;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration(proxyBeanMethods = false)
@EnableWebSocket
class LiveConfiguration implements WebSocketConfigurer {

	private final LiveSocketHandler handler;

	LiveConfiguration(LiveSocketHandler handler) {
		this.handler = handler;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		// same origin is always allowed; localhost ports cover the Vite dev server
		registry.addHandler(handler, "/ws").setAllowedOriginPatterns("http://localhost:[*]", "http://127.0.0.1:[*]");
	}
}
