package shinhan.fibri.ieum.ai.internal.security;

import java.security.MessageDigest;

final class Hex {

	private static final char[] LOWER = "0123456789abcdef".toCharArray();

	private Hex() {
	}

	static String sha256(byte[] bytes) {
		try {
			return lower(MessageDigest.getInstance("SHA-256").digest(bytes));
		}
		catch (Exception ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}

	static String lower(byte[] bytes) {
		char[] out = new char[bytes.length * 2];
		for (int i = 0; i < bytes.length; i++) {
			int value = bytes[i] & 0xff;
			out[i * 2] = LOWER[value >>> 4];
			out[(i * 2) + 1] = LOWER[value & 0x0f];
		}
		return new String(out);
	}

}
