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
	private String base;

	@BeforeEach
	void createRig() throws Exception {
		var project = id(post("/api/projects", "{\"name\": \"Party\"}"));
		assertThat(post("/api/projects/" + project + "/activate", "")).hasStatusOk();
		base = "/api/projects/" + project;
		var universe = id(post(base + "/universes", "{\"number\": 1}"));
		par = id(post(base + "/fixtures", """
				{"name": "PAR", "fixtureTypeId": "generic-rgbw-par", "mode": "8ch", "universeId": %d, "address": 1}
				""".formatted(universe)));
		assertThat(mvc.delete().uri("/api/programmer")).hasStatus(204);
	}

	@AfterEach
	void backToSetup() {
		put("/api/mode", "{\"mode\": \"setup\"}");
	}

	@Test
	void fieldsOfTheMatrixSwitchCompositionsOnlyInPartyMode() throws Exception {
		var red = recorded("Red", "{\"dimmer\": 1, \"red\": 1}");
		var blue = recorded("Blue", "{\"dimmer\": 1, \"blue\": 1}");
		var strobe = recorded("Strobe", "{\"strobe\": 1}");
		assertThat(put(base + "/party-matrix", """
				{"rows": 2, "columns": 3, "separators": [1], "cells": [
				  {"row": 0, "column": 0, "compositionId": %d, "group": "Colors", "color": "#ff0000"},
				  {"row": 0, "column": 1, "compositionId": %d, "group": "Colors", "borderColor": "#0000ff"},
				  {"row": 0, "column": 2, "label": "Colors"},
				  {"row": 1, "column": 0, "compositionId": %d, "mode": "flash"}]}""".formatted(red, blue, strobe)))
				.hasStatusOk().bodyJson().satisfies(json -> {
					assertThat(json).extractingPath("$.cells.length()").isEqualTo(4);
					assertThat(json).extractingPath("$.separators[0]").isEqualTo(1);
				});
		assertThat(post("/api/party/cells/0/0/press", "")).as("not in party mode").hasStatus(409);

		assertThat(put("/api/mode", "{\"mode\": \"party\"}")).hasStatusOk();
		assertThat(post("/api/party/cells/0/0/press", "")).hasStatusOk().bodyJson()
				.extractingPath("$.on[0].column").isEqualTo(0);
		Thread.sleep(100); // a render tick
		assertThat(dmx(6)).as("dimmer, red, green, blue, white, strobe").containsExactly(255, 255, 0, 0, 0, 0);

		post("/api/party/cells/0/1/press", "");
		post("/api/party/cells/1/0/press", "");
		Thread.sleep(100);
		assertThat(dmx(6)).as("blue instead of red, strobe on top").containsExactly(255, 0, 0, 255, 0, 255);
		assertThat(post("/api/party/cells/1/0/release", "")).hasStatusOk().bodyJson()
				.extractingPath("$.on.length()").isEqualTo(1);
		assertThat(post("/api/party/cells/0/2/press", "")).as("a text field").hasStatus(400);

		assertThat(put("/api/mode", "{\"mode\": \"setup\"}")).hasStatusOk();
		Thread.sleep(100);
		assertThat(dmx(1)[0]).as("party mode gives no light in setup").isZero();

		var copy = "/api/projects/" + id(post("/api/projects/import", body(base + "/export")));
		var matrix = body(copy + "/party-matrix");
		var newBlue = JsonPath.<List<Integer>>read(body(copy + "/compositions"), "$[?(@.name == 'Blue')].id")
				.getFirst();
		assertThat(JsonPath.<Integer>read(matrix, "$.cells[1].compositionId")).isEqualTo(newBlue);
		assertThat(JsonPath.<String>read(matrix, "$.cells[1].borderColor")).isEqualTo("#0000ff");
	}

	@Test
	void theMatrixRefusesFieldsOutsideAndUnknownCompositions() {
		assertThat(put(base + "/party-matrix", "{\"rows\": 1, \"columns\": 1, \"cells\": [{\"row\": 1, \"column\": 0}]}"))
				.hasStatus(400);
		assertThat(put(base + "/party-matrix", "{\"rows\": 1, \"columns\": 1, \"cells\": "
				+ "[{\"row\": 0, \"column\": 0, \"compositionId\": 999999}]}")).hasStatus(400);
		assertThat(mvc.get().uri(base + "/party-matrix")).bodyJson().extractingPath("$.rows").isEqualTo(4);
	}

	@Test
	void partyAndShowAreOnlyReachedFromSetup() {
		assertThat(put("/api/mode", "{\"mode\": \"party\"}")).hasStatusOk();
		assertThat(put("/api/mode", "{\"mode\": \"show\"}")).hasStatus(409);
		assertThat(put("/api/mode", "{\"mode\": \"setup\"}")).hasStatusOk();
		assertThat(put("/api/mode", "{\"mode\": \"show\"}")).hasStatusOk();
		assertThat(put("/api/mode", "{\"mode\": \"party\"}")).hasStatus(409);
	}

	/** A composition with one step recorded from the programmer. */
	private long recorded(String name, String values) throws Exception {
		patch("/api/programmer/fixtures/" + par, values);
		var composition = id(post(base + "/compositions", "{\"name\": \"%s\"}".formatted(name)));
		assertThat(post(base + "/compositions/" + composition + "/record-step", "")).hasStatusOk();
		assertThat(mvc.delete().uri("/api/programmer")).hasStatus(204);
		return composition;
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
