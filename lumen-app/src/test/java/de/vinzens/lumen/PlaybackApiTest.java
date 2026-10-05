package de.vinzens.lumen;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class PlaybackApiTest {

	@Autowired
	MockMvcTester mvc;

	private long dawn;
	private long noon;
	private long night;

	@BeforeEach
	void recordAShow() throws Exception {
		var project = id(post("/api/projects", "{\"name\": \"Show\", \"fadeInMs\": 0, \"fadeOutMs\": 0}"));
		assertThat(post("/api/projects/" + project + "/activate", "")).hasStatusOk();
		var universe = id(post("/api/projects/" + project + "/universes", "{\"number\": 1}"));
		var a = id(post("/api/projects/" + project + "/fixtures", fixture("A", universe, 1)));
		var b = id(post("/api/projects/" + project + "/fixtures", fixture("B", universe, 2)));
		var scenes = "/api/projects/" + project + "/scenes";

		assertThat(mvc.delete().uri("/api/programmer")).hasStatus(204);
		patch("/api/programmer/fixtures/" + a, "{\"dimmer\": 1}");
		dawn = id(post(scenes, "{\"name\": \"Dawn\", \"record\": true}"));
		assertThat(mvc.delete().uri("/api/programmer")).hasStatus(204);
		patch("/api/programmer/fixtures/" + b, "{\"dimmer\": 0.5}");
		noon = id(post(scenes, "{\"name\": \"Noon\", \"record\": true}"));
		assertThat(mvc.delete().uri("/api/programmer")).hasStatus(204);
		night = id(post(scenes, "{\"name\": \"Night\"}"));
	}

	@AfterEach
	void backToSetup() {
		put("/api/mode", "{\"mode\": \"setup\"}");
	}

	@Test
	void playsTheShowSceneBySceneInShowMode() throws Exception {
		assertThat(put("/api/mode", "{\"mode\": \"show\"}")).hasStatusOk().bodyJson()
				.extractingPath("$.mode").isEqualTo("show");
		assertThat(mvc.patch().uri("/api/programmer/fixtures/1").contentType(MediaType.APPLICATION_JSON)
				.content("{\"dimmer\": 1}").exchange()).as("programmer locked").hasStatus(409);

		assertThat(mvc.get().uri("/api/playback")).bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.position").isEqualTo(-1);
			assertThat(json).extractingPath("$.nextSceneId").isEqualTo((int) dawn);
		});
		assertThat(dmx()).startsWith(0, 0);

		go();
		assertThat(dmx()).startsWith(255, 0);
		go();
		assertThat(dmx()).startsWith(0, 128);
		go();
		assertThat(dmx()).as("Night is empty – dark").startsWith(0, 0);

		var end = go();
		assertThat(JsonPath.<Integer>read(end, "$.position")).isEqualTo(3);
		assertThat(JsonPath.<Boolean>read(end, "$.nextIsEnd")).isFalse();
	}

	@Test
	void backAndJump() throws Exception {
		go();
		Thread.sleep(250);
		assertThat(put("/api/playback/next", "{\"sceneId\": " + night + "}")).hasStatusOk().bodyJson()
				.extractingPath("$.nextSceneId").isEqualTo((int) night);
		go();
		// back goes to the previous scene in the list, not to where the jump came from
		assertThat(post("/api/playback/back", "")).hasStatusOk().bodyJson()
				.extractingPath("$.currentSceneId").isEqualTo((int) noon);
		assertThat(put("/api/playback/next", "{\"sceneId\": 999999}")).hasStatus(404);
	}

	@Test
	void allClientsFollowTheHighlightedSentenceOfTheScript() throws Exception {
		go();
		assertThat(put("/api/playback/script", "{\"sceneId\": " + dawn + ", \"line\": 2}")).hasStatusOk().bodyJson()
				.extractingPath("$.scriptLine").isEqualTo(2);
		assertThat(mvc.get().uri("/api/playback")).bodyJson().extractingPath("$.scriptLine").isEqualTo(2);

		go();
		assertThat(mvc.get().uri("/api/playback")).bodyJson().extractingPath("$.scriptLine").isEqualTo(0);
		assertThat(put("/api/playback/script", "{\"sceneId\": " + dawn + ", \"line\": 1}")).as("not on stage")
				.hasStatusOk().bodyJson().extractingPath("$.scriptLine").isEqualTo(0);
		assertThat(put("/api/playback/script", "{\"sceneId\": " + noon + "}")).hasStatus(400);
	}

	@Test
	void pauseIsReported() {
		assertThat(post("/api/playback/pause", "")).hasStatusOk().bodyJson().extractingPath("$.paused").isEqualTo(true);
		assertThat(post("/api/playback/pause", "")).hasStatusOk().bodyJson().extractingPath("$.paused").isEqualTo(false);
		assertThat(put("/api/mode", "{\"mode\": \"disco\"}")).hasStatus(400);
	}

	/** GO, then wait past the double-GO guard. */
	private String go() throws Exception {
		var result = post("/api/playback/go", "");
		assertThat(result).hasStatusOk();
		Thread.sleep(250);
		return result.getResponse().getContentAsString();
	}

	private int[] dmx() throws Exception {
		var body = mvc.get().uri("/api/output/universes/1/dmx").exchange().getResponse().getContentAsString();
		var values = JsonPath.<java.util.List<Integer>>read(body, "$.values");
		return values.stream().mapToInt(Integer::intValue).toArray();
	}

	private static String fixture(String name, long universe, int address) {
		return """
				{"name": "%s", "fixtureTypeId": "generic-dimmer", "mode": "1ch", "universeId": %d, "address": %d}
				""".formatted(name, universe, address);
	}

	private MvcTestResult post(String uri, String json) {
		return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

	private MvcTestResult put(String uri, String json) {
		return mvc.put().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

	private void patch(String uri, String json) {
		assertThat(mvc.patch().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange()).hasStatusOk();
	}

	private static long id(MvcTestResult result) throws Exception {
		assertThat(result.getResponse().getStatus()).isEqualTo(201);
		return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
	}
}
