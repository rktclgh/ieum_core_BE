package shinhan.fibri.ieum.main.admin.inquiry.repository;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import shinhan.fibri.ieum.main.admin.inquiry.dto.AdminInquiryItem;
import shinhan.fibri.ieum.main.inquiry.domain.Inquiry;
import shinhan.fibri.ieum.main.inquiry.domain.InquiryStatus;

public interface AdminInquiryQueryRepository extends Repository<Inquiry, Long> {

	@Query("""
		select new shinhan.fibri.ieum.main.admin.inquiry.dto.AdminInquiryItem(
			i.id, i.userId, u.email, i.title, i.content, i.status,
			i.answer, i.answeredBy, i.answeredAt, i.createdAt)
		from Inquiry i join User u on u.id = i.userId
		where (:status is null or i.status = :status)
		order by i.createdAt desc, i.id desc
		""")
	List<AdminInquiryItem> findAdminItems(@Param("status") InquiryStatus status);
}
