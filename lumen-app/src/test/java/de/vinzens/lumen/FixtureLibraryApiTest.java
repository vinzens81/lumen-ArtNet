package de.vinzens.lumen;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lädt die echten Beispielprofile aus {@code fixture-types/} im Projekt-Root.
 */
@SpringBootTest(properties = "lumen.fixture-library-dir=../fixture-types")
@AutoConfigureMockMvc
class FixtureLibraryApiTest {

	@Autowired
	MockMvcTester mvc;

	@Test
	void exampleProfilesLoadWithoutIssues() {
		assertThat(mvc.get().uri("/api/library/issues")).hasStatusOk()
				.bodyJson().extractingPath("$").asArray().isEmpty();
	}

	@Test
	void listsFixtureTypesWithDmxOffsets() {
		var response = assertThat(mvc.get().uri("/api/library/fixture-types")).hasStatusOk().bodyJson();

		response.extractingPath("$[*].id").asArray()
				.containsExactly("example-moving-head-spot", "generic-dimmer", "generic-rgbw-par");
		response.extractingPath("$[0].modes[0].dmxChannelCount").isEqualTo(13);
		// Tilt folgt auf den 16-Bit-Pan-Kanal und beginnt daher bei Offset 3
		response.extractingPath("$[0].modes[0].channels[1].offset").isEqualTo(3);
		response.extractingPath("$[0].modes[0].channels[5].fade").isEqualTo("snap");
	}

	@Test
	void unknownFixtureTypeIs404() {
		assertThat(mvc.get().uri("/api/library/fixture-types/gibts-nicht")).hasStatus(404);
	}
}
