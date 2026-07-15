package shinhan.fibri.ieum.common.chat.repository;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import shinhan.fibri.ieum.common.chat.domain.Message;

public interface MessageRepository extends JpaRepository<Message, Long> {

	@Query("SELECT COALESCE(MAX(message.id), 0) FROM Message message WHERE message.room.id = :roomId")
	long findMaxMessageIdByRoomId(@Param("roomId") Long roomId);

	@Query("""
		SELECT message
		FROM Message message
		JOIN FETCH message.sender
		JOIN ChatMember member ON member.room = message.room AND member.user.id = :userId
		WHERE message.room.id = :roomId
		  AND member.leftAt IS NULL
		  AND message.id > member.visibleAfterMessageId
		  AND message.deletedAt IS NULL
		ORDER BY message.createdAt DESC, message.id DESC
		""")
	List<Message> findLatestVisibleMessages(
		@Param("roomId") Long roomId,
		@Param("userId") Long userId,
		Pageable pageable
	);

	@Query("""
		SELECT message
		FROM Message message
		JOIN FETCH message.sender
		JOIN ChatMember member ON member.room = message.room AND member.user.id = :userId
		WHERE message.room.id = :roomId
		  AND member.leftAt IS NULL
		  AND message.id > member.visibleAfterMessageId
		  AND message.deletedAt IS NULL
		  AND (
			message.createdAt < :cursorCreatedAt
			OR (message.createdAt = :cursorCreatedAt AND message.id < :cursorMessageId)
		  )
		ORDER BY message.createdAt DESC, message.id DESC
		""")
	List<Message> findVisibleMessagesBeforeCursor(
		@Param("roomId") Long roomId,
		@Param("userId") Long userId,
		@Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
		@Param("cursorMessageId") Long cursorMessageId,
		Pageable pageable
	);

	@Query("""
		SELECT message.room.id AS roomId, COUNT(message) AS unreadCount
		FROM Message message
		JOIN ChatMember member ON member.room = message.room AND member.user.id = :userId
		WHERE message.room.id IN :roomIds
		  AND member.leftAt IS NULL
		  AND message.id > member.visibleAfterMessageId
		  AND message.deletedAt IS NULL
		  AND message.sender.id <> :userId
		  AND (member.lastReadAt IS NULL OR message.createdAt > member.lastReadAt)
		GROUP BY message.room.id
		""")
	List<RoomUnreadCount> countUnreadByRoomIds(
		@Param("userId") Long userId,
		@Param("roomIds") List<Long> roomIds
	);

	@Query("""
		SELECT message
		FROM Message message
		JOIN FETCH message.sender
		JOIN ChatMember member ON member.room = message.room AND member.user.id = :userId
		WHERE message.room.id IN :roomIds
		  AND member.leftAt IS NULL
		  AND message.id > member.visibleAfterMessageId
		  AND message.deletedAt IS NULL
		  AND message.id = (
			SELECT MAX(latest.id)
			FROM Message latest
			WHERE latest.room = message.room
			  AND latest.deletedAt IS NULL
			  AND latest.id > member.visibleAfterMessageId
		)
		ORDER BY message.createdAt DESC, message.id DESC
		""")
	List<Message> findLastVisibleMessagesByRoomIds(
		@Param("userId") Long userId,
		@Param("roomIds") List<Long> roomIds
	);

	@Query("""
		SELECT message
		FROM Message message
		JOIN FETCH message.sender
		WHERE message.room.id = :roomId
		  AND message.deletedAt IS NULL
		  AND (
			message.createdAt < :reportedCreatedAt
			OR (message.createdAt = :reportedCreatedAt AND message.id < :reportedMessageId)
		  )
		ORDER BY message.createdAt DESC, message.id DESC
		""")
	List<Message> findContextBeforeMessage(
		@Param("roomId") Long roomId,
		@Param("reportedCreatedAt") OffsetDateTime reportedCreatedAt,
		@Param("reportedMessageId") Long reportedMessageId,
		Pageable pageable
	);

	@Query("""
		SELECT message
		FROM Message message
		JOIN FETCH message.sender
		WHERE message.room.id = :roomId
		  AND message.deletedAt IS NULL
		  AND (
			message.createdAt > :reportedCreatedAt
			OR (message.createdAt = :reportedCreatedAt AND message.id > :reportedMessageId)
		  )
		ORDER BY message.createdAt ASC, message.id ASC
		""")
	List<Message> findContextAfterMessage(
		@Param("roomId") Long roomId,
		@Param("reportedCreatedAt") OffsetDateTime reportedCreatedAt,
		@Param("reportedMessageId") Long reportedMessageId,
		Pageable pageable
	);

	interface RoomUnreadCount {
		Long getRoomId();

		Long getUnreadCount();
	}
}
