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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class PresetApiTest {

	@Autowired
	MockMvcTester mvc;

	private long project;
	private long left;
	private long right;
	private String presets;
	private String scenes;
	private String looks;

	@BeforeEach
	void createActiveProject() throws Exception {
		project = id(post("/api/projects", "{\"name\": \"Presets\"}"));
		assertThat(post("/api/projects/" + project + "/activate", "")).hasStatusOk();
		var universe = id(post("/api/projects/" + project + "/universes", "{\"number\": 1}"));
		left = id(post("/api/projects/" + project + "/fixtures", par("Left", universe, 1)));
		right = id(post("/api/projects/" + project + "/fixtures", par("Right", universe, 9)));
		presets = "/api/projects/" + project + "/presets";
		scenes = "/api/projects/" + project + "/scenes";
		looks = "/api/projects/" + project + "/looks";
		assertThat(mvc.delete().uri("/api/programmer")).hasStatus(204);
	}

	@Test
	void looksChangeWithTheirPreset() throws Exception {
		patch("/api/programmer/fixtures/" + left, "{\"dimmer\": 1, \"red\": 1, \"green\": 0.5}");
		patch("/api/programmer/fixtures/" + right, "{\"dimmer\": 1, \"red\": 1}");
		var warm = id(post(presets, """
				{"name": "Warm", "group": "color", "fixtureIds": [%d, %d]}""".formatted(left, right)));
		assertThat(mvc.get().uri(presets)).bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$[0].group").isEqualTo("color");
			assertThat(json).extractingPath("$[0].values['%d'].green".formatted(left)).isEqualTo(0.5);
			assertThat(json).doesNotHavePath("$[0].values['%d'].dimmer".formatted(left));
		});
		assertThat(mvc.get().uri("/api/programmer/presets")).bodyJson()
				.extractingPath("$['%d'].color".formatted(left)).isEqualTo((int) warm);

		// the recorded scene keeps its values in a look
		var scene = id(post(scenes, "{\"name\": \"Evening\", \"record\": true}"));
		assertThat(mvc.get().uri(looks)).bodyJson()
				.extractingPath("$[0].presets['%d'].color".formatted(right)).isEqualTo((int) warm);

		// change the preset for the left fixture: the look follows
		patch("/api/programmer/fixtures/" + left, "{\"red\": 0.2, \"green\": 0.1}");
		assertThat(mvc.get().uri("/api/programmer/presets")).bodyJson()
				.doesNotHavePath("$['%d']".formatted(left));
		assertThat(post(presets + "/" + warm + "/record", "{\"fixtureIds\": [%d]}".formatted(left))).hasStatusOk();
		var updated = assertThat(mvc.get().uri(looks)).bodyJson();
		updated.extractingPath("$[0].values['%d'].red".formatted(left)).isEqualTo(0.2);
		updated.extractingPath("$[0].values['%d'].dimmer".formatted(left)).isEqualTo(1.0);
		updated.extractingPath("$[0].values['%d'].red".formatted(right)).isEqualTo(1.0);

		// apply puts the preset back on a fixture
		patch("/api/programmer/fixtures/" + right, "{\"red\": 0}");
		assertThat(post(presets + "/" + warm + "/apply", "{\"fixtureIds\": [%d]}".formatted(right))).hasStatus(204);
		assertThat(mvc.get().uri("/api/programmer")).bodyJson()
				.extractingPath("$['%d'].red".formatted(right)).isEqualTo(1.0);

		// deleting keeps the values but drops the references
		assertThat(mvc.delete().uri(presets + "/" + warm)).hasStatus(204);
		var after = assertThat(mvc.get().uri(looks)).bodyJson();
		after.extractingPath("$[0].presets").asMap().isEmpty();
		after.extractingPath("$[0].values['%d'].red".formatted(left)).isEqualTo(0.2);
		assertThat(scene).isPositive();
	}

	@Test
	void presetsNeedValuesOfTheirGroup() throws Exception {
		patch("/api/programmer/fixtures/" + left, "{\"dimmer\": 1}");

		assertThat(post(presets, "{\"name\": \"Empty\", \"group\": \"color\", \"fixtureIds\": [%d]}".formatted(left)))
				.hasStatus(400);
		assertThat(post(presets, "{\"name\": \"Bright\", \"group\": \"intensity\", \"fixtureIds\": [%d]}"
				.formatted(left))).hasStatus(400);
		assertThat(post(presets + "/999999/apply", "{\"fixtureIds\": []}")).hasStatus(404);
	}

	@Test
	void presetsTravelWithTheProjectFile() throws Exception {
		patch("/api/programmer/fixtures/" + left, "{\"dimmer\": 1, \"blue\": 1}");
		var night = id(post(presets, "{\"name\": \"Night\", \"group\": \"color\", \"fixtureIds\": [%d]}"
				.formatted(left)));
		post(scenes, "{\"name\": \"Night\", \"record\": true}");

		var file = mvc.get().uri("/api/projects/" + project + "/export").exchange().getResponse()
				.getContentAsString();
		var copy = id(post("/api/projects/import", file));

		var copiedPresets = body("/api/projects/" + copy + "/presets");
		var copiedPreset = JsonPath.<Integer>read(copiedPresets, "$[0].id");
		assertThat(copiedPreset).isNotEqualTo((int) night);
		var newLeft = JsonPath.<List<Integer>>read(body("/api/projects/" + copy + "/fixtures"),
				"$[?(@.name == 'Left')].id").getFirst();
		assertThat(JsonPath.<Integer>read(body("/api/projects/" + copy + "/looks"),
				"$[0].presets['%d'].color".formatted(newLeft))).isEqualTo(copiedPreset);
	}

	private static String par(String name, long universe, int address) {
		return """
				{"name": "%s", "fixtureTypeId": "generic-rgbw-par", "mode": "8ch", "universeId": %d, "address": %d}
				""".formatted(name, universe, address);
	}

	private MvcTestResult post(String uri, String json) {
		return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

	private void patch(String uri, String json) {
		assertThat(mvc.patch().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange()).hasStatusOk();
	}

	private String body(String uri) throws Exception {
		return mvc.get().uri(uri).exchange().getResponse().getContentAsString();
	}

	private static long id(MvcTestResult result) throws Exception {
		assertThat(result.getResponse().getStatus()).isEqualTo(201);
		return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
	}
}
