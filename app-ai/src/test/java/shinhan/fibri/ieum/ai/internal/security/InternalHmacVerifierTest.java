package shinhan.fibri.ieum.ai.internal.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InternalHmacVerifierTest {

	private static final byte[] CURRENT_SECRET = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
	private static final byte[] PREVIOUS_SECRET = "abcdef0123456789abcdef0123456789".getBytes(StandardCharsets.UTF_8);
	private static final Instant NOW = Instant.ofEpochSecond(1_725_000_000L);

	private InternalHmacVerifier verifier;
	private LinkedHashMap<String, String> headers;

	@BeforeEach
	void setUp() {
		InternalHmacProperties properties = new InternalHmacProperties();
		properties.setServiceName("app-main");
		properties.setCurrentKeyId("current-key");
		properties.setCurrentSecretBase64(Base64.getEncoder().encodeToString(CURRENT_SECRET));
		properties.setPreviousKeyId("previous-key");
		properties.setPreviousSecretBase64(Base64.getEncoder().encodeToString(PREVIOUS_SECRET));
		properties.setReplayMaxEntries(64);
		properties.setReplayTtlSeconds(120);
		properties.afterPropertiesSet();

		verifier = new InternalHmacVerifier(properties, Clock.fixed(NOW, ZoneOffset.UTC));
		headers = validHeaders("current-key", CURRENT_SECRET, "POST", "/ai/v1/reviews", "b=2&a=1",
				"{\"ok\":true}".getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void acceptsValidCurrentKeySignature() {
		InternalHmacVerificationResult result = verifier.verify(request("POST", "/ai/v1/reviews", "b=2&a=1",
				"{\"ok\":true}".getBytes(StandardCharsets.UTF_8), headers));

		assertThat(result.accepted()).isTrue();
		assertThat(result.failureReason()).isNull();
	}

	@Test
	void acceptsPreviousKeySignature() {
		byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
		LinkedHashMap<String, String> previousHeaders = validHeaders("previous-key", PREVIOUS_SECRET, "POST",
				"/ai/v1/reviews", "", body);

		InternalHmacVerificationResult result = verifier.verify(request("POST", "/ai/v1/reviews", "", body,
				previousHeaders));

		assertThat(result.accepted()).isTrue();
	}

	@Test
	void rejectsBodyHashThatDoesNotMatchRawBytes() {
		headers.put("X-Internal-Body-SHA256", sha256Hex("different".getBytes(StandardCharsets.UTF_8)));

		InternalHmacVerificationResult result = verifier.verify(request("POST", "/ai/v1/reviews", "b=2&a=1",
				"{\"ok\":true}".getBytes(StandardCharsets.UTF_8), headers));

		assertThat(result.accepted()).isFalse();
		assertThat(result.failureReason()).isEqualTo("body_hash_mismatch");
	}

	@Test
	void rejectsMalformedCanonicalRequestId() {
		headers.put("X-Internal-Request-Id", UUID.randomUUID().toString().toUpperCase());

		InternalHmacVerificationResult result = verifier.verify(request("POST", "/ai/v1/reviews", "b=2&a=1",
				"{\"ok\":true}".getBytes(StandardCharsets.UTF_8), headers));

		assertThat(result.accepted()).isFalse();
		assertThat(result.failureReason()).isEqualTo("malformed_header");
	}

	@Test
	void rejectsTimestampOutsideClockSkew() {
		headers.put("X-Internal-Timestamp", Long.toString(NOW.getEpochSecond() - 61));
		resign(headers, CURRENT_SECRET, "POST", "/ai/v1/reviews", "b=2&a=1");

		InternalHmacVerificationResult result = verifier.verify(request("POST", "/ai/v1/reviews", "b=2&a=1",
				"{\"ok\":true}".getBytes(StandardCharsets.UTF_8), headers));

		assertThat(result.accepted()).isFalse();
		assertThat(result.failureReason()).isEqualTo("timestamp_out_of_range");
	}

	@Test
	void rejectsUnknownKeyId() {
		headers.put("X-Internal-Key-Id", "unknown-key");
		resign(headers, CURRENT_SECRET, "POST", "/ai/v1/reviews", "b=2&a=1");

		InternalHmacVerificationResult result = verifier.verify(request("POST", "/ai/v1/reviews", "b=2&a=1",
				"{\"ok\":true}".getBytes(StandardCharsets.UTF_8), headers));

		assertThat(result.accepted()).isFalse();
		assertThat(result.failureReason()).isEqualTo("unknown_key");
	}

	@Test
	void rejectsReplayForSameServiceKeyAndRequestId() {
		InternalHmacVerificationRequest request = request("POST", "/ai/v1/reviews", "b=2&a=1",
				"{\"ok\":true}".getBytes(StandardCharsets.UTF_8), headers);

		assertThat(verifier.verify(request).accepted()).isTrue();
		InternalHmacVerificationResult replay = verifier.verify(request);

		assertThat(replay.accepted()).isFalse();
		assertThat(replay.failureReason()).isEqualTo("replay");
	}

	@Test
	void rejectsNewRequestsWhenReplayCacheIsFullWithoutEvictingAnAcceptedRequest() {
		InternalHmacProperties properties = validProperties();
		properties.setReplayMaxEntries(1);
		properties.afterPropertiesSet();
		InternalHmacVerifier limitedVerifier = new InternalHmacVerifier(properties, Clock.fixed(NOW, ZoneOffset.UTC));
		byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
		LinkedHashMap<String, String> firstHeaders = validHeaders("current-key", CURRENT_SECRET, "POST",
				"/ai/v1/reviews", "", body);
		LinkedHashMap<String, String> secondHeaders = validHeaders("current-key", CURRENT_SECRET, "POST",
				"/ai/v1/reviews", "", body);
		InternalHmacVerificationRequest first = request("POST", "/ai/v1/reviews", "", body, firstHeaders);

		assertThat(limitedVerifier.verify(first).accepted()).isTrue();
		InternalHmacVerificationResult capacityRejection = limitedVerifier.verify(
				request("POST", "/ai/v1/reviews", "", body, secondHeaders));

		assertThat(capacityRejection.accepted()).isFalse();
		assertThat(capacityRejection.failureReason()).isEqualTo("replay_cache_full");
		assertThat(limitedVerifier.verify(first).failureReason()).isEqualTo("replay");
	}

	@Test
	void acceptsTheDocumentedAppMainBase64SigningVector() {
		byte[] body = "{\"reportId\":900,\"decision\":\"normal\"}".getBytes(StandardCharsets.UTF_8);
		InternalHmacProperties properties = new InternalHmacProperties();
		properties.setServiceName("app-main");
		properties.setCurrentKeyId("main-202607");
		properties.setCurrentSecretBase64(Base64.getEncoder()
			.encodeToString("test-secret-for-hmac-v1-32-bytes!!".getBytes(StandardCharsets.UTF_8)));
		properties.afterPropertiesSet();
		InternalHmacVerifier vectorVerifier = new InternalHmacVerifier(properties,
				Clock.fixed(Instant.ofEpochSecond(1_784_000_000L), ZoneOffset.UTC));
		InternalHmacHeaders vectorHeaders = new InternalHmacHeaders();
		vectorHeaders.add("X-Internal-Service", "app-main");
		vectorHeaders.add("X-Internal-Key-Id", "main-202607");
		vectorHeaders.add("X-Internal-Timestamp", "1784000000");
		vectorHeaders.add("X-Internal-Request-Id", "123e4567-e89b-12d3-a456-426614174000");
		vectorHeaders.add("X-Internal-Body-SHA256", "708ab878bf3a15dae379a8a14681a1ffcd26797a23dd24054a1705c3f6cd91bb");
		vectorHeaders.add("X-Internal-Signature", "341dd6d775aedf8ea9c83eb3d0dd72043ed31398352509372c731848e0958a2e");

		InternalHmacVerificationResult result = vectorVerifier.verify(new InternalHmacVerificationRequest(
				"POST", "/ai/v1/internal/reports/900/review", "trace=abc%20def&x=1", body, vectorHeaders));

		assertThat(result.accepted()).isTrue();
	}

	@Test
	void failsFastWhenCurrentSecretIsBlank() {
		InternalHmacProperties properties = new InternalHmacProperties();
		properties.setServiceName("app-main");
		properties.setCurrentKeyId("current-key");
		properties.setCurrentSecretBase64(" ");

		assertThatThrownBy(properties::afterPropertiesSet)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("current-secret-base64");
	}

	@Test
	void failsFastWhenCurrentSecretIsShorterThan32Bytes() {
		InternalHmacProperties properties = new InternalHmacProperties();
		properties.setServiceName("app-main");
		properties.setCurrentKeyId("current-key");
		properties.setCurrentSecretBase64(Base64.getEncoder().encodeToString(new byte[31]));

		assertThatThrownBy(properties::afterPropertiesSet)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("current-secret-base64")
			.hasMessageContaining("at least 32 bytes");
	}

	@Test
	void failsFastWhenClockSkewExceeds60Seconds() {
		InternalHmacProperties properties = validProperties();
		properties.setClockSkewSeconds(61);

		assertThatThrownBy(properties::afterPropertiesSet)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("clock-skew-seconds")
			.hasMessageContaining("60");
	}

	@Test
	void failsFastWhenMaximumBodySizeExceedsOneMiB() {
		InternalHmacProperties properties = validProperties();
		properties.setMaxBodyBytes(1_048_577);

		assertThatThrownBy(properties::afterPropertiesSet)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("max-body-bytes")
			.hasMessageContaining("1048576");
	}

	@Test
	void failsFastWhenReplayCacheTtlCannotCoverTheTimestampAcceptanceWindow() {
		InternalHmacProperties properties = validProperties();
		properties.setReplayTtlSeconds(119);

		assertThatThrownBy(properties::afterPropertiesSet)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("replay-ttl-seconds")
			.hasMessageContaining("at least 120");
	}

	@Test
	void failsFastWhenConfiguredServiceNameContainsControls() {
		InternalHmacProperties properties = validProperties();
		properties.setServiceName("app-main\nspoofed");

		assertThatThrownBy(properties::afterPropertiesSet)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("service-name");
	}

	@Test
	void failsFastWhenConfiguredCurrentKeyIdContainsControls() {
		InternalHmacProperties properties = validProperties();
		properties.setCurrentKeyId("current-key\rspoofed");

		assertThatThrownBy(properties::afterPropertiesSet)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("current-key-id");
	}

	@Test
	void failsFastWhenConfiguredPreviousKeyIdContainsControls() {
		InternalHmacProperties properties = validProperties();
		properties.setPreviousKeyId("previous-key\tspoofed");
		properties.setPreviousSecretBase64(Base64.getEncoder().encodeToString(PREVIOUS_SECRET));

		assertThatThrownBy(properties::afterPropertiesSet)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("previous-key-id");
	}

	private LinkedHashMap<String, String> validHeaders(String keyId, byte[] secret, String method, String rawPath,
			String rawQuery, byte[] body) {
		LinkedHashMap<String, String> values = new LinkedHashMap<>();
		values.put("X-Internal-Service", "app-main");
		values.put("X-Internal-Key-Id", keyId);
		values.put("X-Internal-Timestamp", Long.toString(NOW.getEpochSecond()));
		values.put("X-Internal-Request-Id", UUID.randomUUID().toString());
		values.put("X-Internal-Body-SHA256", sha256Hex(body));
		resign(values, secret, method, rawPath, rawQuery);
		return values;
	}

	private InternalHmacProperties validProperties() {
		InternalHmacProperties properties = new InternalHmacProperties();
		properties.setServiceName("app-main");
		properties.setCurrentKeyId("current-key");
		properties.setCurrentSecretBase64(Base64.getEncoder().encodeToString(CURRENT_SECRET));
		return properties;
	}

	private void resign(Map<String, String> values, byte[] secret, String method, String rawPath, String rawQuery) {
		String canonical = String.join("\n",
				"v1",
				values.get("X-Internal-Service"),
				values.get("X-Internal-Key-Id"),
				values.get("X-Internal-Timestamp"),
				values.get("X-Internal-Request-Id"),
				method.toUpperCase(),
				rawPath,
				rawQuery,
				values.get("X-Internal-Body-SHA256"));
		values.put("X-Internal-Signature", hmacSha256Hex(secret, canonical));
	}

	private InternalHmacVerificationRequest request(String method, String rawPath, String rawQuery, byte[] body,
			Map<String, String> values) {
		InternalHmacHeaders requestHeaders = new InternalHmacHeaders();
		values.forEach((name, value) -> requestHeaders.add(name, value));
		return new InternalHmacVerificationRequest(method, rawPath, rawQuery, body, requestHeaders);
	}

	private String sha256Hex(byte[] bytes) {
		return Hex.sha256(bytes);
	}

	private String hmacSha256Hex(byte[] secret, String canonical) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret, "HmacSHA256"));
			return Hex.lower(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

}
