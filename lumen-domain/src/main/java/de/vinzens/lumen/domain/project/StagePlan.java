package de.vinzens.lumen.domain.project;

import de.vinzens.lumen.domain.Validation;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

/**
 * Optional floor plan image behind the stage view. Only PNG, JPEG and WebP are accepted (no SVG: it could carry
 * scripts), checked by the file signature, not only by the declared type.
 */
public final class StagePlan {

	public static final int MAX_BYTES = 5 * 1024 * 1024;
	public static final Set<String> CONTENT_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

	private final String contentType;
	private final byte[] data;

	public StagePlan(String contentType, byte[] data) {
		Objects.requireNonNull(data, "data");
		new Validation()
				.require(CONTENT_TYPES.contains(contentType),
						"The floor plan must be a PNG, JPEG or WebP image, was " + contentType)
				.require(data.length > 0, "The floor plan image is empty")
				.require(data.length <= MAX_BYTES, "The floor plan image is larger than 5 MB")
				.throwIfInvalid();
		new Validation()
				.require(matchesSignature(contentType, data), "The file content does not match its type " + contentType)
				.throwIfInvalid();
		this.contentType = contentType;
		this.data = data.clone();
	}

	public String contentType() {
		return contentType;
	}

	public byte[] data() {
		return data.clone();
	}

	private static boolean matchesSignature(String contentType, byte[] data) {
		return switch (contentType) {
			case "image/png" -> startsWith(data, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);
			case "image/jpeg" -> startsWith(data, 0, 0xFF, 0xD8, 0xFF);
			case "image/webp" -> startsWith(data, 0, 'R', 'I', 'F', 'F') && startsWith(data, 8, 'W', 'E', 'B', 'P');
			default -> false;
		};
	}

	private static boolean startsWith(byte[] data, int offset, int... signature) {
		if (data.length < offset + signature.length) {
			return false;
		}
		for (int i = 0; i < signature.length; i++) {
			if ((data[offset + i] & 0xFF) != signature[i]) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof StagePlan plan && contentType.equals(plan.contentType)
				&& Arrays.equals(data, plan.data);
	}

	@Override
	public int hashCode() {
		return 31 * contentType.hashCode() + Arrays.hashCode(data);
	}
}
