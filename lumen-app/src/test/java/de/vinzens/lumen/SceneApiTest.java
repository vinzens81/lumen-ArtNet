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
class SceneApiTest {

	@Autowired
	MockMvcTester mvc;

	private long project;
	private long par;
	private String scenes;

	@BeforeEach
	void createActiveProject() throws Exception {
		project = id(post("/api/projects", "{\"name\": \"Scenes\"}"));
		assertThat(post("/api/projects/" + project + "/activate", "")).hasStatusOk();
		var universe = id(post("/api/projects/" + project + "/universes", "{\"number\": 1}"));
		par = id(post("/api/projects/" + project + "/fixtures", """
				{"name": "PAR", "fixtureTypeId": "generic-rgbw-par", "mode": "8ch", "universeId": %d, "address": 1}
				""".formatted(universe)));
		scenes = "/api/projects/" + project + "/scenes";
		assertThat(mvc.delete().uri("/api/programmer")).hasStatus(204);
	}

	@Test
	void recordScenesFromTheProgrammerAndLoadThemBack() throws Exception {
		patch("/api/programmer/fixtures/" + par, "{\"dimmer\": 1, \"red\": 1}");
		var dawn = id(post(scenes, "{\"name\": \"Dawn\", \"record\": true, \"cueNote\": \"GO on sunrise\"}"));
		patch("/api/programmer/fixtures/" + par, "{\"red\": 0, \"blue\": 1}");
		post(scenes, "{\"name\": \"Night\", \"record\": true, \"autoFollowMs\": 8000}");
		post(scenes, "{\"number\": \"1.5\", \"name\": \"Dark\"}");

		var list = assertThat(mvc.get().uri(scenes)).hasStatusOk().bodyJson();
		list.extractingPath("$[*].number").asArray().containsExactly("1", "1.5", "2");
		list.extractingPath("$[0].values['%d'].red".formatted(par)).isEqualTo(1.0);
		list.extractingPath("$[1].fixtureCount").isEqualTo(0);
		list.extractingPath("$[2].autoFollowMs").isEqualTo(8000);

		assertThat(post(scenes + "/" + dawn + "/load", "")).hasStatus(204);
		assertThat(mvc.get().uri("/api/programmer")).bodyJson()
				.extractingPath("$['%d']".formatted(par)).asMap().containsEntry("red", 1.0).containsEntry("dimmer", 1.0);
	}

	@Test
	void recordOverwritesAnExistingScene() throws Exception {
		patch("/api/programmer/fixtures/" + par, "{\"dimmer\": 1}");
		var scene = id(post(scenes, "{\"name\": \"Look\", \"record\": true}"));

		patch("/api/programmer/fixtures/" + par, "{\"dimmer\": 0.25}");
		assertThat(post(scenes + "/" + scene + "/record", "")).hasStatusOk().bodyJson()
				.extractingPath("$.values['%d'].dimmer".formatted(par)).isEqualTo(0.25);
	}

	@Test
	void numbersMustBeUniqueAndValid() throws Exception {
		var first = id(post(scenes, "{\"number\": \"2.50\", \"name\": \"A\"}"));

		assertThat(post(scenes, "{\"number\": \"2.5\", \"name\": \"B\"}")).hasStatus(409);
		assertThat(post(scenes, "{\"number\": \"abc\", \"name\": \"B\"}")).hasStatus(400);
		assertThat(put(scenes + "/" + first, "{\"number\": \"7\", \"name\": \"Renamed\"}")).hasStatusOk()
				.bodyJson().extractingPath("$.number").isEqualTo("7");
		assertThat(post(scenes, "{\"name\": \"Next\"}")).hasStatus(201).bodyJson()
				.extractingPath("$.number").isEqualTo("8");
	}

	@Test
	void fadeTimesPerSceneDefaultToTheProjectTimes() throws Exception {
		var flash = id(post(scenes, "{\"name\": \"Flash\", \"fadeInMs\": 0, \"fadeOutMs\": 0, \"delayMs\": 500}"));
		var plain = id(post(scenes, "{\"name\": \"Plain\"}"));

		var list = assertThat(mvc.get().uri(scenes)).hasStatusOk().bodyJson();
		list.extractingPath("$[0].fadeInMs").isEqualTo(0);
		list.extractingPath("$[0].delayMs").isEqualTo(500);
		list.extractingPath("$[1].fadeInMs").isNull();
		list.extractingPath("$[1].delayMs").isEqualTo(0);

		assertThat(put(scenes + "/" + flash, "{\"number\": \"1\", \"name\": \"Flash\"}")).hasStatusOk().bodyJson()
				.extractingPath("$.fadeOutMs").isNull();
		assertThat(put(scenes + "/" + plain, "{\"number\": \"2\", \"name\": \"Plain\", \"delayMs\": -1}"))
				.hasStatus(400);
	}

	@Test
	void scriptIsStoredWithTheScene() throws Exception {
		var scene = id(post(scenes, "{\"name\": \"Prologue\", \"script\": \"Two households.\\r\\n**Enter Chorus**\\n\"}"));

		assertThat(mvc.get().uri(scenes)).hasStatusOk().bodyJson()
				.extractingPath("$[0].script").isEqualTo("Two households.\n**Enter Chorus**");
		assertThat(put(scenes + "/" + scene, "{\"number\": \"1\", \"name\": \"Prologue\"}")).hasStatusOk()
				.bodyJson().extractingPath("$.script").isEqualTo("");
		assertThat(post(scenes, "{\"name\": \"Long\", \"script\": \"%s\"}".formatted("a".repeat(100_001))))
				.hasStatus(400);
	}

	@Test
	void recordingNeedsTheActiveProject() throws Exception {
		var other = id(post("/api/projects", "{\"name\": \"Not active\"}"));

		assertThat(post("/api/projects/" + other + "/scenes", "{\"name\": \"X\", \"record\": true}")).hasStatus(409);
		assertThat(post("/api/projects/" + other + "/scenes", "{\"name\": \"X\"}")).hasStatus(201);
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
