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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class PartyApiTest {

	@Autowired
	MockMvcTester mvc;

	private long par;
	private String buttons;

	@BeforeEach
	void createRig() throws Exception {
		var project = id(post("/api/projects", "{\"name\": \"Party\"}"));
		assertThat(post("/api/projects/" + project + "/activate", "")).hasStatusOk();
		var base = "/api/projects/" + project;
		var universe = id(post(base + "/universes", "{\"number\": 1}"));
		par = id(post(base + "/fixtures", """
				{"name": "PAR", "fixtureTypeId": "generic-rgbw-par", "mode": "8ch", "universeId": %d, "address": 1}
				""".formatted(universe)));
		buttons = base + "/party-buttons";
		assertThat(mvc.delete().uri("/api/programmer")).hasStatus(204);
	}

	@AfterEach
	void backToSetup() {
		put("/api/mode", "{\"mode\": \"setup\"}");
	}

	@Test
	void palettesAndButtonsDriveTheLightOnlyInPartyMode() throws Exception {
		// a button is recorded from the programmer in setup mode
		assertThat(post(buttons, "{\"name\": \"Strobe\"}")).as("empty programmer").hasStatus(409);
		patch("/api/programmer/fixtures/" + par, "{\"strobe\": 1}");
		var strobe = id(post(buttons, "{\"name\": \"Strobe\", \"mode\": \"flash\"}"));
		assertThat(mvc.delete().uri("/api/programmer")).hasStatus(204);
		assertThat(post("/api/party/palette", palette("red"))).as("not in party mode").hasStatus(409);

		assertThat(put("/api/mode", "{\"mode\": \"party\"}")).hasStatusOk().bodyJson()
				.extractingPath("$.mode").isEqualTo("party");
		assertThat(post("/api/party/palette", palette("red"))).hasStatusOk().bodyJson()
				.satisfies(json -> {
					assertThat(json).extractingPath("$.fixtures['%d'].palette".formatted(par)).isEqualTo("red");
					assertThat(json).extractingPath("$.palettes.length()").isEqualTo(10);
				});
		Thread.sleep(100); // a render tick
		assertThat(dmx(6)).as("dimmer, red, green, blue, white, strobe").containsExactly(255, 255, 0, 0, 0, 0);

		assertThat(post("/api/party/buttons/" + strobe + "/press", "")).hasStatusOk().bodyJson()
				.extractingPath("$.buttons[0].on").isEqualTo(true);
		Thread.sleep(100);
		assertThat(dmx(6)[5]).isEqualTo(255);
		post("/api/party/buttons/" + strobe + "/release", "");
		assertThat(put("/api/party/intensity", "{\"fixtureIds\": [%d], \"level\": 0.5}".formatted(par)))
				.hasStatusOk();
		Thread.sleep(100);
		assertThat(dmx(6)).containsExactly(128, 255, 0, 0, 0, 0);

		assertThat(put("/api/mode", "{\"mode\": \"setup\"}")).hasStatusOk();
		Thread.sleep(100);
		assertThat(dmx(1)[0]).as("party mode gives no light in setup").isZero();
	}

	@Test
	void tapSetsTheTempo() throws Exception {
		put("/api/mode", "{\"mode\": \"party\"}");
		assertThat(put("/api/party/bpm", "{\"bpm\": 128}")).hasStatusOk().bodyJson()
				.satisfies(json -> {
					assertThat(json).extractingPath("$.bpm").isEqualTo(128.0);
					assertThat(json).extractingPath("$.beatMs").isEqualTo(468);
				});
		var tapped = post("/api/party/tap", "");
		assertThat(tapped).hasStatusOk();
		assertThat(JsonPath.<Integer>read(tapped.getResponse().getContentAsString(), "$.msSinceBeat"))
				.as("the tap is a beat").isLessThan(100);
	}

	@Test
	void buttonsCanBeRenamedReorderedAndDeleted() throws Exception {
		patch("/api/programmer/fixtures/" + par, "{\"white\": 1}");
		var white = id(post(buttons, "{\"name\": \"White\"}"));
		var second = id(post(buttons, "{\"name\": \"Second\"}"));

		assertThat(put(buttons + "/" + white, "{\"name\": \"Wash\", \"mode\": \"flash\"}")).hasStatusOk().bodyJson()
				.extractingPath("$.mode").isEqualTo("flash");
		assertThat(put(buttons + "/order", "{\"ids\": [%d, %d]}".formatted(second, white))).hasStatusOk().bodyJson()
				.extractingPath("$[1].name").isEqualTo("Wash");
		assertThat(put(buttons + "/" + white, "{\"name\": \"X\", \"mode\": \"blink\"}")).hasStatus(400);
		assertThat(mvc.delete().uri(buttons + "/" + white)).hasStatus(204);
		assertThat(JsonPath.<List<Object>>read(body(buttons), "$")).hasSize(1);
	}

	private String palette(String id) {
		return "{\"fixtureIds\": [%d], \"palette\": \"%s\"}".formatted(par, id);
	}

	private int[] dmx(int channels) throws Exception {
		List<Integer> values = JsonPath.read(body("/api/output/universes/1/dmx"), "$.values");
		return values.stream().limit(channels).mapToInt(Integer::intValue).toArray();
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

	private MvcTestResult patch(String uri, String json) {
		return mvc.patch().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

	private static long id(MvcTestResult result) throws Exception {
		assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
		return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
	}
}
