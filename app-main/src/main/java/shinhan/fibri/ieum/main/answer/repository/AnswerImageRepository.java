package shinhan.fibri.ieum.main.answer.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import shinhan.fibri.ieum.main.answer.domain.AnswerImage;

public interface AnswerImageRepository extends JpaRepository<AnswerImage, Long> {

	@Query(value = "SELECT EXISTS (SELECT 1 FROM answer_images WHERE file_id = :fileId)", nativeQuery = true)
	boolean existsByFileId(@Param("fileId") UUID fileId);
}
