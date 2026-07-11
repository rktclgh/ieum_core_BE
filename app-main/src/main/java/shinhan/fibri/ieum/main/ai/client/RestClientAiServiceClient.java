package shinhan.fibri.ieum.main.ai.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public class RestClientAiServiceClient implements AiServiceClient {

	private final RestClient restClient;
	private final ObjectMapper objectMapper;
	private final InternalRequestSigner signer;
	private final Supplier<UUID> requestIdSupplier;

	public RestClientAiServiceClient(
		RestClient restClient,
		ObjectMapper objectMapper,
		InternalRequestSigner signer,
		Supplier<UUID> requestIdSupplier
	) {
		this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
		this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
		this.signer = Objects.requireNonNull(signer, "signer must not be null");
		this.requestIdSupplier = Objects.requireNonNull(requestIdSupplier, "requestIdSupplier must not be null");
	}

	@Override
	public ReportReviewResponse review(ReportReviewRequest request) {
		Objects.requireNonNull(request, "request must not be null");
		String path = "/ai/v1/internal/reports/" + request.reportId() + "/review";
		try {
			byte[] body = objectMapper.writeValueAsBytes(request);
			SignedInternalRequest signed = signer.sign("POST", path, "", body, requestIdSupplier.get());
			return restClient.post()
				.uri(path)
				.contentType(MediaType.APPLICATION_JSON)
				.headers(headers -> {
					headers.set("X-Internal-Service", signed.service());
					headers.set("X-Internal-Key-Id", signed.keyId());
					headers.set("X-Internal-Timestamp", Long.toString(signed.timestamp()));
					headers.set("X-Internal-Request-Id", signed.requestId());
					headers.set("X-Internal-Body-SHA256", signed.bodyHash());
					headers.set("X-Internal-Signature", signed.signature());
				})
				.body(body)
				.retrieve()
				.body(ReportReviewResponse.class);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Failed to serialize report review request", exception);
		}
	}
}
