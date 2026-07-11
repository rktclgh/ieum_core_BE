package shinhan.fibri.ieum.main.ai.client;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class InternalRequestSigner {

	private static final String HMAC_ALGORITHM = "HmacSHA256";

	private final String service;
	private final String keyId;
	private final SecretKeySpec secretKey;
	private final Clock clock;

	public InternalRequestSigner(String service, String keyId, String secret, Clock clock) {
		this.service = requiredHeaderValue(service, "service");
		this.keyId = requiredHeaderValue(keyId, "keyId");
		this.secretKey = new SecretKeySpec(requiredSecret(secret).getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
		this.clock = Objects.requireNonNull(clock, "clock must not be null");
	}

	public SignedInternalRequest sign(
		String method,
		String rawPath,
		String rawQuery,
		byte[] rawBody,
		UUID requestId
	) {
		String normalizedMethod = requiredHeaderValue(method, "method").toUpperCase(java.util.Locale.ROOT);
		String normalizedPath = requiredPath(rawPath);
		String normalizedQuery = rawQuery == null ? "" : rawQuery;
		byte[] body = rawBody == null ? new byte[0] : rawBody;
		String requestIdValue = Objects.requireNonNull(requestId, "requestId must not be null").toString();
		long timestamp = clock.instant().getEpochSecond();
		String bodyHash = sha256(body);
		String canonical = String.join("\n",
			"v1",
			service,
			keyId,
			Long.toString(timestamp),
			requestIdValue,
			normalizedMethod,
			normalizedPath,
			normalizedQuery,
			bodyHash
		);
		return new SignedInternalRequest(service, keyId, timestamp, requestIdValue, bodyHash, hmac(canonical));
	}

	private String hmac(String canonical) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(secretKey);
			return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException | InvalidKeyException exception) {
			throw new IllegalStateException("HMAC-SHA256 must be available", exception);
		}
	}

	private String sha256(byte[] body) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 must be available", exception);
		}
	}

	private static String requiredHeaderValue(String value, String field) {
		if (value == null || value.isBlank() || value.chars().anyMatch(Character::isISOControl)) {
			throw new IllegalArgumentException(field + " must be a nonblank header-safe value");
		}
		return value;
	}

	private static String requiredSecret(String secret) {
		if (secret == null || secret.isBlank()) {
			throw new IllegalArgumentException("secret must not be blank");
		}
		return secret;
	}

	private static String requiredPath(String rawPath) {
		if (rawPath == null || !rawPath.startsWith("/") || rawPath.chars().anyMatch(Character::isISOControl)) {
			throw new IllegalArgumentException("rawPath must be an absolute header-safe path");
		}
		return rawPath;
	}
}
