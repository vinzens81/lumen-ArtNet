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
	void houseLightFaderPlaysItsCompositionAtItsLevel() throws Exception {
		var project = id(post("/api/projects", "{\"name\": \"Faders\"}"));
		assertThat(post("/api/projects/" + project + "/activate", "")).hasStatusOk();
		var universe = id(post("/api/projects/" + project + "/universes", "{\"number\": 1}"));
		var house = id(post("/api/projects/" + project + "/fixtures", """
				{"name": "House", "fixtureTypeId": "generic-dimmer", "mode": "1ch", "universeId": %d, "address": 1}
				""".formatted(universe)));
		assertThat(mvc.delete().uri("/api/programmer")).hasStatus(204);
		assertThat(mvc.patch().uri("/api/programmer/fixtures/" + house).contentType(MediaType.APPLICATION_JSON)
				.content("{\"dimmer\": 1}")).hasStatusOk();
		var composition = id(post("/api/projects/" + project + "/compositions", "{\"name\": \"House full\"}"));
		assertThat(post("/api/projects/" + project + "/compositions/" + composition + "/record-step", ""))
				.hasStatusOk();
		assertThat(mvc.delete().uri("/api/programmer")).hasStatus(204);

		assertThat(put("/api/projects/" + project + "/faders/1", "{\"name\": \"House\", \"compositionId\": %d}"
				.formatted(composition))).hasStatusOk();
		assertThat(put("/api/projects/" + project + "/faders/9", "{\"compositionId\": %d}".formatted(composition)))
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

	@Test
	void stopTakesTheShowAndTheFadersToZeroAtOnce() throws Exception {
		var project = id(post("/api/projects", "{\"name\": \"Stop\"}"));
		assertThat(post("/api/projects/" + project + "/activate", "")).hasStatusOk();
		var universe = id(post("/api/projects/" + project + "/universes", "{\"number\": 1}"));
		var dimmer = id(post("/api/projects/" + project + "/fixtures", """
				{"name": "Dimmer", "fixtureTypeId": "generic-dimmer", "mode": "1ch", "universeId": %d, "address": 1}
				""".formatted(universe)));
		assertThat(mvc.delete().uri("/api/programmer")).hasStatus(204);
		assertThat(mvc.patch().uri("/api/programmer/fixtures/" + dimmer).contentType(MediaType.APPLICATION_JSON)
				.content("{\"dimmer\": 1}")).hasStatusOk();
		post("/api/projects/" + project + "/scenes", "{\"name\": \"Full\", \"record\": true}");
		var composition = JsonPath.<List<Integer>>read(body("/api/projects/" + project + "/compositions"), "$[*].id")
				.getFirst();
		assertThat(mvc.delete().uri("/api/programmer")).hasStatus(204);
		put("/api/projects/" + project + "/faders/1", "{\"compositionId\": %d}".formatted(composition));
		put("/api/mode", "{\"mode\": \"show\"}");
		post("/api/playback/go", "");
		put("/api/playback/faders/1", "{\"level\": 1}");

		assertThat(post("/api/playback/stop", "")).hasStatusOk().bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.position").isEqualTo(-1);
			assertThat(json).extractingPath("$.faders[0].level").isEqualTo(0.0);
		});
		Thread.sleep(150);
		var dmx = JsonPath.<List<Integer>>read(body("/api/output/universes/1/dmx"), "$.values");
		assertThat(dmx.getFirst()).isZero();
		put("/api/mode", "{\"mode\": \"setup\"}");
	}

	@Test
	void aFaderOfAVersion2FilePlaysTheFirstCompositionOfItsSceneOutsideTheCueList() throws Exception {
		var file = """
				{"format": "lumen-project", "version": 2, "project": {"name": "Old faders"},
				 "compositions": [{"id": 1, "name": "Dawn"}, {"id": 2, "name": "House full"}],
				 "scenes": [{"number": "1", "name": "Dawn", "compositions": [{"compositionId": 1}]},
				            {"number": "50", "name": "House", "outsideCueList": true,
				             "compositions": [{"compositionId": 2}]}],
				 "faders": [{"slot": 1, "scene": "50"}],
				 "partyButtons": [{"name": "Strobe", "mode": "flash", "values": {}}]}""";

		var copy = id(post("/api/projects/import", file));

		assertThat(mvc.get().uri("/api/projects/" + copy + "/scenes")).bodyJson()
				.extractingPath("$[*].name").asArray().containsExactly("Dawn");
		var house = JsonPath.<List<Integer>>read(body("/api/projects/" + copy + "/compositions"),
				"$[?(@.name == 'House full')].id").getFirst();
		assertThat(mvc.get().uri("/api/projects/" + copy + "/faders")).bodyJson()
				.extractingPath("$[0].compositionId").isEqualTo(house);
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
