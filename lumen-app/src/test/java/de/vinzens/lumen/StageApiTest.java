package de.vinzens.lumen;

import com.jayway.jsonpath.JsonPath;
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
class StageApiTest {

	private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 7, 7};

	@Autowired
	MockMvcTester mvc;

	@Test
	void fixturesArePlacedOnTheStageAndTheFloorPlanTravelsWithTheExport() throws Exception {
		var project = id(post("/api/projects", "{\"name\": \"Stage\"}"));
		var universe = id(post("/api/projects/" + project + "/universes", "{\"number\": 1}"));
		var a = id(post("/api/projects/" + project + "/fixtures", fixture("A", universe, 1)));
		var b = id(post("/api/projects/" + project + "/fixtures", fixture("B", universe, 2)));

		assertThat(put("/api/projects/" + project + "/fixtures/positions",
				"{\"positions\": {\"%d\": {\"x\": 0.25, \"y\": 0.5}, \"%d\": null}}".formatted(a, b))).hasStatus(204);
		assertThat(mvc.get().uri("/api/projects/" + project + "/fixtures")).bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$[0].position.x").isEqualTo(0.25);
			assertThat(json).extractingPath("$[1].position").isNull();
		});
		assertThat(put("/api/projects/" + project + "/fixtures/positions",
				"{\"positions\": {\"%d\": {\"x\": 1.5, \"y\": 0}}}".formatted(a))).hasStatus(400);

		var plan = "/api/projects/" + project + "/stage/plan";
		assertThat(mvc.get().uri(plan)).hasStatus(404);
		assertThat(mvc.put().uri(plan).contentType(MediaType.IMAGE_PNG).content(PNG)).hasStatus(204);
		assertThat(mvc.get().uri(plan)).hasStatusOk().hasContentType(MediaType.IMAGE_PNG)
				.hasHeader("X-Content-Type-Options", "nosniff");
		assertThat(mvc.put().uri(plan).contentType(MediaType.IMAGE_PNG).content("<svg/>".getBytes())).hasStatus(400);
		assertThat(mvc.put().uri(plan).contentType("image/svg+xml").content("<svg/>".getBytes())).hasStatus(415);

		var file = mvc.get().uri("/api/projects/" + project + "/export").exchange().getResponse().getContentAsString();
		var copy = id(post("/api/projects/import", file));
		assertThat(mvc.get().uri("/api/projects/" + copy + "/stage/plan")).hasStatusOk().body().isEqualTo(PNG);

		assertThat(mvc.delete().uri(plan)).hasStatus(204);
		assertThat(mvc.get().uri(plan)).hasStatus(404);
	}

	@Test
	void fixturesKeepTheirStageShapeAcrossMovesAndTheExport() throws Exception {
		var project = id(post("/api/projects", "{\"name\": \"Shapes\"}"));
		var universe = id(post("/api/projects/" + project + "/universes", "{\"number\": 1}"));
		var bar = id(post("/api/projects/" + project + "/fixtures", fixture("Bar", universe, 1)));
		var fixtures = "/api/projects/" + project + "/fixtures";

		assertThat(mvc.get().uri(fixtures)).bodyJson().extractingPath("$[0].stageShape.kind").isEqualTo("auto");
		assertThat(put(fixtures + "/" + bar + "/stage-shape",
				"{\"kind\": \"strip\", \"rotation\": -90, \"length\": 0.4}")).hasStatus(204);
		assertThat(put(fixtures + "/positions", "{\"positions\": {\"%d\": {\"x\": 0.5, \"y\": 0.5}}}".formatted(bar)))
				.hasStatus(204);
		assertThat(mvc.get().uri(fixtures)).bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$[0].stageShape.kind").isEqualTo("strip");
			assertThat(json).extractingPath("$[0].stageShape.rotation").isEqualTo(270);
			assertThat(json).extractingPath("$[0].stageShape.length").isEqualTo(0.4);
		});
		assertThat(put(fixtures + "/" + bar + "/stage-shape", "{\"kind\": \"star\"}")).hasStatus(400);
		assertThat(put(fixtures + "/" + bar + "/stage-shape", "{\"kind\": \"bar\", \"length\": 2}")).hasStatus(400);

		var file = mvc.get().uri("/api/projects/" + project + "/export").exchange().getResponse().getContentAsString();
		var copy = id(post("/api/projects/import", file));
		assertThat(mvc.get().uri("/api/projects/" + copy + "/fixtures")).bodyJson()
				.extractingPath("$[0].stageShape.kind").isEqualTo("strip");
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

	private static long id(MvcTestResult result) throws Exception {
		assertThat(result.getResponse().getStatus()).isEqualTo(201);
		return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
	}
}
