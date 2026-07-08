package shinhan.fibri.ieum.main.answer.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import shinhan.fibri.ieum.main.answer.domain.AnswerImage;

public interface AnswerImageRepository extends JpaRepository<AnswerImage, Long> {

	boolean existsByFileId(UUID fileId);
}
