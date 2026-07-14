package shinhan.fibri.ieum.main.admin.inquiry.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import shinhan.fibri.ieum.main.admin.inquiry.dto.AdminInquiryItem;
import shinhan.fibri.ieum.main.admin.inquiry.dto.InquiryAdminListResponse;
import shinhan.fibri.ieum.main.admin.inquiry.repository.AdminInquiryQueryRepository;
import shinhan.fibri.ieum.main.inquiry.domain.InquiryStatus;

class AdminInquiryQueryServiceTest {

	private final AdminInquiryQueryRepository repository = mock(AdminInquiryQueryRepository.class);
	private final AdminInquiryQueryService service = new AdminInquiryQueryService(repository);

	@Test
	void returnsRepositoryItemsAsListResponse() {
		AdminInquiryItem item = new AdminInquiryItem(
			90L,
			42L,
			"user@example.com",
			"문의 제목",
			"문의 내용",
			InquiryStatus.pending,
			null,
			null,
			null,
			OffsetDateTime.parse("2026-07-13T10:00:00+09:00")
		);
		when(repository.findAdminItems(InquiryStatus.pending)).thenReturn(List.of(item));

		InquiryAdminListResponse response = service.list(InquiryStatus.pending);

		assertThat(response.items()).containsExactly(item);
		verify(repository).findAdminItems(InquiryStatus.pending);
	}
}
