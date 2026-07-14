package shinhan.fibri.ieum.main.admin.report.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import shinhan.fibri.ieum.main.report.domain.ReportAiReviewState;

public record AdminReportAiDetail(
	ReportAiReviewState reviewState,
	String recommendation,
	String reason,
	BigDecimal confidence,
	String modelVersion,
	String policyVersion,
	OffsetDateTime reviewedAt,
	AdminReportDecision decision,
	String policySetHash,
	JsonNode result,
	String lastErrorCode
) {
}
