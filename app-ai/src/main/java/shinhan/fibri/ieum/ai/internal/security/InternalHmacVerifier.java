package shinhan.fibri.ieum.ai.internal.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class InternalHmacVerifier {

	private static final Pattern LOWER_64_HEX = Pattern.compile("[0-9a-f]{64}");
	private static final Pattern EPOCH_SECONDS = Pattern.compile("[0-9]+");
	private static final Set<String> REQUIRED_HEADERS = Set.of(
			"X-Internal-Service",
			"X-Internal-Key-Id",
			"X-Internal-Timestamp",
			"X-Internal-Request-Id",
			"X-Internal-Body-SHA256",
			"X-Internal-Signature");

	private final InternalHmacProperties properties;
	private final Clock clock;
	private final InternalHmacReplayCache replayCache;

	@Autowired
	public InternalHmacVerifier(InternalHmacProperties properties) {
		this(properties, Clock.systemUTC());
	}

	InternalHmacVerifier(InternalHmacProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
		this.replayCache = new InternalHmacReplayCache(clock, properties.getReplayMaxEntries(),
				properties.getReplayTtlSeconds());
	}

	public InternalHmacVerificationResult verify(InternalHmacVerificationRequest request) {
		if (request == null || request.headers() == null || request.body() == null) {
			return InternalHmacVerificationResult.rejected("malformed_request");
		}
		for (String header : REQUIRED_HEADERS) {
			if (!request.headers().hasExactlyOneValue(header)) {
				return InternalHmacVerificationResult.rejected("malformed_header");
			}
		}

		String service = request.headers().singleValue("X-Internal-Service");
		String keyId = request.headers().singleValue("X-Internal-Key-Id");
		String timestamp = request.headers().singleValue("X-Internal-Timestamp");
		String requestId = request.headers().singleValue("X-Internal-Request-Id");
		String bodyHash = request.headers().singleValue("X-Internal-Body-SHA256");
		String signature = request.headers().singleValue("X-Internal-Signature");

		if (!properties.getServiceName().equals(service)
				|| !isCanonicalUuid(requestId)
				|| !EPOCH_SECONDS.matcher(timestamp).matches()
				|| !LOWER_64_HEX.matcher(bodyHash).matches()
				|| !LOWER_64_HEX.matcher(signature).matches()) {
			return InternalHmacVerificationResult.rejected("malformed_header");
		}

		long epochSeconds;
		try {
			epochSeconds = Long.parseLong(timestamp);
		}
		catch (NumberFormatException ex) {
			return InternalHmacVerificationResult.rejected("malformed_header");
		}
		if (Math.abs(clock.instant().getEpochSecond() - epochSeconds) > properties.getClockSkewSeconds()) {
			return InternalHmacVerificationResult.rejected("timestamp_out_of_range");
		}

		if (!MessageDigest.isEqual(bodyHash.getBytes(StandardCharsets.US_ASCII),
				Hex.sha256(request.body()).getBytes(StandardCharsets.US_ASCII))) {
			return InternalHmacVerificationResult.rejected("body_hash_mismatch");
		}

		byte[] secret = secretForKey(keyId);
		if (secret == null) {
			return InternalHmacVerificationResult.rejected("unknown_key");
		}

		String canonical = canonicalString(service, keyId, timestamp, requestId, request.method(), request.rawPath(),
				request.rawQuery(), bodyHash);
		String expectedSignature = hmacSha256Hex(secret, canonical);
		if (!MessageDigest.isEqual(signature.getBytes(StandardCharsets.US_ASCII),
				expectedSignature.getBytes(StandardCharsets.US_ASCII))) {
			return InternalHmacVerificationResult.rejected("signature_mismatch");
		}

		String replayKey = service + '\n' + keyId + '\n' + requestId;
		InternalHmacReplayCache.MarkResult replayResult = replayCache.markIfAbsent(replayKey);
		if (replayResult == InternalHmacReplayCache.MarkResult.REPLAYED) {
			return InternalHmacVerificationResult.rejected("replay");
		}
		if (replayResult == InternalHmacReplayCache.MarkResult.CAPACITY_EXCEEDED) {
			return InternalHmacVerificationResult.rejected("replay_cache_full");
		}
		return InternalHmacVerificationResult.acceptedResult();
	}

	private byte[] secretForKey(String keyId) {
		if (properties.getCurrentKeyId().equals(keyId)) {
			return properties.currentSecret();
		}
		if (properties.getPreviousKeyId() != null && properties.getPreviousKeyId().equals(keyId)) {
			return properties.previousSecret();
		}
		return null;
	}

	private boolean isCanonicalUuid(String value) {
		try {
			return UUID.fromString(value).toString().equals(value);
		}
		catch (IllegalArgumentException ex) {
			return false;
		}
	}

	private String canonicalString(String service, String keyId, String timestamp, String requestId, String method,
			String rawPath, String rawQuery, String bodyHash) {
		return String.join("\n",
				"v1",
				service,
				keyId,
				timestamp,
				requestId,
				method.toUpperCase(Locale.ROOT),
				rawPath,
				rawQuery == null ? "" : rawQuery,
				bodyHash);
	}

	private String hmacSha256Hex(byte[] secret, String canonical) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret, "HmacSHA256"));
			return Hex.lower(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception ex) {
			throw new IllegalStateException("HmacSHA256 is not available", ex);
		}
	}

}
