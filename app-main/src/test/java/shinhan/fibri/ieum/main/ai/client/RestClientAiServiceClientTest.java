package shinhan.fibri.ieum.main.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestClientAiServiceClientTest {

	@Test
	void postsTheRawSignedReviewRequestToTheInternalEndpoint() {
		RestClient.Builder builder = RestClient.builder().baseUrl("https://ai.example.test");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		InternalRequestSigner signer = new InternalRequestSigner(
			"ieum-main",
			"main-202607",
			"test-secret-for-hmac-v1-32-bytes!!",
			Clock.fixed(Instant.ofEpochSecond(1_784_000_000L), ZoneOffset.UTC)
		);
		RestClientAiServiceClient client = new RestClientAiServiceClient(
			builder.build(),
			new ObjectMapper().findAndRegisterModules(),
			signer,
			() -> UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
		);
		ReportReviewRequest request = new ReportReviewRequest(
			900L,
			UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
			500L,
			"harassment",
			"detail",
			"a".repeat(64),
			List.of(new ReportReviewMessage(500L, "reported_user", "abuse", null, "2026-07-11T00:00:00Z"))
		);

		server.expect(requestTo(URI.create("https://ai.example.test/ai/v1/internal/reports/900/review")))
			.andExpect(method(POST))
			.andExpect(header("X-Internal-Service", "ieum-main"))
			.andExpect(header("X-Internal-Key-Id", "main-202607"))
			.andExpect(header("X-Internal-Timestamp", "1784000000"))
			.andExpect(header("X-Internal-Request-Id", "123e4567-e89b-12d3-a456-426614174000"))
			.andExpect(header("X-Internal-Body-SHA256", org.hamcrest.Matchers.matchesPattern("[0-9a-f]{64}")))
			.andExpect(header("X-Internal-Signature", org.hamcrest.Matchers.matchesPattern("[0-9a-f]{64}")))
			.andRespond(withSuccess("{\"decision\":\"normal\",\"confidence\":0.42}", MediaType.APPLICATION_JSON));

		ReportReviewResponse response = client.review(request);

		assertThat(response.decision()).isEqualTo("normal");
		assertThat(response.confidence()).isEqualByComparingTo("0.42");
		server.verify();
	}
}
