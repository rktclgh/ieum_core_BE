package shinhan.fibri.ieum.main.admin.inquiry.repository;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import shinhan.fibri.ieum.main.admin.inquiry.dto.AdminInquiryItem;
import shinhan.fibri.ieum.main.inquiry.domain.Inquiry;
import shinhan.fibri.ieum.main.inquiry.domain.InquiryStatus;

public interface AdminInquiryQueryRepository extends Repository<Inquiry, Long> {

	default List<AdminInquiryItem> findAdminItems(InquiryStatus status) {
		if (status == null) {
			return findAllAdminItems();
		}
		return findAdminItemsByStatus(status);
	}

	@Query("""
		select new shinhan.fibri.ieum.main.admin.inquiry.dto.AdminInquiryItem(
			i.id, i.userId, u.email, i.title, i.content, i.status,
			i.answer, i.answeredBy, i.answeredAt, i.createdAt)
		from Inquiry i join User u on u.id = i.userId
		where i.status = :status
		order by i.createdAt desc, i.id desc
		""")
	List<AdminInquiryItem> findAdminItemsByStatus(@Param("status") InquiryStatus status);

	@Query("""
		select new shinhan.fibri.ieum.main.admin.inquiry.dto.AdminInquiryItem(
			i.id, i.userId, u.email, i.title, i.content, i.status,
			i.answer, i.answeredBy, i.answeredAt, i.createdAt)
		from Inquiry i join User u on u.id = i.userId
		order by i.createdAt desc, i.id desc
		""")
	List<AdminInquiryItem> findAllAdminItems();
}
