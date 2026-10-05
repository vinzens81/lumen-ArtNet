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

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class CompositionApiTest {

	@Autowired
	MockMvcTester mvc;

	private long project;
	private long ramp;
	private long par1;
	private long par2;
	private String base;

	@BeforeEach
	void createRig() throws Exception {
		project = id(post("/api/projects", "{\"name\": \"Fair\", \"fadeInMs\": 0, \"fadeOutMs\": 0}"));
		assertThat(post("/api/projects/" + project + "/activate", "")).hasStatusOk();
		base = "/api/projects/" + project;
		var universe = id(post(base + "/universes", "{\"number\": 1}"));
		var dimmers = new long[4];
		for (int i = 0; i < 4; i++) {
			dimmers[i] = id(post(base + "/fixtures", fixture("generic-dimmer", "1ch", universe, i + 1)));
		}
		par1 = id(post(base + "/fixtures", fixture("generic-rgbw-par", "8ch", universe, 10)));
		par2 = id(post(base + "/fixtures", fixture("generic-rgbw-par", "8ch", universe, 20)));
		ramp = id(post(base + "/groups", "{\"name\": \"Ramp\", \"fixtureIds\": [%d, %d, %d, %d]}"
				.formatted(dimmers[0], dimmers[1], dimmers[2], dimmers[3])));
	}

	@AfterEach
	void backToSetup() {
		put("/api/mode", "{\"mode\": \"setup\"}");
	}

	@Test
	void libraryCompositionsAreTypedAndValidated() throws Exception {
		var created = post(base + "/compositions", """
				{"name": "Fair chase", "kind": "chase", "params": {"stepMs": 400, "direction": "ping_pong"},
				 "target": {"groupId": %d}}""".formatted(ramp));
		assertThat(created).hasStatus(201).bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.params.stepMs").isEqualTo(400);
			assertThat(json).extractingPath("$.params.width").as("default").isEqualTo(1);
			assertThat(json).extractingPath("$.params.direction").isEqualTo("ping_pong");
			assertThat(json).extractingPath("$.target.groupId").isEqualTo((int) ramp);
			assertThat(json).extractingPath("$.sceneId").isNull();
		});

		assertThat(post(base + "/compositions", """
				{"name": "X", "kind": "chase", "params": {"stepMs": 400, "speeed": 2}, "target": {"groupId": %d}}"""
				.formatted(ramp))).as("unknown parameter").hasStatus(400);
		assertThat(post(base + "/compositions", """
				{"name": "X", "kind": "laser", "target": {"groupId": %d}}""".formatted(ramp))).hasStatus(400);
		assertThat(post(base + "/compositions", """
				{"name": "X", "kind": "rainbow", "target": {"fixtureIds": [999999]}}""")).hasStatus(400)
				.bodyJson().extractingPath("$.errors[0]").isEqualTo("Fixture 999999 does not exist in this project");
		assertThat(post(base + "/compositions", """
				{"name": "X", "kind": "color_cycle", "params": {"colors": ["red"]}, "target": {"groupId": %d}}"""
				.formatted(ramp))).hasStatus(400);

		var steps = post(base + "/compositions", """
				{"name": "Steps", "kind": "steps", "params": {"playMode": "ping_pong"},
				 "steps": [{"holdMs": 500, "fadeMs": 0, "values": {"%d": {"dimmer": 1, "red": 1}}},
				           {"holdMs": 500, "values": {"%d": {"dimmer": 1, "blue": 1}}}]}""".formatted(par1, par2));
		assertThat(steps).hasStatus(201).bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.target").isNull();
			assertThat(json).extractingPath("$.steps[1].fadeMs").as("default").isEqualTo(0);
			assertThat(json).extractingPath("$.steps[1].values['%d'].blue".formatted(par2)).isEqualTo(1.0);
		});
		assertThat(JsonPath.<List<String>>read(body(base + "/compositions"), "$[*].name"))
				.containsExactly("Fair chase", "Steps");
	}

	@Test
	void sceneCompositionsRunInShowMode() throws Exception {
		var chase = id(post(base + "/compositions", """
				{"name": "Fair chase", "kind": "chase", "params": {"stepMs": 100}, "target": {"groupId": %d}}"""
				.formatted(ramp)));
		var colors = id(post(base + "/compositions", """
				{"name": "Booths", "kind": "color_cycle",
				 "params": {"colors": ["#ff0000", "#00ff00"], "holdMs": 1000, "fadeMs": 0},
				 "target": {"fixtureIds": [%d, %d]}}""".formatted(par1, par2)));
		var scene = id(post(base + "/scenes", "{\"name\": \"Fair\"}"));
		var copies = base + "/scenes/" + scene + "/compositions";
		var chaseCopy = id(post(copies, "{\"libraryId\": " + chase + "}"));
		var colorCopy = id(post(copies, "{\"libraryId\": " + colors + "}"));
		assertThat(mvc.get().uri(copies)).bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$[0].sourceId").isEqualTo((int) chase);
			assertThat(json).extractingPath("$[1].position").isEqualTo(1);
		});

		assertThat(put("/api/mode", "{\"mode\": \"show\"}")).hasStatusOk();
		assertThat(post("/api/playback/go", "")).hasStatusOk();

		// the chase lights exactly one dimmer at a time and moves; the PARs show red at full intensity
		var lit = new HashSet<Integer>();
		for (int sample = 0; sample < 8; sample++) {
			var dmx = dmx();
			var on = 0;
			for (int channel = 0; channel < 4; channel++) {
				if (dmx[channel] == 255) {
					on++;
					lit.add(channel);
				}
			}
			assertThat(on).as("one dimmer on").isEqualTo(1);
			assertThat(new int[] {dmx[9], dmx[10], dmx[11]}).as("PAR 1: dimmer, red, green").containsExactly(255, 255, 0);
			Thread.sleep(60);
		}
		assertThat(lit).as("the chase moved").hasSizeGreaterThan(1);

		// a copy is edited independently of the library
		assertThat(put(base + "/compositions/" + colorCopy, """
				{"name": "Booths", "kind": "color_cycle", "params": {"colors": ["#0000ff", "#0000ff"]},
				 "target": {"fixtureIds": [%d]}, "level": 0.5}""".formatted(par1))).hasStatusOk();
		assertThat(JsonPath.<List<String>>read(body(base + "/compositions/" + colors), "$.params.colors"))
				.containsExactly("#ff0000", "#00ff00");
		assertThat(post(base + "/compositions/" + colorCopy + "/reload-from-library", "")).hasStatusOk().bodyJson()
				.satisfies(json -> {
					assertThat(json).extractingPath("$.params.colors[0]").isEqualTo("#ff0000");
					assertThat(json).extractingPath("$.level").as("own level stays").isEqualTo(0.5);
				});

		assertThat(put(copies + "/order", "{\"ids\": [%d, %d]}".formatted(colorCopy, chaseCopy))).hasStatusOk()
				.bodyJson().extractingPath("$[0].id").isEqualTo((int) colorCopy);
		assertThat(put(copies + "/order", "{\"ids\": [%d]}".formatted(colorCopy))).hasStatus(400);

		assertThat(mvc.delete().uri(base + "/compositions/" + chase)).hasStatus(204);
		assertThat(body(base + "/compositions/" + chaseCopy)).as("copy keeps running").contains("\"sourceId\":null");
	}

	@Test
	void stagePreviewRunsAboveThePlaybackUntilStopped() throws Exception {
		var chase = id(post(base + "/compositions", """
				{"name": "Fair chase", "kind": "chase", "params": {"stepMs": 60000}, "target": {"groupId": %d}}"""
				.formatted(ramp)));
		assertThat(mvc.get().uri("/api/stage-preview")).bodyJson()
				.extractingPath("$.compositionId").isNull();

		assertThat(post(base + "/compositions/" + chase + "/stage-preview", "")).hasStatusOk().bodyJson()
				.extractingPath("$.compositionId").isEqualTo((int) chase);
		Thread.sleep(100); // a render tick
		assertThat(dmx()[0]).as("first fixture of the chase").isEqualTo(255);

		assertThat(mvc.delete().uri("/api/stage-preview")).hasStatusOk().bodyJson()
				.extractingPath("$.compositionId").isNull();
		assertThat(dmx()[0]).isZero();

		// a whole scene: its static value and its compositions
		var scene = id(post(base + "/scenes", "{\"name\": \"Fair\"}"));
		id(post(base + "/scenes/" + scene + "/compositions", "{\"libraryId\": " + chase + "}"));
		assertThat(post(base + "/scenes/" + scene + "/stage-preview", "")).hasStatusOk().bodyJson()
				.satisfies(json -> {
					assertThat(json).extractingPath("$.sceneId").isEqualTo((int) scene);
					assertThat(json).extractingPath("$.compositionId").isNull();
				});
		Thread.sleep(100);
		assertThat(dmx()[0]).isEqualTo(255);

		// a rehearsed GO into the first scene fades in from the dark stage with the scene's own 3 s
		assertThat(put(base + "/scenes/" + scene, "{\"number\": \"1\", \"name\": \"Fair\", \"fadeInMs\": 3000}"))
				.hasStatusOk();
		assertThat(post(base + "/scenes/" + scene + "/rehearse-fade", "")).hasStatusOk().bodyJson()
				.satisfies(json -> {
					assertThat(json).extractingPath("$.sceneId").isEqualTo((int) scene);
					assertThat(json).extractingPath("$.fade.fromSceneId").isNull();
					assertThat(json).extractingPath("$.fade.fadeInMs").isEqualTo(3000);
				});
		Thread.sleep(100);
		assertThat(dmx()[0]).as("still fading in").isBetween(1, 254);

		assertThat(post(base + "/compositions/" + chase + "/stage-preview", "")).hasStatusOk();
		assertThat(put("/api/mode", "{\"mode\": \"show\"}")).hasStatusOk();
		assertThat(mvc.get().uri("/api/stage-preview")).as("show mode stops it").bodyJson()
				.extractingPath("$.compositionId").isNull();
	}

	@Test
	void extraKeysPlayOverTheSceneOnlyWhereTheyAreEnabled() throws Exception {
		var storm = id(post(base + "/compositions", """
				{"name": "Storm", "kind": "wave", "params": {"shape": "square", "periodMs": 3600000, "min": 1, "max": 1},
				 "target": {"groupId": %d}, "once": true}""".formatted(ramp)));
		var first = id(post(base + "/scenes", "{\"name\": \"Act 1\"}"));
		var second = id(post(base + "/scenes", "{\"name\": \"Act 2\"}"));

		assertThat(put(base + "/extra-keys/3", "{\"libraryId\": " + storm + ", \"mode\": \"toggle\", \"fadeMs\": 0}"))
				.hasStatusOk().bodyJson().satisfies(json -> {
					assertThat(json).extractingPath("$.slot").isEqualTo(3);
					assertThat(json).extractingPath("$.composition.sourceId").isEqualTo((int) storm);
					assertThat(json).extractingPath("$.composition.once").isEqualTo(true);
				});
		assertThat(put(base + "/extra-keys/9", "{\"libraryId\": " + storm + "}")).hasStatus(400);
		assertThat(body(base + "/scenes/" + second + "/extra-keys")).as("new key: usable everywhere")
				.isEqualTo("{\"slots\":[3]}");
		var third = id(post(base + "/scenes", "{\"name\": \"Act 3\"}"));
		assertThat(body(base + "/scenes/" + third + "/extra-keys")).as("inherited from Act 2").isEqualTo("{\"slots\":[3]}");
		assertThat(put(base + "/scenes/" + second + "/extra-keys", "{\"slots\": []}")).hasStatusOk();
		assertThat(put(base + "/scenes/" + first + "/extra-keys", "{\"slots\": [5]}")).as("empty key").hasStatus(400);

		assertThat(put("/api/mode", "{\"mode\": \"show\"}")).hasStatusOk();
		assertThat(post("/api/playback/go", "")).hasStatusOk();
		assertThat(post("/api/playback/keys/3/press", "")).hasStatusOk().bodyJson()
				.extractingPath("$.keys[0].on").isEqualTo(true);
		Thread.sleep(100);
		assertThat(dmx()[0]).as("storm over Act 1").isEqualTo(255);

		Thread.sleep(250); // double-GO guard
		assertThat(post("/api/playback/go", "")).hasStatusOk().bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.keys[0].available").isEqualTo(false);
			assertThat(json).extractingPath("$.keys[0].on").isEqualTo(false);
		});
		Thread.sleep(100);
		assertThat(dmx()[0]).as("Act 2 has no storm key").isZero();
		put("/api/mode", "{\"mode\": \"setup\"}");

		var file = mvc.get().uri(base + "/export").exchange().getResponse().getContentAsString();
		var copy = "/api/projects/" + id(post("/api/projects/import", file));
		var keys = body(copy + "/extra-keys");
		assertThat(JsonPath.<Integer>read(keys, "$[0].slot")).isEqualTo(3);
		assertThat(JsonPath.<Boolean>read(keys, "$[0].composition.once")).isTrue();
		var copiedScenes = JsonPath.<List<Integer>>read(body(copy + "/scenes"), "$[*].id");
		assertThat(body(copy + "/scenes/" + copiedScenes.get(0) + "/extra-keys")).isEqualTo("{\"slots\":[3]}");
		assertThat(body(copy + "/scenes/" + copiedScenes.get(1) + "/extra-keys")).isEqualTo("{\"slots\":[]}");

		assertThat(mvc.delete().uri(base + "/extra-keys/3")).hasStatus(204);
		assertThat(body(base + "/scenes/" + first + "/extra-keys")).isEqualTo("{\"slots\":[]}");
	}

	@Test
	void keysCanPlayWholeScenesThatGoSkips() throws Exception {
		var scenes = base + "/scenes";
		id(post(scenes, "{\"number\": \"1\", \"name\": \"Act 1\"}"));
		// key-only scene: first dimmer of the ramp at full, the PARs red
		assertThat(mvc.delete().uri("/api/programmer")).hasStatus(204);
		var rampFirst = JsonPath.<List<Integer>>read(body(base + "/groups"), "$[0].fixtureIds").getFirst();
		assertThat(mvc.patch().uri("/api/programmer/fixtures/" + rampFirst).contentType(MediaType.APPLICATION_JSON)
				.content("{\"dimmer\": 1}")).hasStatusOk();
		assertThat(mvc.patch().uri("/api/programmer/fixtures/" + par1).contentType(MediaType.APPLICATION_JSON)
				.content("{\"dimmer\": 1, \"red\": 1}")).hasStatusOk();
		var look = id(post(scenes, "{\"number\": \"90\", \"name\": \"Storm look\", \"keyOnly\": true, \"record\": true}"));
		assertThat(mvc.delete().uri("/api/programmer")).hasStatus(204);

		assertThat(put(base + "/extra-keys/4", "{\"sceneId\": " + look + ", \"mode\": \"flash\"}")).hasStatusOk()
				.bodyJson().satisfies(json -> {
					assertThat(json).extractingPath("$.sceneId").isEqualTo((int) look);
					assertThat(json).extractingPath("$.composition").isNull();
				});
		assertThat(put(base + "/extra-keys/4", "{\"sceneId\": " + look + ", \"libraryId\": 1}")).hasStatus(400);
		assertThat(body(scenes + "/" + look + "/extra-keys")).as("key-only scenes get no keys").isEqualTo("{\"slots\":[]}");

		assertThat(put("/api/mode", "{\"mode\": \"show\"}")).hasStatusOk();
		assertThat(mvc.get().uri("/api/playback")).bodyJson().extractingPath("$.sceneCount").as("GO skips it")
				.isEqualTo(1);
		assertThat(post("/api/playback/go", "")).hasStatusOk();
		assertThat(post("/api/playback/keys/4/press", "")).hasStatusOk().bodyJson()
				.extractingPath("$.keys[0].kind").isEqualTo("scene");
		Thread.sleep(100);
		var dmx = dmx();
		assertThat(dmx[0]).as("storm look over Act 1").isEqualTo(255);
		assertThat(new int[] {dmx[9], dmx[10]}).as("PAR 1 dimmer and red").containsExactly(255, 255);
		assertThat(post("/api/playback/keys/4/release", "")).hasStatusOk();
		Thread.sleep(100);
		assertThat(dmx()[0]).as("flash released").isZero();
		put("/api/mode", "{\"mode\": \"setup\"}");

		var file = mvc.get().uri(base + "/export").exchange().getResponse().getContentAsString();
		var copy = "/api/projects/" + id(post("/api/projects/import", file));
		var copiedKey = body(copy + "/extra-keys");
		var copiedLook = JsonPath.<List<Integer>>read(body(copy + "/scenes"), "$[?(@.keyOnly == true)].id").getFirst();
		assertThat(JsonPath.<Integer>read(copiedKey, "$[0].sceneId")).isEqualTo(copiedLook);
	}

	@Test
	void keyOnlyScenesArePickedDirectlyInAScene() throws Exception {
		var scenes = base + "/scenes";
		var act1 = id(post(scenes, "{\"number\": \"1\", \"name\": \"Act 1\"}"));
		var act2 = id(post(scenes, "{\"number\": \"2\", \"name\": \"Act 2\"}"));
		var police = id(post(scenes, "{\"number\": \"90\", \"name\": \"Police\", \"keyOnly\": true}"));

		assertThat(post(scenes + "/" + act2 + "/extra-keys/scenes/" + police, "")).hasStatusOk()
				.bodyJson().extractingPath("$.slots").asArray().containsExactly(1);
		var keys = body(base + "/extra-keys");
		assertThat(JsonPath.<Integer>read(keys, "$[0].sceneId")).isEqualTo((int) police);
		assertThat(body(scenes + "/" + act1 + "/extra-keys")).as("only where it was picked").isEqualTo("{\"slots\":[]}");

		assertThat(post(scenes + "/" + act1 + "/extra-keys/scenes/" + police, "")).hasStatusOk()
				.bodyJson().extractingPath("$.slots").asArray().as("same key, no second one").containsExactly(1);
		assertThat(JsonPath.<List<Object>>read(body(base + "/extra-keys"), "$")).hasSize(1);
		assertThat(post(scenes + "/" + police + "/extra-keys/scenes/" + police, "")).hasStatus(400);
	}

	@Test
	void compositionsTravelWithTheProjectExport() throws Exception {
		var chase = id(post(base + "/compositions", """
				{"name": "Fair chase", "kind": "chase", "target": {"groupId": %d}}""".formatted(ramp)));
		id(post(base + "/compositions", """
				{"name": "Steps", "kind": "steps", "steps": [{"holdMs": 500, "values": {"%d": {"dimmer": 1}}}]}"""
				.formatted(par2)));
		var scene = id(post(base + "/scenes", "{\"number\": \"2.5\", \"name\": \"Fair\"}"));
		id(post(base + "/scenes/" + scene + "/compositions", "{\"libraryId\": " + chase + "}"));

		var file = mvc.get().uri(base + "/export").exchange().getResponse().getContentAsString();
		var copy = "/api/projects/" + id(post("/api/projects/import", file));

		var library = body(copy + "/compositions");
		assertThat(JsonPath.<List<String>>read(library, "$[*].name")).containsExactly("Fair chase", "Steps");
		var newGroup = JsonPath.<Integer>read(body(copy + "/groups"), "$[0].id");
		var newChase = JsonPath.<Integer>read(library, "$[0].id");
		assertThat(JsonPath.<Integer>read(library, "$[0].target.groupId")).isEqualTo(newGroup);
		var newPar2 = JsonPath.<List<Integer>>read(body(copy + "/fixtures"), "$[?(@.address == 20)].id").getFirst();
		assertThat(JsonPath.<Double>read(library, "$[1].steps[0].values['%d'].dimmer".formatted(newPar2))).isEqualTo(1.0);
		var newScene = JsonPath.<Integer>read(body(copy + "/scenes"), "$[0].id");
		var copies = body(copy + "/scenes/" + newScene + "/compositions");
		assertThat(JsonPath.<Integer>read(copies, "$[0].sourceId")).isEqualTo(newChase);
	}

	private int[] dmx() throws Exception {
		List<Integer> values = JsonPath.read(body("/api/output/universes/1/dmx"), "$.values");
		return values.stream().mapToInt(Integer::intValue).toArray();
	}

	private static String fixture(String type, String mode, long universe, int address) {
		return """
				{"name": "%s %d", "fixtureTypeId": "%s", "mode": "%s", "universeId": %d, "address": %d}
				""".formatted(type, address, type, mode, universe, address);
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
