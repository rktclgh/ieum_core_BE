package shinhan.fibri.ieum.main.ai.client;

public record SignedInternalRequest(
	String service,
	String keyId,
	long timestamp,
	String requestId,
	String bodyHash,
	String signature
) {
}
