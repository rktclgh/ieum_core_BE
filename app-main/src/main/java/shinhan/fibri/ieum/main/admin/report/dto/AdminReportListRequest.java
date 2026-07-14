package shinhan.fibri.ieum.main.admin.report.dto;

import shinhan.fibri.ieum.main.report.domain.ReportAiReviewState;
import shinhan.fibri.ieum.main.report.domain.ReportStatus;

public record AdminReportListRequest(
	ReportStatus status,
	ReportAiReviewState aiReviewState,
	AdminReportDecision decision,
	String cursor,
	Integer size
) {
}
