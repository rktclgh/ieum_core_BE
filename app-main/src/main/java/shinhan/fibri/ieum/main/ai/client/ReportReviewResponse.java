package shinhan.fibri.ieum.main.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;

public record ReportReviewResponse(
	String decision,
	String category,
	String severity,
	BigDecimal confidence,
	String reason,
	JsonNode evidence,
	JsonNode matchedRules,
	String policySetHash,
	JsonNode policySnapshot,
	String modelVersion,
	String promptVersion,
	Boolean fallbackUsed,
	JsonNode providerAttempts
) {
}
