package shinhan.fibri.ieum.ai.report.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shinhan.fibri.ieum.ai.report.support.ReportReviewObservationLogger;
import shinhan.fibri.ieum.ai.report.service.PreparedReportReview;
import shinhan.fibri.ieum.ai.report.service.ReportReviewInferenceOrchestrator;
import shinhan.fibri.ieum.ai.report.service.ReportReviewPreparationService;
import shinhan.fibri.ieum.common.ai.report.dto.ReportReviewRequest;
import shinhan.fibri.ieum.common.ai.report.dto.ReportReviewResponse;

@RestController
@RequestMapping("/ai/v1/internal/reports")
@ConditionalOnProperty(prefix = "app.ai.features", name = "report-review-enabled", havingValue = "true")
public class ReportReviewInternalController {

	private final ReportReviewPreparationService preparationService;
	private final ReportReviewInferenceOrchestrator inferenceOrchestrator;
	private final ReportReviewObservationLogger observationLogger;

	public ReportReviewInternalController(
		ReportReviewPreparationService preparationService,
		ReportReviewInferenceOrchestrator inferenceOrchestrator,
		ReportReviewObservationLogger observationLogger
	) {
		this.preparationService = preparationService;
		this.inferenceOrchestrator = inferenceOrchestrator;
		this.observationLogger = observationLogger;
	}

	@PostMapping("/{reportId}/review")
	public ReportReviewResponse review(
		@PathVariable long reportId,
		@RequestBody ReportReviewRequest request,
		HttpServletRequest servletRequest
	) {
		observationLogger.started(servletRequest, reportId, request);
		PreparedReportReview preparedReview = preparationService.prepare(reportId, request);
		ReportReviewResponse response = inferenceOrchestrator.review(preparedReview);
		observationLogger.completed(servletRequest, reportId, request, response);
		return response;
	}
}
