package de.vinzens.lumen;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

/** Imports into a library of its own, never into the real fixture-types folder. */
@SpringBootTest(properties = "lumen.fixture-library-dir=target/test-data/ofl-library")
@AutoConfigureMockMvc
class OflImportApiTest {

	private static final String FIXTURE = """
			{"name": "Flat PAR", "categories": ["Color Changer"],
			 "availableChannels": {
			   "Dimmer": {"capability": {"type": "Intensity"}},
			   "Red": {"capability": {"type": "ColorIntensity", "color": "Red"}}
			 },
			 "modes": [{"name": "2-channel", "shortName": "2ch", "channels": ["Dimmer", "Red"]}]}
			""";

	@Autowired
	MockMvcTester mvc;

	@BeforeAll
	static void emptyLibrary() throws IOException {
		var library = Path.of("target/test-data/ofl-library");
		if (Files.exists(library)) {
			try (var files = Files.walk(library)) {
				files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
			}
		}
		Files.createDirectories(library);
	}

	@Test
	void importedFixturesJoinTheLibrary() {
		assertThat(importOfl(FIXTURE, "false")).hasStatus(201).bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.fixtureType.id").isEqualTo("acme-flat-par");
			assertThat(json).extractingPath("$.source").isEqualTo("acme/acme-flat-par.yml");
		});
		assertThat(mvc.get().uri("/api/library/fixture-types/acme-flat-par")).hasStatusOk().bodyJson()
				.extractingPath("$.modes[0].channels[1].function").isEqualTo("red");
		assertThat(Path.of("target/test-data/ofl-library/acme/acme-flat-par.yml")).exists();

		assertThat(importOfl(FIXTURE, "false")).hasStatus(409);
		assertThat(importOfl(FIXTURE, "true")).hasStatus(201);
		assertThat(importOfl("{\"name\": 1}", "false")).hasStatus(400);
	}

	private MvcTestResult importOfl(String json, String replace) {
		return mvc.post().uri("/api/library/import/ofl?manufacturer=Acme&replace=" + replace)
				.contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}
}
