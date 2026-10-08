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
import tools.jackson.databind.json.JsonMapper;

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
		assertThat(mvc.delete().uri("/api/programmer")).hasStatus(204);
	}

	@AfterEach
	void backToSetup() {
		put("/api/mode", "{\"mode\": \"setup\"}");
	}

	@Test
	void compositionsPlayLooksWithDefaultAndOwnTimes() throws Exception {
		patch("/api/programmer/fixtures/" + par1, "{\"dimmer\": 1, \"red\": 1}");
		var warm = id(post(base + "/looks", "{\"name\": \"Warm\", \"record\": true}"));
		var dark = id(post(base + "/looks", "{\"name\": \"Dark\"}"));

		var created = post(base + "/compositions", """
				{"name": "Pulse", "fadeMs": 500, "playMode": "ping_pong",
				 "steps": [{"lookId": %d}, {"lookId": %d, "holdMs": 4000}]}""".formatted(warm, dark));
		assertThat(created).hasStatus(201).bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.fadeMs").isEqualTo(500);
			assertThat(json).extractingPath("$.holdMs").as("default").isEqualTo(2000);
			assertThat(json).extractingPath("$.playMode").isEqualTo("ping_pong");
			assertThat(json).extractingPath("$.intensityMode").isEqualTo("absolute");
			assertThat(json).extractingPath("$.generator").isNull();
			assertThat(json).extractingPath("$.steps[0].holdMs").as("the composition's").isNull();
			assertThat(json).extractingPath("$.steps[1].holdMs").isEqualTo(4000);
		});
		var pulse = id(created);

		assertThat(post(base + "/compositions", "{\"name\": \"X\", \"playMode\": \"wobble\"}")).hasStatus(400);
		assertThat(post(base + "/compositions", "{\"name\": \"X\", \"steps\": [{\"lookId\": 999999}]}"))
				.hasStatus(400);
		assertThat(post(base + "/compositions", "{\"name\": \"X\", \"fadeMs\": 0, \"holdMs\": 0}")).hasStatus(400);

		// recording a step gives the composition a look of its own
		patch("/api/programmer/fixtures/" + par1, "{\"red\": 0, \"blue\": 1}");
		assertThat(post(base + "/compositions/" + pulse + "/record-step", "")).hasStatusOk().bodyJson()
				.extractingPath("$.steps.length()").isEqualTo(3);
		var looks = body(base + "/looks");
		assertThat(JsonPath.<List<Integer>>read(looks, "$[?(@.name == 'Pulse – Step 3')].owner"))
				.containsExactly((int) pulse);
		assertThat(mvc.delete().uri(base + "/looks/" + warm)).as("still played").hasStatus(409);
		assertThat(post(base + "/compositions/" + pulse + "/duplicate", "")).hasStatus(201).bodyJson()
				.extractingPath("$.name").isEqualTo("Pulse (copy)");
	}

	@Test
	void generatorsMakeStepsFromEffects() throws Exception {
		var chase = id(post(base + "/compositions", "{\"name\": \"Fair chase\"}"));
		var generate = base + "/compositions/" + chase + "/generate";

		assertThat(post(generate, """
				{"kind": "chase", "params": {"stepMs": 400, "direction": "ping_pong"}, "target": {"groupId": %d}}"""
				.formatted(ramp))).hasStatusOk().bodyJson().satisfies(json -> {
					assertThat(json).extractingPath("$.steps.length()").as("one step per fixture").isEqualTo(4);
					assertThat(json).extractingPath("$.holdMs").isEqualTo(400);
					assertThat(json).extractingPath("$.playMode").isEqualTo("ping_pong");
					assertThat(json).extractingPath("$.generator.kind").isEqualTo("chase");
					assertThat(json).extractingPath("$.generator.params.width").as("default").isEqualTo(1);
					assertThat(json).extractingPath("$.generator.target.groupId").isEqualTo((int) ramp);
				});

		assertThat(post(generate, """
				{"kind": "chase", "params": {"stepMs": 400, "speeed": 2}, "target": {"groupId": %d}}"""
				.formatted(ramp))).as("unknown parameter").hasStatus(400);
		assertThat(post(generate, "{\"kind\": \"laser\", \"target\": {\"groupId\": %d}}".formatted(ramp)))
				.hasStatus(400);
		assertThat(post(generate, "{\"kind\": \"rainbow\", \"target\": {\"fixtureIds\": [999999]}}")).hasStatus(400)
				.bodyJson().extractingPath("$.errors[0]").isEqualTo("fixtures [999999] do not exist in this project");
		assertThat(post(generate, """
				{"kind": "color_cycle", "params": {"colors": ["red"]}, "target": {"groupId": %d}}""".formatted(ramp)))
				.hasStatus(400);

		// editing keeps what the steps were generated from
		var current = body(base + "/compositions/" + chase);
		var steps = JsonPath.<List<Object>>read(current, "$.steps");
		assertThat(put(base + "/compositions/" + chase, """
				{"name": "Fair chase", "fadeMs": 100, "holdMs": 300, "playMode": "loop", "steps": %s}"""
				.formatted(JsonMapper.builder().build().writeValueAsString(steps)))).hasStatusOk()
				.bodyJson().extractingPath("$.generator.kind").isEqualTo("chase");
	}

	@Test
	void sharedCompositionsRunSideBySideInShowMode() throws Exception {
		var chase = generated("Fair chase", """
				{"kind": "chase", "params": {"stepMs": 100}, "target": {"groupId": %d}}""".formatted(ramp));
		var booths = generated("Booths", """
				{"kind": "color_cycle", "params": {"colors": ["#ff0000", "#00ff00"], "holdMs": 1000, "fadeMs": 0},
				 "target": {"fixtureIds": [%d, %d]}}""".formatted(par1, par2));
		var scene = id(post(base + "/scenes", "{\"name\": \"Fair\"}"));
		assertThat(put(base + "/scenes/" + scene + "/compositions", """
				[{"compositionId": %d}, {"compositionId": %d}]""".formatted(chase, booths))).hasStatusOk()
				.bodyJson().extractingPath("$.compositions[1].compositionId").isEqualTo((int) booths);
		assertThat(put(base + "/scenes/" + scene + "/compositions", "[{\"compositionId\": 999999}]"))
				.hasStatus(400);

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

		// shared: changing the composition changes the scene
		assertThat(post(base + "/compositions/" + booths + "/generate", """
				{"kind": "color_cycle", "params": {"colors": ["#0000ff", "#0000ff"]}, "target": {"fixtureIds": [%d]}}"""
				.formatted(par1))).hasStatusOk();
		Thread.sleep(150);
		var dmx = dmx();
		assertThat(new int[] {dmx[9], dmx[10], dmx[12]}).as("PAR 1: dimmer, red, blue").containsExactly(255, 0, 255);

		put("/api/mode", "{\"mode\": \"setup\"}");
		assertThat(mvc.delete().uri(base + "/compositions/" + chase)).as("used in a scene").hasStatus(409);
	}

	@Test
	void stagePreviewRunsAboveThePlaybackUntilStopped() throws Exception {
		var chase = generated("Fair chase", """
				{"kind": "chase", "params": {"stepMs": 60000}, "target": {"groupId": %d}}""".formatted(ramp));
		assertThat(mvc.get().uri("/api/stage-preview")).bodyJson()
				.extractingPath("$.compositionId").isNull();

		assertThat(post(base + "/compositions/" + chase + "/stage-preview", "")).hasStatusOk().bodyJson()
				.extractingPath("$.compositionId").isEqualTo((int) chase);
		Thread.sleep(100); // a render tick
		assertThat(dmx()[0]).as("first fixture of the chase").isEqualTo(255);

		assertThat(mvc.delete().uri("/api/stage-preview")).hasStatusOk().bodyJson()
				.extractingPath("$.compositionId").isNull();
		assertThat(dmx()[0]).isZero();

		// a whole scene with its compositions
		var scene = id(post(base + "/scenes", "{\"name\": \"Fair\"}"));
		put(base + "/scenes/" + scene + "/compositions", "[{\"compositionId\": %d}]".formatted(chase));
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
	void keysBelongToScenesAndStayOnWhileTheNextSceneHasTheirComposition() throws Exception {
		var storm = generated("Storm", """
				{"kind": "wave", "params": {"shape": "square", "periodMs": 3600000, "min": 1, "max": 1},
				 "target": {"groupId": %d}}""".formatted(ramp));
		var scenes = base + "/scenes";
		var act1 = id(post(scenes, "{\"number\": \"1\", \"name\": \"Act 1\"}"));
		var act2 = id(post(scenes, "{\"number\": \"2\", \"name\": \"Act 2\"}"));
		id(post(scenes, "{\"number\": \"3\", \"name\": \"Act 3\"}"));
		assertThat(put(scenes + "/" + act1 + "/keys", "[{\"slot\": 3, \"compositionId\": %d}]".formatted(storm)))
				.hasStatusOk().bodyJson().extractingPath("$.keys[0].mode").isEqualTo("toggle");
		assertThat(put(scenes + "/" + act2 + "/keys", """
				[{"slot": 5, "compositionId": %d, "mode": "toggle", "fadeMs": 0}]""".formatted(storm))).hasStatusOk();
		assertThat(put(scenes + "/" + act2 + "/keys", "[{\"slot\": 9, \"compositionId\": %d}]".formatted(storm)))
				.hasStatus(400);
		assertThat(put(scenes + "/" + act2 + "/keys", "[{\"slot\": 1, \"compositionId\": 999999}]")).hasStatus(400);
		assertThat(post(scenes, "{\"number\": \"2.5\", \"name\": \"Interlude\"}")).hasStatus(201).bodyJson()
				.extractingPath("$.keys[0].slot").as("taken from Act 2").isEqualTo(5);

		assertThat(put("/api/mode", "{\"mode\": \"show\"}")).hasStatusOk();
		assertThat(post("/api/playback/go", "")).hasStatusOk();
		assertThat(post("/api/playback/keys/3/press", "")).hasStatusOk().bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.keys[0].on").isEqualTo(true);
			assertThat(json).extractingPath("$.keys[0].name").isEqualTo("Storm");
		});
		Thread.sleep(100);
		assertThat(dmx()[0]).as("storm over Act 1").isEqualTo(255);

		Thread.sleep(250); // double-GO guard
		assertThat(post("/api/playback/go", "")).hasStatusOk().bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.keys[0].slot").isEqualTo(5);
			assertThat(json).extractingPath("$.keys[0].on").as("the same composition: stays on").isEqualTo(true);
		});
		Thread.sleep(250);
		post("/api/playback/go", ""); // Interlude: still key 5
		Thread.sleep(250);
		assertThat(post("/api/playback/go", "")).hasStatusOk().bodyJson()
				.extractingPath("$.keys").asArray().as("Act 3 has no keys").isEmpty();
		Thread.sleep(100);
		assertThat(dmx()[0]).as("the storm went out").isZero();
	}

	@Test
	void looksCompositionsAndScenesTravelWithTheProjectExport() throws Exception {
		var chase = generated("Fair chase", "{\"kind\": \"chase\", \"target\": {\"groupId\": %d}}".formatted(ramp));
		patch("/api/programmer/fixtures/" + par2, "{\"dimmer\": 1}");
		var scene = id(post(base + "/scenes", "{\"number\": \"2.5\", \"name\": \"Fair\", \"record\": true}"));
		var recorded = JsonPath.<Integer>read(body(base + "/scenes"), "$[0].compositions[0].compositionId");
		put(base + "/scenes/" + scene + "/compositions", """
				[{"compositionId": %d}, {"compositionId": %d, "level": 0.5}]""".formatted(recorded, chase));
		put(base + "/scenes/" + scene + "/keys", "[{\"slot\": 2, \"compositionId\": %d, \"mode\": \"flash\"}]"
				.formatted(chase));

		var file = mvc.get().uri(base + "/export").exchange().getResponse().getContentAsString();
		assertThat(JsonPath.<Integer>read(file, "$.version")).isEqualTo(4);
		var copy = "/api/projects/" + id(post("/api/projects/import", file));

		var compositions = body(copy + "/compositions");
		assertThat(JsonPath.<List<String>>read(compositions, "$[*].name")).containsExactly("Fair", "Fair chase");
		var newChase = JsonPath.<Integer>read(compositions, "$[1].id");
		var newGroup = JsonPath.<Integer>read(body(copy + "/groups"), "$[0].id");
		assertThat(JsonPath.<Integer>read(compositions, "$[1].generator.target.groupId")).isEqualTo(newGroup);
		var newScene = body(copy + "/scenes");
		assertThat(JsonPath.<Integer>read(newScene, "$[0].compositions[1].compositionId")).isEqualTo(newChase);
		assertThat(JsonPath.<Double>read(newScene, "$[0].compositions[1].level")).isEqualTo(0.5);
		assertThat(JsonPath.<Integer>read(newScene, "$[0].keys[0].compositionId")).isEqualTo(newChase);
		var newPar2 = JsonPath.<List<Integer>>read(body(copy + "/fixtures"), "$[?(@.address == 20)].id").getFirst();
		var stepLook = JsonPath.<Integer>read(compositions, "$[0].steps[0].lookId");
		assertThat(JsonPath.<Double>read(body(copy + "/looks/" + stepLook), "$.values['%d'].dimmer".formatted(newPar2)))
				.isEqualTo(1.0);
	}

	/** A composition with steps generated from an effect. */
	private long generated(String name, String generator) throws Exception {
		var id = id(post(base + "/compositions", "{\"name\": \"%s\"}".formatted(name)));
		assertThat(post(base + "/compositions/" + id + "/generate", generator)).hasStatusOk();
		return id;
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

	private void patch(String uri, String json) {
		assertThat(mvc.patch().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange()).hasStatusOk();
	}

	private static long id(MvcTestResult result) throws Exception {
		assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
		return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
	}
}
