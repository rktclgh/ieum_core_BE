package shinhan.fibri.ieum.main.notification.repository;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import shinhan.fibri.ieum.main.notification.domain.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	List<Notification> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

	@Query("""
		SELECT COUNT(n)
		FROM Notification n
		WHERE n.userId = :userId AND n.read = false
		""")
	long countUnreadByUserId(@Param("userId") Long userId);

	@Query("""
		SELECT n
		FROM Notification n
		WHERE n.userId = :userId
		  AND (n.createdAt < :cursorCreatedAt OR (n.createdAt = :cursorCreatedAt AND n.id < :cursorId))
		ORDER BY n.createdAt DESC, n.id DESC
		""")
	List<Notification> findPage(
		@Param("userId") Long userId,
		@Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
		@Param("cursorId") Long cursorId,
		Pageable pageable
	);
}
