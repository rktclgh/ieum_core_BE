package shinhan.fibri.ieum.main.inquiry.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import shinhan.fibri.ieum.main.inquiry.domain.Inquiry;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

	List<Inquiry> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);
}
