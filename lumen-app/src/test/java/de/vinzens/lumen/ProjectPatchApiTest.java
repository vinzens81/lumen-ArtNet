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
class ProjectPatchApiTest {

	@Autowired
	MockMvcTester mvc;

	@Test
	void activatingAProjectDeactivatesTheOthers() throws Exception {
		var first = createProject("Erstes");
		var second = createProject("Zweites");

		assertThat(post("/api/projects/" + second + "/activate", "")).hasStatusOk()
				.bodyJson().extractingPath("$.active").isEqualTo(true);
		assertThat(mvc.get().uri("/api/projects/active")).hasStatusOk()
				.bodyJson().extractingPath("$.id").isEqualTo((int) second);
		assertThat(mvc.get().uri("/api/projects/" + first)).hasStatusOk()
				.bodyJson().extractingPath("$.active").isEqualTo(false);
	}

	@Test
	void projectDefaultsAndValidation() {
		assertThat(post("/api/projects", "{\"name\": \"Default-Fades\"}")).hasStatus(201)
				.bodyJson().extractingPath("$.fadeInMs").isEqualTo(3000);

		assertThat(post("/api/projects", "{\"name\": \"\", \"fadeInMs\": -1}")).hasStatus(400)
				.bodyJson().extractingPath("$.errors").asArray()
				.containsExactly("name fehlt", "fadeIn muss zwischen 0 und 10 Minuten liegen");
	}

	@Test
	void patchWithOverlapIsSavedAndReported() throws Exception {
		var project = createProject("Patch");
		var universe = createUniverse(project, 1);

		var left = createFixture(project, universe, "PAR links", 1);
		assertThat(left).hasStatus(201).bodyJson()
				.extractingPath("$.lastAddress").isEqualTo(8);
		createFixture(project, universe, "PAR rechts", 5);

		var issues = assertThat(mvc.get().uri("/api/projects/" + project + "/patch/issues")).hasStatusOk().bodyJson();
		issues.extractingPath("$[0].message").isEqualTo(
				"'PAR links' und 'PAR rechts' überschneiden sich in Universe 1 – Bühne (Kanäle 5–8)");
		issues.extractingPath("$[0].fixtureIds").asArray().hasSize(2);
	}

	@Test
	void universeDefaultsAndUniqueNumber() throws Exception {
		var project = createProject("Universen");

		assertThat(post("/api/projects/" + project + "/universes", "{\"number\": 3}")).hasStatus(201).bodyJson()
				.satisfies(json -> {
					assertThat(json).extractingPath("$.portAddress").isEqualTo(2);
					assertThat(json).extractingPath("$.target").isEqualTo("broadcast");
				});
		assertThat(post("/api/projects/" + project + "/universes", "{\"number\": 3}")).hasStatus(409);
		assertThat(post("/api/projects/" + project + "/universes",
				"{\"number\": 4, \"target\": \"192.168.1.300\"}")).hasStatus(400);
	}

	@Test
	void universeInUseCannotBeDeleted() throws Exception {
		var project = createProject("Löschen");
		var universe = createUniverse(project, 1);
		var fixture = id(createFixture(project, universe, "PAR", 1));

		assertThat(mvc.delete().uri("/api/projects/" + project + "/universes/" + universe)).hasStatus(409);

		assertThat(mvc.delete().uri("/api/projects/" + project + "/fixtures/" + fixture)).hasStatus(204);
		assertThat(mvc.delete().uri("/api/projects/" + project + "/universes/" + universe)).hasStatus(204);
	}

	@Test
	void groupsOnlyAcceptFixturesOfTheProject() throws Exception {
		var project = createProject("Gruppen");
		var universe = createUniverse(project, 1);
		var a = id(createFixture(project, universe, "A", 1));
		var b = id(createFixture(project, universe, "B", 9));

		assertThat(post("/api/projects/" + project + "/groups",
				"{\"name\": \"Front\", \"color\": \"#ff9a3c\", \"fixtureIds\": [" + b + ", " + a + "]}"))
				.hasStatus(201).bodyJson().extractingPath("$.fixtureIds").asArray()
				.containsExactly((int) b, (int) a);

		var other = createProject("Anderes");
		assertThat(post("/api/projects/" + other + "/groups",
				"{\"name\": \"Fremd\", \"fixtureIds\": [" + a + "]}")).hasStatus(400);
	}

	@Test
	void fixturesOfOtherProjectsAreNotFound() throws Exception {
		var project = createProject("Eigenes");
		var universe = createUniverse(project, 1);
		var fixture = id(createFixture(project, universe, "PAR", 1));
		var other = createProject("Fremdes");

		assertThat(mvc.delete().uri("/api/projects/" + other + "/fixtures/" + fixture)).hasStatus(404);
		assertThat(mvc.get().uri("/api/projects/999999/fixtures")).hasStatus(404);
	}

	private long createProject(String name) throws Exception {
		return id(post("/api/projects", "{\"name\": \"" + name + "\", \"fadeInMs\": 2000, \"fadeOutMs\": 4000}"));
	}

	private long createUniverse(long project, int number) throws Exception {
		return id(post("/api/projects/" + project + "/universes",
				"{\"number\": " + number + ", \"name\": \"Bühne\", \"artnet\": {\"net\": 0, \"subnet\": 0, "
						+ "\"universe\": " + (number - 1) + "}, \"target\": \"broadcast\"}"));
	}

	private MvcTestResult createFixture(long project, long universe, String name, int address) {
		return post("/api/projects/" + project + "/fixtures", """
				{"name": "%s", "fixtureTypeId": "generic-rgbw-par", "mode": "8ch", "universeId": %d,
				 "address": %d, "position": {"x": 0.2, "y": 0.8}}""".formatted(name, universe, address));
	}

	private MvcTestResult post(String uri, String json) {
		return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

	private static long id(MvcTestResult result) throws Exception {
		assertThat(result.getResponse().getStatus()).isEqualTo(201);
		return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
	}
}
