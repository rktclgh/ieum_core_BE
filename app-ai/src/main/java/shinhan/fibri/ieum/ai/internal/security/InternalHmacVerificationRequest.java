package shinhan.fibri.ieum.ai.internal.security;

public record InternalHmacVerificationRequest(
	String method,
	String rawPath,
	String rawQuery,
	byte[] body,
	InternalHmacHeaders headers
) {
}
