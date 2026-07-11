package shinhan.fibri.ieum.ai.internal.security;

public record InternalHmacVerificationResult(boolean accepted, String failureReason) {

	public static InternalHmacVerificationResult acceptedResult() {
		return new InternalHmacVerificationResult(true, null);
	}

	public static InternalHmacVerificationResult rejected(String failureReason) {
		return new InternalHmacVerificationResult(false, failureReason);
	}

}
