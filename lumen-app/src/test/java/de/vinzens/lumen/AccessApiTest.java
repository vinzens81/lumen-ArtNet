package de.vinzens.lumen;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class AccessApiTest {

	private static final String PIN = "4711";

	@Autowired
	MockMvcTester mvc;

	private final MockHttpSession admin = new MockHttpSession();
	private final MockHttpSession tablet = new MockHttpSession();

	/** The Spring context is shared with other tests: remove the PIN again. */
	@AfterEach
	void removePin() {
		var cleanup = new MockHttpSession();
		send(cleanup, "POST", "/api/access/unlock", "{\"pin\": \"" + PIN + "\"}");
		send(cleanup, "PUT", "/api/mode", "{\"mode\": \"setup\"}");
		assertThat(send(cleanup, "PUT", "/api/access/pin", "{\"pin\": null}")).hasStatusOk();
	}

	@Test
	void withAPinOnlyUnlockedClientsMayChangeTheSetup() {
		assertThat(mvc.get().uri("/api/access").session(tablet)).bodyJson().extractingPath("$.unlocked").isEqualTo(true);

		assertThat(send(admin, "PUT", "/api/access/pin", "{\"pin\": \"" + PIN + "\"}")).hasStatusOk().bodyJson()
				.satisfies(json -> {
					assertThat(json).extractingPath("$.pinSet").isEqualTo(true);
					assertThat(json).extractingPath("$.unlocked").as("whoever sets the PIN stays unlocked").isEqualTo(true);
				});
		assertThat(send(admin, "POST", "/api/projects", "{\"name\": \"Admin\"}")).hasStatus(201);

		var locked = send(tablet, "POST", "/api/projects", "{\"name\": \"Tablet\"}");
		assertThat(locked).hasStatus(403).bodyJson().extractingPath("$.locked").isEqualTo(true);
		assertThat(mvc.get().uri("/api/projects").session(tablet)).as("reading is open").hasStatusOk();
		assertThat(send(tablet, "PUT", "/api/output/master", "{\"level\": 1}")).as("show operation is open").hasStatusOk();
		assertThat(send(tablet, "POST", "/api/playback/pause", "")).hasStatusOk();
		send(tablet, "POST", "/api/playback/pause", "");

		assertThat(send(tablet, "POST", "/api/access/unlock", "{\"pin\": \"0000\"}")).hasStatus(403).bodyJson()
				.extractingPath("$.detail").isEqualTo("Wrong PIN");
		assertThat(send(tablet, "POST", "/api/access/unlock", "{\"pin\": \"" + PIN + "\"}")).hasStatusOk();
		assertThat(send(tablet, "POST", "/api/projects", "{\"name\": \"Tablet\"}")).hasStatus(201);

		// entering show mode locks everyone, leaving it needs the PIN
		assertThat(send(tablet, "PUT", "/api/mode", "{\"mode\": \"show\"}")).hasStatusOk();
		assertThat(send(admin, "PUT", "/api/mode", "{\"mode\": \"setup\"}")).hasStatus(403);
		assertThat(send(admin, "POST", "/api/access/unlock", "{\"pin\": \"" + PIN + "\"}")).hasStatusOk();
		assertThat(send(admin, "PUT", "/api/mode", "{\"mode\": \"setup\"}")).hasStatusOk();

		assertThat(send(admin, "POST", "/api/access/lock", "")).hasStatus(204);
		assertThat(send(admin, "POST", "/api/projects", "{\"name\": \"Again\"}")).hasStatus(403);
	}

	@Test
	void networkAccessTakesEffectAfterARestart() {
		assertThat(send(admin, "PUT", "/api/access/network", "{\"enabled\": true}")).hasStatusOk().bodyJson()
				.satisfies(json -> {
					assertThat(json).extractingPath("$.networkAccess").isEqualTo(true);
					assertThat(json).extractingPath("$.networkAccessActive").isEqualTo(false);
				});
		assertThat(send(admin, "PUT", "/api/access/network", "{\"enabled\": false}")).hasStatusOk();
	}

	private MvcTestResult send(MockHttpSession session, String method, String uri, String json) {
		var request = switch (method) {
			case "POST" -> mvc.post();
			case "PUT" -> mvc.put();
			default -> throw new IllegalArgumentException(method);
		};
		return request.uri(uri).session(session).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}
}
