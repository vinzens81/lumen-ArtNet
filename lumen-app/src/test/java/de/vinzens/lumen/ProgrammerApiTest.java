package de.vinzens.lumen;

import com.jayway.jsonpath.JsonPath;
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
class ProgrammerApiTest {

	@Autowired
	MockMvcTester mvc;

	private long project;
	private long par;
	private long dimmer;

	@BeforeEach
	void createActiveProject() throws Exception {
		project = id(post("/api/projects", "{\"name\": \"Programmer\"}"));
		assertThat(post("/api/projects/" + project + "/activate", "")).hasStatusOk();
		var universe = id(post("/api/projects/" + project + "/universes", "{\"number\": 1}"));
		par = id(post("/api/projects/" + project + "/fixtures", """
				{"name": "PAR", "fixtureTypeId": "generic-rgbw-par", "mode": "8ch", "universeId": %d, "address": 10}
				""".formatted(universe)));
		dimmer = id(post("/api/projects/" + project + "/fixtures", """
				{"name": "Dimmer", "fixtureTypeId": "generic-dimmer", "mode": "1ch", "universeId": %d, "address": 1}
				""".formatted(universe)));
	}

	@Test
	void programmerValuesAppearInTheDmxOutput() {
		assertThat(patch("/api/programmer/fixtures/" + par, "{\"dimmer\": 1, \"red\": 1, \"blue\": 0.5}"))
				.hasStatusOk().bodyJson().extractingPath("$.blue").isEqualTo(0.5);
		assertThat(patch("/api/programmer/fixtures/" + dimmer, "{\"dimmer\": 0.5}")).hasStatusOk();

		var dmx = assertThat(mvc.get().uri("/api/output/universes/1/dmx")).hasStatusOk().bodyJson();
		dmx.extractingPath("$.values[0]").as("dimmer at channel 1").isEqualTo(128);
		dmx.extractingPath("$.values[9]").as("PAR dimmer at channel 10").isEqualTo(255);
		dmx.extractingPath("$.values[10]").as("PAR red").isEqualTo(255);
		dmx.extractingPath("$.values[12]").as("PAR blue").isEqualTo(128);
	}

	@Test
	void updatesKeepOtherAttributesAndCanBeCleared() {
		patch("/api/programmer/fixtures/" + par, "{\"dimmer\": 1, \"red\": 1}");

		assertThat(patch("/api/programmer/fixtures/" + par, "{\"red\": 0}")).hasStatusOk().bodyJson()
				.extractingPath("$.dimmer").isEqualTo(1.0);

		assertThat(mvc.delete().uri("/api/programmer/fixtures/" + par)).hasStatus(204);
		assertThat(mvc.get().uri("/api/output/universes/1/dmx")).bodyJson()
				.extractingPath("$.values[9]").isEqualTo(0);
	}

	@Test
	void rejectsInvalidValuesAndAttributes() {
		assertThat(patch("/api/programmer/fixtures/" + par, "{\"dimmer\": 2}")).hasStatus(400).bodyJson()
				.extractingPath("$.errors[0]").isEqualTo("dimmer must be between 0 and 1, was 2.0");
		assertThat(patch("/api/programmer/fixtures/" + par, "{\"pan\": 0.5}")).hasStatus(400);
		assertThat(patch("/api/programmer/fixtures/" + par, "{\"brightness\": 0.5}")).hasStatus(400);
	}

	@Test
	void onlyFixturesOfTheActiveProjectCanBeControlled() throws Exception {
		var other = id(post("/api/projects", "{\"name\": \"Other\"}"));
		assertThat(post("/api/projects/" + other + "/activate", "")).hasStatusOk();

		assertThat(patch("/api/programmer/fixtures/" + par, "{\"dimmer\": 1}")).hasStatus(404);
		assertThat(mvc.get().uri("/api/output/universes/1/dmx")).hasStatus(404);
	}

	@Test
	void selectionAndGroupsReceiveOnlySupportedAttributes() throws Exception {
		assertThat(patch("/api/programmer/fixtures", """
				{"fixtureIds": [%d, %d], "values": {"dimmer": 1, "red": 1}}""".formatted(par, dimmer)))
				.hasStatusOk().bodyJson()
				.satisfies(json -> {
					assertThat(json).extractingPath("$['%d'].red".formatted(par)).isEqualTo(1.0);
					assertThat(json).extractingPath("$['%d']".formatted(dimmer)).asMap().containsOnlyKeys("dimmer");
				});

		var group = id(post("/api/projects/" + project + "/groups", """
				{"name": "All", "fixtureIds": [%d, %d]}""".formatted(par, dimmer)));
		assertThat(patch("/api/programmer/groups/" + group, "{\"dimmer\": 0}")).hasStatusOk();
		assertThat(mvc.get().uri("/api/output/universes/1/dmx")).bodyJson()
				.extractingPath("$.values[0]").isEqualTo(0);

		assertThat(post("/api/programmer/clear", "{\"fixtureIds\": [%d]}".formatted(par))).hasStatus(204);
		assertThat(mvc.get().uri("/api/programmer")).bodyJson().extractingPath("$").asMap()
				.containsOnlyKeys(String.valueOf(dimmer));
	}

	@Test
	void grandMasterAndBlackout() {
		patch("/api/programmer/fixtures/" + dimmer, "{\"dimmer\": 1}");

		assertThat(put("/api/output/master", "{\"level\": 0.5}")).hasStatusOk();
		assertThat(mvc.get().uri("/api/output/universes/1/dmx")).bodyJson().extractingPath("$.values[0]").isEqualTo(128);

		assertThat(put("/api/output/blackout", "{\"active\": true}")).hasStatusOk().bodyJson()
				.extractingPath("$.level").isEqualTo(0.5);
		assertThat(mvc.get().uri("/api/output/universes/1/dmx")).bodyJson().extractingPath("$.values[0]").isEqualTo(0);

		assertThat(put("/api/output/master", "{\"level\": 2}")).hasStatus(400);
		put("/api/output/blackout", "{\"active\": false}");
		put("/api/output/master", "{\"level\": 1}");
	}

	@Test
	void artNetNodesAreEmptyWhenArtNetIsDisabled() {
		assertThat(mvc.get().uri("/api/artnet/nodes")).hasStatusOk().bodyJson().extractingPath("$").asArray().isEmpty();
	}

	@Test
	void patchChangesAreAppliedToTheOutput() {
		patch("/api/programmer/fixtures/" + dimmer, "{\"dimmer\": 1}");

		assertThat(put("/api/projects/" + project + "/fixtures/" + dimmer, """
				{"name": "Dimmer", "fixtureTypeId": "generic-dimmer", "mode": "1ch", "universeId": %d, "address": 100}
				""".formatted(universeId()))).hasStatusOk();

		var dmx = assertThat(mvc.get().uri("/api/output/universes/1/dmx")).bodyJson();
		dmx.extractingPath("$.values[0]").isEqualTo(0);
		dmx.extractingPath("$.values[99]").isEqualTo(255);
	}

	private long universeId() {
		var json = mvc.get().uri("/api/projects/" + project + "/universes").exchange();
		try {
			return ((Number) JsonPath.read(json.getResponse().getContentAsString(), "$[0].id")).longValue();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private MvcTestResult post(String uri, String json) {
		return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

	private MvcTestResult put(String uri, String json) {
		return mvc.put().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

	private MvcTestResult patch(String uri, String json) {
		return mvc.patch().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

	private static long id(MvcTestResult result) throws Exception {
		assertThat(result.getResponse().getStatus()).isEqualTo(201);
		return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
	}
}
