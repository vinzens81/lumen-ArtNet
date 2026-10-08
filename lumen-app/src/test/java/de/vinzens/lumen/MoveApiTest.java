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
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@AutoConfigureMockMvc
class MoveApiTest {

	@Autowired
	MockMvcTester mvc;

	private long project;
	private long head1;
	private long head2;
	private long par;
	private String base;

	@BeforeEach
	void createActiveProject() throws Exception {
		project = id(post("/api/projects", "{\"name\": \"Moves\"}"));
		assertThat(post("/api/projects/" + project + "/activate", "")).hasStatusOk();
		base = "/api/projects/" + project;
		var universe = id(post(base + "/universes", "{\"number\": 1}"));
		head1 = id(post(base + "/fixtures", fixture("MH 1", "generic-moving-wash-rgbw", "9ch", universe, 1)));
		head2 = id(post(base + "/fixtures", fixture("MH 2", "generic-moving-wash-rgbw", "9ch", universe, 10)));
		par = id(post(base + "/fixtures", fixture("PAR", "generic-rgbw-par", "8ch", universe, 20)));
		assertThat(mvc.delete().uri("/api/programmer")).hasStatus(204);
	}

	@Test
	void figuresAndMovesArePlayedByScenesAndCannotBeDeletedWhileInUse() throws Exception {
		var figure = id(post(base + "/figures", """
				{"name": "Line", "closed": true, "points": [{"x": 0, "y": 127.5}, {"x": 255, "y": 127.5}]}"""));
		assertThat(post(base + "/moves", move("PAR move", figure, par))).hasStatus(400)
				.bodyText().contains("has no pan and tilt");
		var move = id(post(base + "/moves", move("Sweep", figure, head1, head2)));
		assertThat(mvc.get().uri(base + "/moves/" + move)).bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.sizeX").isEqualTo(100.0);
			assertThat(json).extractingPath("$.fixtures[1].shiftX").isEqualTo(-12.0);
			assertThat(json).extractingPath("$.repeat").isEqualTo("loop");
		});

		var scene = id(post(base + "/scenes", "{\"name\": \"Song\"}"));
		assertThat(put(base + "/scenes/" + scene + "/sections", "{\"marker\": \"\", \"moves\": [{\"moveId\": %d}]}"
				.formatted(move))).hasStatusOk().bodyJson().extractingPath("$.moves[0].moveId").isEqualTo((int) move);
		assertThat(put(base + "/scenes/" + scene + "/keys", "[{\"slot\": 1, \"moveId\": %d}]".formatted(move)))
				.hasStatusOk();

		assertThat(mvc.delete().uri(base + "/moves/" + move)).hasStatus(409);
		assertThat(mvc.delete().uri(base + "/figures/" + figure)).hasStatus(409);

		// the move plays in the show: GO puts the heads on the line
		assertThat(put("/api/mode", "{\"mode\": \"show\"}")).hasStatusOk();
		try {
			assertThat(post("/api/playback/go", "")).hasStatusOk();
			List<Integer> values = JsonPath.read(body("/api/output/universes/1/dmx"), "$.values");
			assertThat(values.get(1)).as("tilt of MH 1").isBetween(127, 128);
			assertThat(values.get(10)).as("tilt of MH 2").isBetween(127, 128);
		} finally {
			put("/api/mode", "{\"mode\": \"setup\"}");
		}
	}

	@Test
	void looksKeepNoPositionAndRecordPositionMakesAMoveWithOnePoint() throws Exception {
		patch("/api/programmer/fixtures/" + head1, "{\"pan\": 0.4, \"tilt\": 0.6, \"red\": 1}");
		patch("/api/programmer/fixtures/" + head2, "{\"pan\": 0.6, \"tilt\": 0.5, \"red\": 1}");

		var look = id(post(base + "/looks", "{\"name\": \"Red\", \"record\": true}"));
		assertThat(mvc.get().uri(base + "/looks/" + look)).bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.values['%d'].red".formatted(head1)).isEqualTo(1.0);
			assertThat(json).doesNotHavePath("$.values['%d'].pan".formatted(head1));
		});
		assertThat(post(base + "/presets", "{\"name\": \"Chair\", \"group\": \"position\", \"fixtureIds\": [%d]}"
				.formatted(head1))).hasStatus(400);

		var recorded = post(base + "/moves/record-position", "{\"name\": \"Singer\", \"fixtureIds\": [%d, %d, %d]}"
				.formatted(head1, head2, par));
		assertThat(recorded).hasStatusOk().bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.name").isEqualTo("Singer");
			assertThat(json).extractingPath("$.fixtures.length()").isEqualTo(2);
		});
		var response = recorded.getResponse().getContentAsString();
		var move = ((Number) JsonPath.read(response, "$.id")).longValue();
		var figure = ((Number) JsonPath.read(response, "$.figureId")).longValue();
		assertThat(JsonPath.<Number>read(response, "$.fixtures[0].shiftX").doubleValue()).isCloseTo(-25.5,
				within(1e-6));
		assertThat(JsonPath.<Number>read(response, "$.fixtures[1].shiftY").doubleValue()).isCloseTo(-12.75,
				within(1e-6));
		assertThat(mvc.get().uri(base + "/figures/" + figure)).bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.points.length()").isEqualTo(1);
			assertThat(json).extractingPath("$.points[0].x").isEqualTo(127.5);
		});

		// recording again updates the move and its figure
		patch("/api/programmer/fixtures/" + head1, "{\"pan\": 1}");
		var again = post(base + "/moves/record-position", "{\"moveId\": %d, \"fixtureIds\": [%d]}"
				.formatted(move, head1));
		assertThat(again).hasStatusOk().bodyJson().extractingPath("$.figureId").isEqualTo((int) figure);
		assertThat(mvc.get().uri(base + "/figures/" + figure)).bodyJson()
				.extractingPath("$.points[0].x").isEqualTo(255.0);
	}

	@Test
	void movesTravelWithTheProjectExport() throws Exception {
		var figure = id(post(base + "/figures", """
				{"name": "Square", "closed": true, "points": [{"x": 64, "y": 64}, {"x": 192, "y": 64},
				 {"x": 192, "y": 192}, {"x": 64, "y": 192}]}"""));
		var move = id(post(base + "/moves", move("Square", figure, head1, head2)));
		var scene = id(post(base + "/scenes", "{\"name\": \"Song\", \"script\": \"//## Chorus ##\"}"));
		put(base + "/scenes/" + scene + "/sections", "{\"marker\": \"Chorus\", \"moves\": [{\"moveId\": %d}]}"
				.formatted(move));
		put(base + "/party-matrix", """
				{"rows": 2, "columns": 2, "cells": [{"row": 0, "column": 1, "moveId": %d}]}""".formatted(move));

		var file = mvc.get().uri(base + "/export").exchange().getResponse().getContentAsString();
		assertThat(JsonPath.<Integer>read(file, "$.version")).isEqualTo(4);
		var copy = "/api/projects/" + id(post("/api/projects/import", file));

		var moves = body(copy + "/moves");
		var newMove = JsonPath.<Integer>read(moves, "$[0].id");
		assertThat(newMove).isNotEqualTo((int) move);
		assertThat(JsonPath.<Number>read(moves, "$[0].fixtures[1].shiftX").doubleValue()).isEqualTo(-12.0);
		var newHead2 = JsonPath.<List<Integer>>read(body(copy + "/fixtures"), "$[?(@.name == 'MH 2')].id")
				.getFirst();
		assertThat(JsonPath.<Integer>read(moves, "$[0].fixtures[1].fixtureId")).isEqualTo(newHead2);
		assertThat(JsonPath.<List<Integer>>read(body(copy + "/figures"), "$[*].id"))
				.containsExactly(JsonPath.<Integer>read(moves, "$[0].figureId"));
		assertThat(JsonPath.<Integer>read(body(copy + "/scenes"), "$[0].sections[0].moves[0].moveId"))
				.isEqualTo(newMove);
		assertThat(JsonPath.<Integer>read(body(copy + "/party-matrix"), "$.cells[0].moveId")).isEqualTo(newMove);
	}

	@Test
	void moveInDarkIsSetPerSceneWithTheProjectsTimes() throws Exception {
		assertThat(mvc.get().uri(base)).bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.dimOutMs").isEqualTo(500);
			assertThat(json).extractingPath("$.travelMs").isEqualTo(1500);
		});
		assertThat(put(base, """
				{"name": "Moves", "fadeInMs": 3000, "fadeOutMs": 3000, "dimOutMs": 300, "travelMs": 2000}"""))
				.hasStatusOk().bodyJson().extractingPath("$.travelMs").isEqualTo(2000);
		var scene = id(post(base + "/scenes", "{\"name\": \"Dark\", \"moveInDark\": true, \"travelMs\": 1000}"));
		assertThat(mvc.get().uri(base + "/scenes")).bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$[0].id").isEqualTo((int) scene);
			assertThat(json).extractingPath("$[0].moveInDark").isEqualTo(true);
			assertThat(json).extractingPath("$[0].travelMs").isEqualTo(1000);
			assertThat(json).extractingPath("$[0].dimOutMs").isNull();
		});
		var file = mvc.get().uri(base + "/export").exchange().getResponse().getContentAsString();
		var copy = "/api/projects/" + id(post("/api/projects/import", file));
		assertThat(mvc.get().uri(copy + "/scenes")).bodyJson().extractingPath("$[0].moveInDark").isEqualTo(true);
		assertThat(mvc.get().uri(copy)).bodyJson().extractingPath("$.dimOutMs").isEqualTo(300);
	}

	private static String move(String name, long figure, long... fixtures) {
		var entries = new StringBuilder();
		for (int i = 0; i < fixtures.length; i++) {
			entries.append(i == 0 ? "" : ", ").append("{\"fixtureId\": %d, \"shiftX\": %d}"
					.formatted(fixtures[i], i * -12));
		}
		return "{\"name\": \"%s\", \"figureId\": %d, \"fixtures\": [%s]}".formatted(name, figure, entries);
	}

	private static String fixture(String name, String type, String mode, long universe, int address) {
		return """
				{"name": "%s", "fixtureTypeId": "%s", "mode": "%s", "universeId": %d, "address": %d}
				""".formatted(name, type, mode, universe, address);
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

	private String body(String uri) throws Exception {
		return mvc.get().uri(uri).exchange().getResponse().getContentAsString();
	}

	private static long id(MvcTestResult result) throws Exception {
		assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
		return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
	}
}
