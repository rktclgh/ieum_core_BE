package shinhan.fibri.ieum.main.admin.inquiry.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import shinhan.fibri.ieum.common.auth.principal.AuthenticatedUser;
import shinhan.fibri.ieum.main.admin.inquiry.dto.AnswerInquiryRequest;
import shinhan.fibri.ieum.main.admin.inquiry.dto.InquiryAdminListResponse;
import shinhan.fibri.ieum.main.admin.inquiry.service.AdminInquiryAnswerService;
import shinhan.fibri.ieum.main.admin.inquiry.service.AdminInquiryQueryService;
import shinhan.fibri.ieum.main.inquiry.domain.InquiryStatus;

@RestController
@RequestMapping("/api/v1/admin/inquiries")
public class AdminInquiryController {

	private final AdminInquiryQueryService queryService;
	private final AdminInquiryAnswerService answerService;

	public AdminInquiryController(
		AdminInquiryQueryService queryService,
		AdminInquiryAnswerService answerService
	) {
		this.queryService = queryService;
		this.answerService = answerService;
	}

	@GetMapping
	public ResponseEntity<InquiryAdminListResponse> list(
		@RequestParam(required = false) InquiryStatus status
	) {
		return ResponseEntity.ok(queryService.list(status));
	}

	@PostMapping("/{inquiryId}/answer")
	public ResponseEntity<Void> answer(
		@AuthenticationPrincipal AuthenticatedUser principal,
		@PathVariable Long inquiryId,
		@Valid @RequestBody AnswerInquiryRequest request
	) {
		answerService.answer(principal, inquiryId, request);
		return ResponseEntity.ok().build();
	}
}
