package de.vinzens.lumen.adapter.web.live;

import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

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
		registry.addHandler(handler, "/ws")
				.addInterceptors(new HttpSessionReference())
				.setAllowedOriginPatterns("http://localhost:[*]", "http://127.0.0.1:[*]");
	}

	/**
	 * Keeps a reference to the HTTP session of the handshake, so commands can check whether the client unlocked
	 * setup. A client that unlocks later reconnects.
	 */
	static final class HttpSessionReference implements HandshakeInterceptor {

		static final String ATTRIBUTE = "lumen.httpSession";

		@Override
		public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
				WebSocketHandler wsHandler, Map<String, Object> attributes) {
			if (request instanceof ServletServerHttpRequest servlet) {
				var session = servlet.getServletRequest().getSession(false);
				if (session != null) {
					attributes.put(ATTRIBUTE, session);
				}
			}
			return true;
		}

		@Override
		public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
				Exception exception) {
		}

		static HttpSession of(Map<String, Object> attributes) {
			return attributes.get(ATTRIBUTE) instanceof HttpSession session ? session : null;
		}
	}
}
