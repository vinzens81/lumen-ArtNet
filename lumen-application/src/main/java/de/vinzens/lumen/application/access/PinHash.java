package de.vinzens.lumen.application.access;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Salted PBKDF2 hash of the configuration PIN, stored as {@code pbkdf2-sha256$<iterations>$<salt>$<hash>}.
 */
final class PinHash {

	private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
	private static final String PREFIX = "pbkdf2-sha256";
	private static final int ITERATIONS = 210_000;
	private static final int SALT_BYTES = 16;
	private static final int HASH_BITS = 256;
	private static final SecureRandom RANDOM = new SecureRandom();

	private PinHash() {
	}

	static String hash(String pin) {
		var salt = new byte[SALT_BYTES];
		RANDOM.nextBytes(salt);
		var encoder = Base64.getEncoder().withoutPadding();
		return String.join("$", PREFIX, String.valueOf(ITERATIONS), encoder.encodeToString(salt),
				encoder.encodeToString(derive(pin, salt, ITERATIONS)));
	}

	static boolean matches(String pin, String stored) {
		var parts = stored.split("\\$");
		if (parts.length != 4 || !PREFIX.equals(parts[0])) {
			return false;
		}
		try {
			var decoder = Base64.getDecoder();
			var expected = decoder.decode(parts[3]);
			var actual = derive(pin, decoder.decode(parts[2]), Integer.parseInt(parts[1]));
			return MessageDigest.isEqual(expected, actual);
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	private static byte[] derive(String pin, byte[] salt, int iterations) {
		try {
			var spec = new PBEKeySpec(pin.toCharArray(), salt, iterations, HASH_BITS);
			return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(ALGORITHM + " is not available", e);
		}
	}
}
