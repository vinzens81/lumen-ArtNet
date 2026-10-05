package de.vinzens.lumen;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class FaderApiTest {

	@Autowired
	MockMvcTester mvc;

	@Test
	void houseLightFaderPlaysItsSceneAtItsLevel() throws Exception {
		var project = id(post("/api/projects", "{\"name\": \"Faders\"}"));
		assertThat(post("/api/projects/" + project + "/activate", "")).hasStatusOk();
		var universe = id(post("/api/projects/" + project + "/universes", "{\"number\": 1}"));
		var house = id(post("/api/projects/" + project + "/fixtures", """
				{"name": "House", "fixtureTypeId": "generic-dimmer", "mode": "1ch", "universeId": %d, "address": 1}
				""".formatted(universe)));
		assertThat(mvc.delete().uri("/api/programmer")).hasStatus(204);
		assertThat(mvc.patch().uri("/api/programmer/fixtures/" + house).contentType(MediaType.APPLICATION_JSON)
				.content("{\"dimmer\": 1}")).hasStatusOk();
		var scene = id(post("/api/projects/" + project + "/scenes",
				"{\"name\": \"House full\", \"keyOnly\": true, \"record\": true}"));
		assertThat(mvc.delete().uri("/api/programmer")).hasStatus(204);

		assertThat(put("/api/projects/" + project + "/faders/1", "{\"name\": \"House\", \"sceneId\": %d}"
				.formatted(scene))).hasStatusOk();
		assertThat(put("/api/projects/" + project + "/faders/9", "{\"sceneId\": %d}".formatted(scene)))
				.hasStatus(400);
		assertThat(put("/api/playback/faders/1", "{\"level\": 0.5}")).hasStatusOk().bodyJson()
				.extractingPath("$.faders[0].level").isEqualTo(0.5);
		assertThat(put("/api/playback/faders/1", "{\"level\": 2}")).hasStatus(400);

		Thread.sleep(150); // a render tick
		var dmx = JsonPath.<List<Integer>>read(body("/api/output/universes/1/dmx"), "$.values");
		assertThat(dmx.getFirst()).isBetween(127, 128);

		var file = body("/api/projects/" + project + "/export");
		var copy = id(post("/api/projects/import", file));
		assertThat(mvc.get().uri("/api/projects/" + copy + "/faders")).bodyJson()
				.extractingPath("$[0].name").isEqualTo("House");

		assertThat(put("/api/playback/faders/1", "{\"level\": 0}")).hasStatusOk();
	}

	private MvcTestResult post(String uri, String json) {
		return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

	private MvcTestResult put(String uri, String json) {
		return mvc.put().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

	private String body(String uri) throws Exception {
		return mvc.get().uri(uri).exchange().getResponse().getContentAsString();
	}

	private static long id(MvcTestResult result) throws Exception {
		assertThat(result.getResponse().getStatus()).isEqualTo(201);
		return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
	}
}
