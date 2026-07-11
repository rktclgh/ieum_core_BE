package shinhan.fibri.ieum.main.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InternalRequestSignerTest {

	@Test
	void signsTheExactHmacV1CanonicalRequest() {
		InternalRequestSigner signer = new InternalRequestSigner(
			"app-main",
			"main-202607",
			Base64.getEncoder().encodeToString("test-secret-for-hmac-v1-32-bytes!!".getBytes(StandardCharsets.UTF_8)),
			Clock.fixed(Instant.ofEpochSecond(1_784_000_000L), ZoneOffset.UTC)
		);

		SignedInternalRequest signed = signer.sign(
			"post",
			"/ai/v1/internal/reports/900/review",
			"trace=abc%20def&x=1",
			"{\"reportId\":900,\"decision\":\"normal\"}".getBytes(StandardCharsets.UTF_8),
			UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
		);

		assertThat(signed.service()).isEqualTo("app-main");
		assertThat(signed.keyId()).isEqualTo("main-202607");
		assertThat(signed.timestamp()).isEqualTo(1_784_000_000L);
		assertThat(signed.requestId()).isEqualTo("123e4567-e89b-12d3-a456-426614174000");
		assertThat(signed.bodyHash()).isEqualTo("708ab878bf3a15dae379a8a14681a1ffcd26797a23dd24054a1705c3f6cd91bb");
		assertThat(signed.signature()).isEqualTo("341dd6d775aedf8ea9c83eb3d0dd72043ed31398352509372c731848e0958a2e");
	}
}
