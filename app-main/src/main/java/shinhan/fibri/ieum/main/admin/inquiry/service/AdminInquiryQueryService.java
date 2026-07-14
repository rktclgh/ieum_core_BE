package shinhan.fibri.ieum.main.admin.inquiry.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shinhan.fibri.ieum.main.admin.inquiry.dto.InquiryAdminListResponse;
import shinhan.fibri.ieum.main.admin.inquiry.repository.AdminInquiryQueryRepository;
import shinhan.fibri.ieum.main.inquiry.domain.InquiryStatus;

@Service
public class AdminInquiryQueryService {

	private final AdminInquiryQueryRepository repository;

	public AdminInquiryQueryService(AdminInquiryQueryRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public InquiryAdminListResponse list(InquiryStatus status) {
		return new InquiryAdminListResponse(repository.findAdminItems(status));
	}
}
