package de.vinzens.lumen;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LiveSocketTest {

	@LocalServerPort
	int port;

	private final HttpClient http = HttpClient.newHttpClient();
	private WebSocket socket;

	@AfterEach
	void close() {
		if (socket != null) {
			socket.abort();
		}
	}

	@Test
	void sendsInitialStateAndBroadcastsChanges() throws Exception {
		var messages = connect();

		assertThat(type(messages.poll(5, TimeUnit.SECONDS))).isEqualTo("programmer");
		assertThat(type(messages.poll(5, TimeUnit.SECONDS))).isEqualTo("master");
		assertThat(type(messages.poll(5, TimeUnit.SECONDS))).isEqualTo("playback");
		assertThat(type(messages.poll(5, TimeUnit.SECONDS))).isEqualTo("mode");

		var response = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/output/master"))
				.header("Content-Type", "application/json")
				.PUT(HttpRequest.BodyPublishers.ofString("{\"level\": 0.25}"))
				.build(), HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).isEqualTo(200);

		var update = await(messages, "master");
		assertThat((Double) JsonPath.read(update, "$.level")).isEqualTo(0.25);
	}

	@Test
	void acceptsCommandsAndReportsErrors() throws Exception {
		var messages = connect();
		await(messages, "mode");

		socket.sendText("{\"type\": \"master.set\", \"level\": 0.75}", true).join();
		var update = await(messages, "master");
		assertThat((Double) JsonPath.read(update, "$.level")).isEqualTo(0.75);

		socket.sendText("{\"type\": \"master.set\", \"level\": 3}", true).join();
		var error = await(messages, "error");
		assertThat((String) JsonPath.read(error, "$.message")).contains("level must be between 0 and 1");

		socket.sendText("{\"type\": \"master.set\", \"level\": 1}", true).join();
	}

	@Test
	void streamsLiveOutputToSubscribers() throws Exception {
		var project = id(request("POST", "/api/projects", "{\"name\": \"Live output\"}"));
		request("POST", "/api/projects/" + project + "/activate", "");
		var universe = id(request("POST", "/api/projects/" + project + "/universes", "{\"number\": 1}"));
		var fixture = id(request("POST", "/api/projects/" + project + "/fixtures", """
				{"name": "D", "fixtureTypeId": "generic-dimmer", "mode": "1ch", "universeId": %d, "address": 3}
				""".formatted(universe)));
		var messages = connect();
		await(messages, "mode");

		socket.sendText("{\"type\": \"subscribe\", \"topics\": [\"output\", \"dmx:1\"]}", true).join();
		request("PATCH", "/api/programmer/fixtures/" + fixture, "{\"dimmer\": 1}");

		// both streams arrive in any order; wait until each shows the new value
		String dmx = null;
		String output = null;
		var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while ((dmx == null || output == null) && System.nanoTime() < deadline) {
			var message = messages.poll(100, TimeUnit.MILLISECONDS);
			if (message == null) {
				continue;
			}
			var type = JsonPath.<String>read(message, "$.type");
			if (type.equals("dmx") && JsonPath.<Integer>read(message, "$.values[2]") == 255) {
				dmx = message;
			} else if (type.equals("output") && message.contains("\"" + fixture + "\"")) {
				output = message;
			}
		}
		assertThat(dmx).as("dmx message with the new value").isNotNull();
		assertThat(output).as("output message with the fixture").isNotNull();
		assertThat(JsonPath.<Integer>read(dmx, "$.universe")).isEqualTo(1);

		socket.sendText("{\"type\": \"subscribe\", \"topics\": [\"stage\"]}", true).join();
		assertThat((String) JsonPath.read(await(messages, "error"), "$.message")).contains("Unknown live topic");
		request("DELETE", "/api/programmer", null);
	}

	private String request(String method, String path, String json) throws Exception {
		var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
				.header("Content-Type", "application/json")
				.method(method, json == null ? HttpRequest.BodyPublishers.noBody()
						: HttpRequest.BodyPublishers.ofString(json));
		var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).as(method + " " + path + ": " + response.body()).isLessThan(300);
		return response.body();
	}

	private static long id(String body) {
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private BlockingQueue<String> connect() {
		var messages = new LinkedBlockingQueue<String>();
		socket = http.newWebSocketBuilder()
				.connectTimeout(Duration.ofSeconds(5))
				.buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws"), new WebSocket.Listener() {
					private final StringBuilder buffer = new StringBuilder();

					@Override
					public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
						buffer.append(data);
						if (last) {
							messages.add(buffer.toString());
							buffer.setLength(0);
						}
						webSocket.request(1);
						return null;
					}
				})
				.join();
		return messages;
	}

	/** Skips other updates until a message of the given type arrives. */
	private static String await(BlockingQueue<String> messages, String type) throws InterruptedException {
		var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (System.nanoTime() < deadline) {
			var message = messages.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
			if (message != null && type.equals(JsonPath.read(message, "$.type"))) {
				return message;
			}
		}
		throw new AssertionError("no '" + type + "' message within 5 s");
	}

	private static String type(String message) {
		assertThat(message).as("message received").isNotNull();
		return JsonPath.read(message, "$.type");
	}
}
