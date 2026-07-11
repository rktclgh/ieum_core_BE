package shinhan.fibri.ieum.main.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InternalRequestSignerTest {

	@Test
	void signsTheExactHmacV1CanonicalRequest() {
		InternalRequestSigner signer = new InternalRequestSigner(
			"ieum-main",
			"main-202607",
			"test-secret-for-hmac-v1-32-bytes!!",
			Clock.fixed(Instant.ofEpochSecond(1_784_000_000L), ZoneOffset.UTC)
		);

		SignedInternalRequest signed = signer.sign(
			"post",
			"/ai/v1/internal/reports/900/review",
			"trace=abc%20def&x=1",
			"{\"reportId\":900,\"decision\":\"normal\"}".getBytes(StandardCharsets.UTF_8),
			UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
		);

		assertThat(signed.service()).isEqualTo("ieum-main");
		assertThat(signed.keyId()).isEqualTo("main-202607");
		assertThat(signed.timestamp()).isEqualTo(1_784_000_000L);
		assertThat(signed.requestId()).isEqualTo("123e4567-e89b-12d3-a456-426614174000");
		assertThat(signed.bodyHash()).isEqualTo("708ab878bf3a15dae379a8a14681a1ffcd26797a23dd24054a1705c3f6cd91bb");
		assertThat(signed.signature()).isEqualTo("8739d3500e17b9c3cf444b3afba73badc6079553dc852d98c9ab42df9af4062b");
	}
}
