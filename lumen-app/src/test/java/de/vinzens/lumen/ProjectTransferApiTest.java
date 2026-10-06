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

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectTransferApiTest {

	@Autowired
	MockMvcTester mvc;

	@AfterEach
	void backToSetup() {
		put("/api/mode", "{\"mode\": \"setup\"}");
	}

	@Test
	void exportedProjectCanBeImportedAsACopy() throws Exception {
		var project = id(post("/api/projects", "{\"name\": \"Tempest\", \"venue\": \"Globe\", \"fadeInMs\": 1500, "
				+ "\"fadeCurve\": \"s-curve\"}"));
		var universe = id(post("/api/projects/" + project + "/universes",
				"{\"number\": 2, \"mode\": \"unicast\", \"ipAddresses\": [\"10.0.0.5\"], \"networkInterface\": \"en7\"}"));
		var front = id(post("/api/projects/" + project + "/fixtures", fixture("Front", universe, 1)));
		var back = id(post("/api/projects/" + project + "/fixtures", fixture("Back", universe, 2)));
		post("/api/projects/" + project + "/groups", "{\"name\": \"All\", \"fixtureIds\": [%d, %d]}".formatted(back, front));
		var scenes = "/api/projects/" + project + "/scenes";
		post(scenes, "{\"number\": \"2.5\", \"name\": \"Storm\", \"cueNote\": \"Thunder\", \"autoFollowMs\": 4000, "
				+ "\"fadeInMs\": 0, \"delayMs\": 1000, \"fadeCurve\": \"square\", \"script\": \"Hark! **Thunder**\"}");
		var dawn = id(post(scenes, "{\"number\": \"1\", \"name\": \"Dawn\"}"));
		// scene values come from the programmer
		assertThat(post("/api/projects/" + project + "/activate", "")).hasStatusOk();
		patchJson("/api/programmer/fixtures/" + back, "{\"dimmer\": 0.5}");
		assertThat(post(scenes + "/" + dawn + "/record", "")).hasStatusOk();
		assertThat(post("/api/projects/" + project + "/party-buttons", "{\"name\": \"Half\", \"mode\": \"flash\"}"))
				.hasStatus(201);

		var export = mvc.get().uri("/api/projects/" + project + "/export").exchange();
		assertThat(export).hasStatusOk().headers().hasValue("Content-Disposition",
				"attachment; filename=\"Tempest.lumen.json\"; filename*=UTF-8''Tempest.lumen.json");
		var file = export.getResponse().getContentAsString();
		assertThat(JsonPath.<String>read(file, "$.format")).isEqualTo("lumen-project");

		var imported = post("/api/projects/import", file);
		var copy = id(imported);
		assertThat(imported).bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.name").isEqualTo("Tempest (imported)");
			assertThat(json).extractingPath("$.venue").isEqualTo("Globe");
			assertThat(json).extractingPath("$.fadeInMs").isEqualTo(1500);
			assertThat(json).extractingPath("$.fadeCurve").isEqualTo("s-curve");
			assertThat(json).extractingPath("$.active").isEqualTo(false);
		});

		var fixtures = body("/api/projects/" + copy + "/fixtures");
		List<Integer> fixtureIds = JsonPath.read(fixtures, "$[*].id");
		assertThat(fixtureIds).doesNotContain((int) front, (int) back).hasSize(2);
		var newBack = JsonPath.<List<Integer>>read(fixtures, "$[?(@.name == 'Back')].id").getFirst();
		var newFront = JsonPath.<List<Integer>>read(fixtures, "$[?(@.name == 'Front')].id").getFirst();
		assertThat(JsonPath.<List<Integer>>read(body("/api/projects/" + copy + "/groups"), "$[0].fixtureIds"))
				.containsExactly(newBack, newFront);
		var universes = body("/api/projects/" + copy + "/universes");
		assertThat(JsonPath.<String>read(universes, "$[0].mode")).isEqualTo("unicast");
		assertThat(JsonPath.<List<String>>read(universes, "$[0].ipAddresses")).containsExactly("10.0.0.5");
		assertThat(JsonPath.<String>read(universes, "$[0].networkInterface")).isEqualTo("en7");

		var copiedScenes = body("/api/projects/" + copy + "/scenes");
		assertThat(JsonPath.<List<String>>read(copiedScenes, "$[*].number")).containsExactly("1", "2.5");
		var copiedLooks = body("/api/projects/" + copy + "/looks");
		assertThat(JsonPath.<List<Double>>read(copiedLooks, "$[?(@.name == 'Dawn')].values['%d'].dimmer"
				.formatted(newBack))).as("the recorded look of the scene").containsExactly(0.5);
		var copiedCompositions = body("/api/projects/" + copy + "/compositions");
		var dawnComposition = JsonPath.<List<Integer>>read(copiedCompositions, "$[?(@.name == 'Dawn')].id").getFirst();
		assertThat(JsonPath.<Integer>read(copiedScenes, "$[0].compositions[0].compositionId"))
				.isEqualTo(dawnComposition);
		assertThat(JsonPath.<String>read(copiedScenes, "$[1].cueNote")).isEqualTo("Thunder");
		assertThat(JsonPath.<Integer>read(copiedScenes, "$[1].autoFollowMs")).isEqualTo(4000);
		assertThat(JsonPath.<Integer>read(copiedScenes, "$[1].fadeInMs")).isZero();
		assertThat(JsonPath.<Object>read(copiedScenes, "$[1].fadeOutMs")).isNull();
		assertThat(JsonPath.<Integer>read(copiedScenes, "$[1].delayMs")).isEqualTo(1000);
		assertThat(JsonPath.<String>read(copiedScenes, "$[1].fadeCurve")).isEqualTo("square");
		assertThat(JsonPath.<Object>read(copiedScenes, "$[0].fadeCurve")).isNull();
		assertThat(JsonPath.<String>read(copiedScenes, "$[1].script")).isEqualTo("Hark! **Thunder**");
		assertThat(JsonPath.<String>read(copiedScenes, "$[0].script")).isEmpty();

		var buttons = body("/api/projects/" + copy + "/party-buttons");
		assertThat(JsonPath.<String>read(buttons, "$[0].mode")).isEqualTo("flash");
		assertThat(JsonPath.<Double>read(buttons, "$[0].values['%d'].dimmer".formatted(newBack))).isEqualTo(0.5);
	}

	@Test
	void brokenFilesAreRejectedWithoutLeavingAProjectBehind() throws Exception {
		var before = JsonPath.<List<Object>>read(body("/api/projects"), "$").size();

		assertThat(post("/api/projects/import", "{\"format\": \"something\", \"version\": 1}")).hasStatus(400);
		assertThat(post("/api/projects/import", """
				{"format": "lumen-project", "version": 1, "project": {"name": "Broken"},
				 "universes": [{"id": 1, "number": 1}],
				 "fixtures": [{"id": 5, "name": "A", "fixtureTypeId": "generic-dimmer", "mode": "1ch",
				               "universeId": 99, "address": 1}]}
				""")).hasStatus(400).bodyJson().extractingPath("$.errors[0]")
				.isEqualTo("Fixture 'A' refers to unknown universe 99");

		assertThat(JsonPath.<List<Object>>read(body("/api/projects"), "$")).hasSize(before);
	}

	@Test
	void filesFromBeforeLooksAreConverted() throws Exception {
		var imported = post("/api/projects/import", """
				{"format": "lumen-project", "version": 1, "project": {"name": "Old show"},
				 "universes": [{"id": 1, "number": 1}],
				 "fixtures": [{"id": 5, "name": "A", "fixtureTypeId": "generic-dimmer", "mode": "1ch",
				               "universeId": 1, "address": 1},
				              {"id": 6, "name": "B", "fixtureTypeId": "generic-dimmer", "mode": "1ch",
				               "universeId": 1, "address": 2}],
				 "groups": [{"id": 3, "name": "Row", "fixtureIds": [5, 6]}],
				 "scenes": [{"number": "1", "name": "Dawn", "values": {"5": {"dimmer": 0.7}}, "extraKeys": [1]},
				            {"number": "2", "name": "Fair", "values": {}}],
				 "compositions": [
				   {"id": 10, "name": "Chase", "kind": "chase", "params": {"stepMs": 500}, "target": {"groupId": 3}},
				   {"id": 11, "sceneNumber": "2", "sourceId": 10, "name": "Chase", "kind": "chase",
				    "params": {"stepMs": 500}, "target": {"groupId": 3}, "level": 0.5}],
				 "extraKeys": [{"slot": 1, "mode": "flash", "composition":
				   {"id": 12, "name": "Flash", "kind": "steps", "params": {"playMode": "loop"},
				    "steps": [{"holdMs": 100, "fadeMs": 0, "values": {"6": {"dimmer": 1}}}]}}]}
				""");
		var project = id(imported);

		var scenes = body("/api/projects/" + project + "/scenes");
		var compositions = body("/api/projects/" + project + "/compositions");
		assertThat(JsonPath.<List<String>>read(compositions, "$[*].name"))
				.containsExactlyInAnyOrder("1 Dawn", "Chase", "Flash");
		var chase = JsonPath.<List<Integer>>read(compositions, "$[?(@.name == 'Chase')].id").getFirst();
		assertThat(JsonPath.<Integer>read(scenes, "$[1].compositions[0].compositionId")).isEqualTo(chase);
		assertThat(JsonPath.<Double>read(scenes, "$[1].compositions[0].level")).isEqualTo(0.5);
		assertThat(JsonPath.<List<Object>>read(compositions, "$[?(@.name == 'Chase')].steps[*]")).hasSize(2);
		assertThat(JsonPath.<String>read(scenes, "$[0].keys[0].mode")).isEqualTo("flash");
		assertThat(JsonPath.<List<Double>>read(body("/api/projects/" + project + "/looks"),
				"$[?(@.name == '1 Dawn')].values.*.dimmer")).containsExactly(0.7);
	}

	@Test
	void backupsAreCreatedOnRequestAndWhenTheShowStarts() throws Exception {
		assertThat(mvc.post().uri("/api/backups").exchange()).hasStatus(201).bodyJson()
				.extractingPath("$.name").asString().endsWith("-manual.db");

		var start = Instant.now();
		assertThat(put("/api/mode", "{\"mode\": \"show\"}")).hasStatusOk();
		while (!body("/api/backups").contains("-show.db")) {
			assertThat(Duration.between(start, Instant.now())).as("show backup").isLessThan(Duration.ofSeconds(5));
			Thread.sleep(50);
		}
		assertThat(JsonPath.<String>read(body("/api/backups"), "$.directory")).contains("test-data");
	}

	private static String fixture(String name, long universe, int address) {
		return """
				{"name": "%s", "fixtureTypeId": "generic-dimmer", "mode": "1ch", "universeId": %d, "address": %d}
				""".formatted(name, universe, address);
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

	private void patchJson(String uri, String json) {
		assertThat(mvc.patch().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange()).hasStatusOk();
	}

	private static long id(MvcTestResult result) throws Exception {
		assertThat(result.getResponse().getStatus()).isEqualTo(201);
		return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
	}
}
