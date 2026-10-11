package de.vinzens.lumen;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Definition of done of iteration 8: the server stops during scene 4 and starts again. In show mode the light is
 * back in scene 4 at once; in setup mode only after confirming.
 */
class ResumeAfterRestartTest {

	@TempDir
	Path dir;

	@Test
	void showContinuesAtTheSameSceneAfterARestart() throws Exception {
		var database = dir.resolve("lumen.db");
		long[] scenes = new long[5];

		try (var context = start(database)) {
			var http = client(context);
			var project = id(http.post().uri("/api/projects").body("{\"name\": \"Show\", \"fadeInMs\": 0, \"fadeOutMs\": 0}"));
			var universe = id(http.post().uri("/api/projects/" + project + "/universes").body("{\"number\": 1}"));
			var dimmer = id(http.post().uri("/api/projects/" + project + "/fixtures").body("""
					{"name": "Dimmer", "fixtureTypeId": "generic-dimmer", "mode": "1ch", "universeId": %d, "address": 1}
					""".formatted(universe)));
			for (int i = 0; i < scenes.length; i++) {
				http.patch().uri("/api/programmer/fixtures/" + dimmer).body("{\"dimmer\": " + (i + 1) / 10.0 + "}")
						.retrieve().toBodilessEntity();
				scenes[i] = id(http.post().uri("/api/projects/" + project + "/scenes")
						.body("{\"name\": \"Scene " + (i + 1) + "\", \"record\": true}"));
			}
			http.put().uri("/api/mode").body("{\"mode\": \"show\"}").retrieve().toBodilessEntity();
			for (int i = 0; i < 4; i++) {
				http.post().uri("/api/playback/go").retrieve().toBodilessEntity();
				Thread.sleep(250); // double-GO guard
			}
			assertThat(dmx(http)).isEqualTo(102); // scene 4: 40 %
		}

		try (var context = start(database)) {
			var http = client(context);
			assertThat(get(http, "/api/mode", "$.mode")).as("still in show mode").isEqualTo("show");
			assertThat(get(http, "/api/playback", "$.currentSceneId")).as("resumed without confirming")
					.isEqualTo((int) scenes[3]);
			assertThat(get(http, "/api/playback", "$.resumeSceneId")).isNull();
			assertThat(dmx(http)).isEqualTo(102);

			http.put().uri("/api/mode").body("{\"mode\": \"setup\"}").retrieve().toBodilessEntity();
		}

		try (var context = start(database)) {
			var http = client(context);
			assertThat(get(http, "/api/playback", "$.position")).as("setup mode only offers").isEqualTo(-1);
			assertThat(get(http, "/api/playback", "$.resumeSceneId")).isEqualTo((int) scenes[3]);
			assertThat(dmx(http)).as("dark until confirmed").isZero();

			http.post().uri("/api/playback/resume").retrieve().toBodilessEntity();

			assertThat(get(http, "/api/playback", "$.currentSceneId")).isEqualTo((int) scenes[3]);
			assertThat(dmx(http)).isEqualTo(102);
		}
	}

	private static ConfigurableApplicationContext start(Path database) {
		return new SpringApplicationBuilder(LumenApplication.class)
				.run("--server.port=0", "--lumen.database-file=" + database);
	}

	private static RestClient client(ConfigurableApplicationContext context) {
		var port = context.getEnvironment().getProperty("local.server.port");
		return RestClient.builder()
				.baseUrl("http://127.0.0.1:" + port)
				.defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				.build();
	}

	private static long id(RestClient.RequestBodySpec request) {
		var body = request.retrieve().body(String.class);
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private static Object get(RestClient http, String uri, String path) {
		return JsonPath.read(http.get().uri(uri).retrieve().body(String.class), path);
	}

	private static int dmx(RestClient http) {
		List<Integer> values = JsonPath.read(http.get().uri("/api/output/universes/1/dmx").retrieve().body(String.class),
				"$.values");
		return values.getFirst();
	}
}
