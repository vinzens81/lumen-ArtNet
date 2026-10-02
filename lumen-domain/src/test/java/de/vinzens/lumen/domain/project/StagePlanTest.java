package de.vinzens.lumen.domain.project;

import de.vinzens.lumen.domain.DomainValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StagePlanTest {

	static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

	@Test
	void acceptsImagesByTheirSignature() {
		assertThat(new StagePlan("image/png", PNG).data()).isEqualTo(PNG);
		assertThat(new StagePlan("image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1}).contentType())
				.isEqualTo("image/jpeg");
		new StagePlan("image/webp", "RIFF\0\0\0\0WEBPVP8 ".getBytes());
	}

	@Test
	void rejectsOtherFiles() {
		assertThatThrownBy(() -> new StagePlan("image/svg+xml", "<svg/>".getBytes()))
				.isInstanceOf(DomainValidationException.class);
		assertThatThrownBy(() -> new StagePlan("image/png", "<script>".getBytes()))
				.isInstanceOf(DomainValidationException.class).hasMessageContaining("does not match");
		assertThatThrownBy(() -> new StagePlan("image/png", new byte[StagePlan.MAX_BYTES + 1]))
				.isInstanceOf(DomainValidationException.class);
	}
}
