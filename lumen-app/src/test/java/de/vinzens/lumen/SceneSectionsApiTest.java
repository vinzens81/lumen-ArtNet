package de.vinzens.lumen;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Light markers in the script split a scene into parts with light of their own; looks can be used directly. */
@SpringBootTest
@AutoConfigureMockMvc
class SceneSectionsApiTest {

	@Autowired
	MockMvcTester mvc;

	@AfterEach
	void backToSetup() {
		put("/api/mode", "{\"mode\": \"setup\"}");
	}

	@Test
	void passingAMarkerInTheScriptFadesToTheLightOfThatPart() throws Exception {
		var project = id(post("/api/projects", "{\"name\": \"Markers\", \"fadeInMs\": 0, \"fadeOutMs\": 0}"));
		assertThat(post("/api/projects/" + project + "/activate", "")).hasStatusOk();
		var base = "/api/projects/" + project;
		var universe = id(post(base + "/universes", "{\"number\": 1}"));
		var front = fixture(base, universe, 1);
		var back = fixture(base, universe, 2);
		var warm = look(base, "Front", front);
		var spot = look(base, "Back", back);
		var scene = id(post(base + "/scenes", """
				{"name": "Party", "script": "Michael: Hello.\\n//## Window (the back lights up)##\\nDoro: Hi."}"""));

		assertThat(put(base + "/scenes/" + scene + "/sections", "{\"looks\": [{\"lookId\": %d}]}".formatted(warm)))
				.hasStatusOk();
		assertThat(put(base + "/scenes/" + scene + "/sections", """
				{"marker": "Window", "looks": [{"lookId": %d}, {"lookId": %d, "level": 0.5}]}""".formatted(warm, spot)))
				.hasStatusOk().bodyJson().satisfies(json -> {
					assertThat(json).extractingPath("$.looks[0].lookId").isEqualTo((int) warm);
					assertThat(json).extractingPath("$.sections[0].marker").isEqualTo("Window");
					assertThat(json).extractingPath("$.sections[0].looks.length()").isEqualTo(2);
				});
		assertThat(mvc.delete().uri(base + "/looks/" + spot)).as("used in the scene").hasStatus(409);

		assertThat(put("/api/mode", "{\"mode\": \"show\"}")).hasStatusOk();
		assertThat(post("/api/playback/go", "")).hasStatusOk();
		Thread.sleep(150);
		assertThat(dmx(2)).as("the start: front only").containsExactly(255, 0);

		assertThat(put("/api/playback/script", "{\"sceneId\": %d, \"line\": 1, \"section\": 1}".formatted(scene)))
				.hasStatusOk().bodyJson().extractingPath("$.section").isEqualTo(1);
		Thread.sleep(150);
		assertThat(dmx(2)).as("after the marker: front stays, back at half").containsExactly(255, 128);

		var copy = "/api/projects/" + id(post("/api/projects/import", body(base + "/export")));
		var copied = body(copy + "/scenes");
		assertThat(JsonPath.<String>read(copied, "$[0].sections[0].marker")).isEqualTo("Window");
		assertThat(JsonPath.<Double>read(copied, "$[0].sections[0].looks[1].level")).isEqualTo(0.5);
	}

	private long fixture(String base, long universe, int address) throws Exception {
		return id(post(base + "/fixtures", """
				{"name": "Dimmer %d", "fixtureTypeId": "generic-dimmer", "mode": "1ch", "universeId": %d, "address": %d}
				""".formatted(address, universe, address)));
	}

	private long look(String base, String name, long fixture) throws Exception {
		assertThat(mvc.delete().uri("/api/programmer")).hasStatus(204);
		assertThat(mvc.patch().uri("/api/programmer/fixtures/" + fixture).contentType(MediaType.APPLICATION_JSON)
				.content("{\"dimmer\": 1}")).hasStatusOk();
		var look = id(post(base + "/looks", "{\"name\": \"%s\", \"record\": true}".formatted(name)));
		assertThat(mvc.delete().uri("/api/programmer")).hasStatus(204);
		return look;
	}

	private int[] dmx(int channels) throws Exception {
		List<Integer> values = JsonPath.read(body("/api/output/universes/1/dmx"), "$.values");
		return values.stream().limit(channels).mapToInt(Integer::intValue).toArray();
	}

	private String body(String uri) throws Exception {
		var result = mvc.get().uri(uri).exchange();
		assertThat(result).hasStatusOk();
		return result.getResponse().getContentAsString();
	}

	private MvcTestResult post(String uri, String json) {
		return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

	private MvcTestResult put(String uri, String json) {
		return mvc.put().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

	private static long id(MvcTestResult result) throws Exception {
		assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
		return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
	}
}
