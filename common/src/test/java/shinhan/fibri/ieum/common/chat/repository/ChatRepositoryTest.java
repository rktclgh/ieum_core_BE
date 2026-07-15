package shinhan.fibri.ieum.common.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import shinhan.fibri.ieum.common.auth.domain.GenderType;
import shinhan.fibri.ieum.common.auth.domain.User;
import shinhan.fibri.ieum.common.chat.domain.ChatMember;
import shinhan.fibri.ieum.common.chat.domain.ChatRoom;
import shinhan.fibri.ieum.common.chat.domain.Message;
import shinhan.fibri.ieum.common.chat.domain.RoomType;
import shinhan.fibri.ieum.testsupport.CanonicalPostgresDatabase;
import shinhan.fibri.ieum.testsupport.CanonicalPostgresContainer;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ChatRepositoryTest {

	@DynamicPropertySource
	static void configureDataSource(DynamicPropertyRegistry registry) {
		String databaseName = "common_chat_repository";
		CanonicalPostgresDatabase.recreateWithSchema(databaseName);
		registry.add("spring.datasource.url", () -> CanonicalPostgresContainer.jdbcUrl(databaseName));
		registry.add("spring.datasource.username", CanonicalPostgresContainer::username);
		registry.add("spring.datasource.password", CanonicalPostgresContainer::password);
		registry.add("spring.datasource.driver-class-name", CanonicalPostgresContainer::driverClassName);
		registry.add("spring.sql.init.mode", () -> "never");
	}

	@Autowired
	private ChatRoomRepository chatRoomRepository;

	@Autowired
	private ChatMemberRepository chatMemberRepository;

	@Autowired
	private MessageRepository messageRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void findsActiveRoomsForUserAndOptionalType() {
		User me = persist(user("rooms-me@example.com", "rooms-me"));
		User friend = persist(user("rooms-friend@example.com", "rooms-friend"));
		User other = persist(user("rooms-other@example.com", "rooms-other"));
		persistQuestion(10L, other);
		ChatRoom direct = chatRoomRepository.save(ChatRoom.direct(me.getId(), friend.getId()));
		ChatRoom question = chatRoomRepository.save(ChatRoom.question(10L, me.getId(), other.getId()));
		ChatRoom left = chatRoomRepository.save(ChatRoom.direct(me.getId(), other.getId()));
		chatMemberRepository.save(ChatMember.join(direct, me));
		chatMemberRepository.save(ChatMember.join(direct, friend));
		chatMemberRepository.save(ChatMember.join(question, me));
		ChatMember leftMember = chatMemberRepository.save(ChatMember.join(left, me));
		leftMember.leave(OffsetDateTime.parse("2026-07-08T09:00:00+09:00"));

		assertThat(chatRoomRepository.findActiveRoomsByUserId(me.getId()))
			.extracting(ChatRoom::getId)
			.containsExactlyInAnyOrder(direct.getId(), question.getId());
		assertThat(chatRoomRepository.findActiveRoomsByUserIdAndRoomType(me.getId(), RoomType.direct))
			.extracting(ChatRoom::getId)
			.containsExactly(direct.getId());
	}

	@Test
	void findsActiveMemberOnlyWhenLeftAtIsNull() {
		User me = persist(user("member-me@example.com", "member-me"));
		User friend = persist(user("member-friend@example.com", "member-friend"));
		ChatRoom room = chatRoomRepository.save(ChatRoom.direct(me.getId(), friend.getId()));
		ChatMember active = chatMemberRepository.save(ChatMember.join(room, me));
		ChatMember left = chatMemberRepository.save(ChatMember.join(room, friend));
		left.leave(OffsetDateTime.parse("2026-07-08T09:00:00+09:00"));

		assertThat(chatMemberRepository.findActiveByRoomIdAndUserId(room.getId(), me.getId()))
			.contains(active);
		assertThat(chatMemberRepository.findActiveByRoomIdAndUserId(room.getId(), friend.getId()))
			.isEmpty();
	}

	@Test
	void findsAllRoomMembersWithUserFetched() {
		User me = persist(user("all-me@example.com", "all-me"));
		User friend = persist(user("all-friend@example.com", "all-friend"));
		ChatRoom room = chatRoomRepository.save(ChatRoom.direct(me.getId(), friend.getId()));
		chatMemberRepository.save(ChatMember.join(room, me));
		ChatMember left = chatMemberRepository.save(ChatMember.join(room, friend));
		left.leave(OffsetDateTime.parse("2026-07-08T09:00:00+09:00"));
		entityManager.flush();
		entityManager.clear();

		List<ChatMember> members = chatMemberRepository.findByRoom_Id(room.getId());

		// left 멤버 포함 전원 반환 + JOIN FETCH로 user가 즉시 로딩되어 clear 이후에도 접근 가능(N+1 없음).
		assertThat(members)
			.extracting(member -> member.getUser().getNickname())
			.containsExactlyInAnyOrder("all-me", "all-friend");
	}

	@Test
	void scopesVisibleHistoryCursorUnreadAndLastMessageToViewer() {
		User returning = persist(user("visible-returning@example.com", "visible-returning"));
		User other = persist(user("visible-other@example.com", "visible-other"));
		ChatRoom room = chatRoomRepository.save(ChatRoom.direct(returning.getId(), other.getId()));
		ChatMember returningMember = chatMemberRepository.save(ChatMember.join(room, returning));
		chatMemberRepository.save(ChatMember.join(room, other));
		OffsetDateTime base = OffsetDateTime.parse("2026-07-08T10:00:00+09:00");
		messageRepository.save(Message.text(room, other, "old-1", base));
		Message old2 = messageRepository.save(Message.text(room, other, "old-2", base.plusMinutes(1)));
		returningMember.leave(base.plusMinutes(2));
		returningMember.reactivateAfter(old2.getId());
		messageRepository.save(Message.text(room, other, "new-1", base.plusMinutes(3)));
		entityManager.flush();
		entityManager.clear();

		assertThat(messageRepository.findLatestVisibleMessages(
			room.getId(), returning.getId(), PageRequest.of(0, 10)
		))
			.extracting(Message::getContent)
			.containsExactly("new-1");
		assertThat(messageRepository.findLatestVisibleMessages(
			room.getId(), other.getId(), PageRequest.of(0, 10)
		))
			.extracting(Message::getContent)
			.containsExactly("new-1", "old-2", "old-1");
		assertThat(messageRepository.findVisibleMessagesBeforeCursor(
			room.getId(),
			returning.getId(),
			base.plusMinutes(2),
			Long.MAX_VALUE,
			PageRequest.of(0, 10)
		)).isEmpty();
		assertThat(messageRepository.countUnreadByRoomIds(returning.getId(), List.of(room.getId())))
			.extracting(MessageRepository.RoomUnreadCount::getRoomId, MessageRepository.RoomUnreadCount::getUnreadCount)
			.containsExactly(org.assertj.core.groups.Tuple.tuple(room.getId(), 1L));
		assertThat(messageRepository.findLastVisibleMessagesByRoomIds(returning.getId(), List.of(room.getId())))
			.extracting(Message::getContent)
			.containsExactly("new-1");
	}

	@Test
	void excludesDeletedMessagesIndependentlyWhilePreservingMaxIdAndReportContext() {
		User returning = persist(user("deleted-returning@example.com", "deleted-returning"));
		User other = persist(user("deleted-other@example.com", "deleted-other"));
		ChatRoom room = chatRoomRepository.save(ChatRoom.direct(returning.getId(), other.getId()));
		ChatMember returningMember = chatMemberRepository.save(ChatMember.join(room, returning));
		chatMemberRepository.save(ChatMember.join(room, other));
		OffsetDateTime base = OffsetDateTime.parse("2026-07-08T10:00:00+09:00");
		Message old1 = messageRepository.save(Message.text(room, other, "old-1", base));
		Message old2 = messageRepository.save(Message.text(room, other, "old-2", base.plusMinutes(1)));
		returningMember.leave(base.plusMinutes(2));
		returningMember.reactivateAfter(old2.getId());
		Message deletedNew = messageRepository.save(Message.text(room, other, "new-1", base.plusMinutes(3)));
		deletedNew.markDeleted(base.plusMinutes(4));
		entityManager.flush();
		entityManager.clear();

		assertThat(messageRepository.findLatestVisibleMessages(
			room.getId(), returning.getId(), PageRequest.of(0, 10)
		)).isEmpty();
		assertThat(messageRepository.findVisibleMessagesBeforeCursor(
			room.getId(),
			returning.getId(),
			base.plusMinutes(5),
			Long.MAX_VALUE,
			PageRequest.of(0, 10)
		)).isEmpty();
		assertThat(messageRepository.countUnreadByRoomIds(returning.getId(), List.of(room.getId())))
			.isEmpty();
		assertThat(messageRepository.findLastVisibleMessagesByRoomIds(returning.getId(), List.of(room.getId())))
			.isEmpty();
		assertThat(messageRepository.findMaxMessageIdByRoomId(room.getId())).isEqualTo(deletedNew.getId());
		assertThat(messageRepository.findContextBeforeMessage(
			room.getId(), old2.getCreatedAt(), old2.getId(), PageRequest.of(0, 10)
		))
			.extracting(Message::getContent)
			.containsExactly("old-1");
		assertThat(messageRepository.findContextAfterMessage(
			room.getId(), old1.getCreatedAt(), old1.getId(), PageRequest.of(0, 10)
		))
			.extracting(Message::getContent)
			.containsExactly("old-2");
	}

	@Test
	void findsOneLastVisibleMessagePerRoomAfterFilteringDeletedAndCutoffMessages() {
		User viewer = persist(user("last-viewer@example.com", "last-viewer"));
		User firstPeer = persist(user("last-first-peer@example.com", "last-first-peer"));
		User secondPeer = persist(user("last-second-peer@example.com", "last-second-peer"));
		ChatRoom firstRoom = chatRoomRepository.save(ChatRoom.direct(viewer.getId(), firstPeer.getId()));
		ChatRoom secondRoom = chatRoomRepository.save(ChatRoom.direct(viewer.getId(), secondPeer.getId()));
		chatMemberRepository.save(ChatMember.join(firstRoom, viewer));
		chatMemberRepository.save(ChatMember.join(firstRoom, firstPeer));
		ChatMember secondRoomViewer = chatMemberRepository.save(ChatMember.join(secondRoom, viewer));
		chatMemberRepository.save(ChatMember.join(secondRoom, secondPeer));
		OffsetDateTime base = OffsetDateTime.parse("2026-07-08T10:00:00+09:00");
		messageRepository.save(Message.text(firstRoom, firstPeer, "first-visible", base));
		Message firstDeleted = messageRepository.save(
			Message.text(firstRoom, firstPeer, "first-deleted", base.plusMinutes(5))
		);
		firstDeleted.markDeleted(base.plusMinutes(6));
		Message secondHidden = messageRepository.save(
			Message.text(secondRoom, secondPeer, "second-hidden", base.plusMinutes(1))
		);
		secondRoomViewer.leave(base.plusMinutes(2));
		secondRoomViewer.reactivateAfter(secondHidden.getId());
		messageRepository.save(Message.text(secondRoom, secondPeer, "second-visible", base.plusMinutes(3)));
		Message secondDeleted = messageRepository.save(
			Message.text(secondRoom, secondPeer, "second-deleted", base.plusMinutes(7))
		);
		secondDeleted.markDeleted(base.plusMinutes(8));
		entityManager.flush();
		entityManager.clear();

		assertThat(messageRepository.findLastVisibleMessagesByRoomIds(
			viewer.getId(), List.of(firstRoom.getId(), secondRoom.getId())
		))
			.extracting(message -> message.getRoom().getId(), Message::getContent)
			.containsExactly(
				org.assertj.core.groups.Tuple.tuple(secondRoom.getId(), "second-visible"),
				org.assertj.core.groups.Tuple.tuple(firstRoom.getId(), "first-visible")
			);
	}

	@Test
	void userFacingQueriesRequireAnActiveViewerMembership() {
		User viewer = persist(user("inactive-viewer@example.com", "inactive-viewer"));
		User peer = persist(user("inactive-peer@example.com", "inactive-peer"));
		ChatRoom room = chatRoomRepository.save(ChatRoom.direct(viewer.getId(), peer.getId()));
		ChatMember viewerMember = chatMemberRepository.save(ChatMember.join(room, viewer));
		chatMemberRepository.save(ChatMember.join(room, peer));
		OffsetDateTime base = OffsetDateTime.parse("2026-07-08T10:00:00+09:00");
		messageRepository.save(Message.text(room, peer, "hidden-while-inactive", base));
		viewerMember.leave(base.plusMinutes(1));
		entityManager.flush();
		entityManager.clear();

		assertThat(messageRepository.findLatestVisibleMessages(
			room.getId(), viewer.getId(), PageRequest.of(0, 10)
		)).isEmpty();
		assertThat(messageRepository.findVisibleMessagesBeforeCursor(
			room.getId(),
			viewer.getId(),
			base.plusMinutes(2),
			Long.MAX_VALUE,
			PageRequest.of(0, 10)
		)).isEmpty();
		assertThat(messageRepository.countUnreadByRoomIds(viewer.getId(), List.of(room.getId())))
			.isEmpty();
		assertThat(messageRepository.findLastVisibleMessagesByRoomIds(viewer.getId(), List.of(room.getId())))
			.isEmpty();
	}

	@Test
	void groupMemberWithZeroCutoffSeesHistoryAndScalarRoomTypeRemainsQueryable() {
		User viewer = persist(user("group-viewer@example.com", "group-viewer"));
		User peer = persist(user("group-peer@example.com", "group-peer"));
		persistMeeting(9001L, viewer);
		ChatRoom room = chatRoomRepository.save(ChatRoom.group(9001L));
		ChatMember viewerMember = chatMemberRepository.save(ChatMember.join(room, viewer));
		chatMemberRepository.save(ChatMember.join(room, peer));
		OffsetDateTime base = OffsetDateTime.parse("2026-07-08T10:00:00+09:00");
		messageRepository.save(Message.text(room, peer, "group-history", base));
		viewerMember.leave(base.plusMinutes(1));
		viewerMember.rejoin();
		entityManager.flush();
		entityManager.clear();

		assertThat(viewerMember.getVisibleAfterMessageId()).isZero();
		assertThat(chatMemberRepository.findActiveRoomTypeByRoomIdAndUserId(room.getId(), viewer.getId()))
			.contains(RoomType.group);
		assertThat(messageRepository.findLatestVisibleMessages(
			room.getId(), viewer.getId(), PageRequest.of(0, 10)
		))
			.extracting(Message::getContent)
			.containsExactly("group-history");
	}

	@Test
	void countsUnreadMessagesAndFindsLastMessagesInBulk() {
		User me = persist(user("unread-me@example.com", "unread-me"));
		User friend = persist(user("unread-friend@example.com", "unread-friend"));
		ChatRoom room = chatRoomRepository.save(ChatRoom.direct(me.getId(), friend.getId()));
		ChatMember meMember = chatMemberRepository.save(ChatMember.join(room, me));
		chatMemberRepository.save(ChatMember.join(room, friend));
		OffsetDateTime readAt = OffsetDateTime.parse("2026-07-08T10:00:00+09:00");
		meMember.markRead(readAt);
		messageRepository.save(Message.text(room, friend, "old", readAt.minusMinutes(1)));
		messageRepository.save(Message.text(room, me, "own", readAt.plusMinutes(1)));
		messageRepository.save(Message.text(room, friend, "new", readAt.plusMinutes(2)));
		entityManager.flush();
		entityManager.clear();

		assertThat(messageRepository.countUnreadByRoomIds(me.getId(), List.of(room.getId())))
			.extracting(MessageRepository.RoomUnreadCount::getRoomId, MessageRepository.RoomUnreadCount::getUnreadCount)
			.containsExactly(org.assertj.core.groups.Tuple.tuple(room.getId(), 1L));
		assertThat(messageRepository.findLastVisibleMessagesByRoomIds(me.getId(), List.of(room.getId())))
			.extracting(Message::getContent)
			.containsExactly("new");
	}

	@Test
	void findsMessagesByCursorDescending() {
		User me = persist(user("cursor-me@example.com", "cursor-me"));
		User friend = persist(user("cursor-friend@example.com", "cursor-friend"));
		ChatRoom room = chatRoomRepository.save(ChatRoom.direct(me.getId(), friend.getId()));
		chatMemberRepository.save(ChatMember.join(room, me));
		chatMemberRepository.save(ChatMember.join(room, friend));
		OffsetDateTime base = OffsetDateTime.parse("2026-07-08T10:00:00+09:00");
		messageRepository.save(Message.text(room, me, "first", base));
		messageRepository.save(Message.text(room, friend, "second", base.plusMinutes(1)));
		Message third = messageRepository.save(Message.text(room, me, "third", base.plusMinutes(2)));
		Message deleted = messageRepository.save(Message.text(room, friend, "deleted", base.plusMinutes(3)));
		deleted.markDeleted(base.plusMinutes(4));
		entityManager.flush();
		entityManager.clear();

		assertThat(messageRepository.findLatestVisibleMessages(room.getId(), me.getId(), PageRequest.of(0, 3)))
			.extracting(Message::getContent)
			.containsExactly("third", "second", "first");
		assertThat(messageRepository.findVisibleMessagesBeforeCursor(
			room.getId(), me.getId(), base.plusMinutes(2), third.getId(), PageRequest.of(0, 2)
		))
			.extracting(Message::getContent)
			.containsExactly("second", "first");
	}

	@Test
	void findsReportContextAroundMessageWithLimitAndDeletedMessagesExcluded() {
		User me = persist(user("report-context-me@example.com", "report-context-me"));
		User friend = persist(user("report-context-friend@example.com", "report-context-friend"));
		ChatRoom room = chatRoomRepository.save(ChatRoom.direct(me.getId(), friend.getId()));
		OffsetDateTime base = OffsetDateTime.parse("2026-07-08T10:00:00+09:00");
		for (int i = 25; i >= 1; i--) {
			messageRepository.save(Message.text(room, me, "before-" + i, base.minusMinutes(i)));
		}
		Message deletedBefore = messageRepository.save(Message.text(room, me, "deleted-before", base.minusSeconds(30)));
		deletedBefore.markDeleted(base.plusMinutes(1));
		Message reported = messageRepository.save(Message.text(room, friend, "reported", base));
		Message deletedAfter = messageRepository.save(Message.text(room, me, "deleted-after", base.plusSeconds(30)));
		deletedAfter.markDeleted(base.plusMinutes(1));
		for (int i = 1; i <= 25; i++) {
			messageRepository.save(Message.text(room, me, "after-" + i, base.plusMinutes(i)));
		}
		entityManager.flush();
		entityManager.clear();

		assertThat(messageRepository.findContextBeforeMessage(room.getId(), base, reported.getId(), PageRequest.of(0, 20)))
			.extracting(Message::getContent)
			.containsExactly(
				"before-1",
				"before-2",
				"before-3",
				"before-4",
				"before-5",
				"before-6",
				"before-7",
				"before-8",
				"before-9",
				"before-10",
				"before-11",
				"before-12",
				"before-13",
				"before-14",
				"before-15",
				"before-16",
				"before-17",
				"before-18",
				"before-19",
				"before-20"
			);
		assertThat(messageRepository.findContextAfterMessage(room.getId(), base, reported.getId(), PageRequest.of(0, 20)))
			.extracting(Message::getContent)
			.containsExactly(
				"after-1",
				"after-2",
				"after-3",
				"after-4",
				"after-5",
				"after-6",
				"after-7",
				"after-8",
				"after-9",
				"after-10",
				"after-11",
				"after-12",
				"after-13",
				"after-14",
				"after-15",
				"after-16",
				"after-17",
				"after-18",
				"after-19",
				"after-20"
			);
	}

	private User user(String email, String nickname) {
		return User.createEmailUser(
			email,
			"hash",
			nickname,
			LocalDate.of(1995, 1, 1),
			GenderType.female,
			"KR"
		);
	}

	private User persist(User user) {
		entityManager.persist(user);
		return user;
	}

	private void persistQuestion(Long questionId, User author) {
		entityManager.createNativeQuery("""
			INSERT INTO pins (pin_id, pin_type, author_id, location, address)
			VALUES (:pinId, 'question', :authorId, ST_GeogFromText('SRID=4326;POINT(127.0 37.5)'), 'address')
			""")
			.setParameter("pinId", questionId)
			.setParameter("authorId", author.getId())
			.executeUpdate();
		entityManager.createNativeQuery("""
			INSERT INTO questions (question_id, pin_id, author_id, title, content)
			VALUES (:questionId, :pinId, :authorId, 'question', 'content')
			""")
			.setParameter("questionId", questionId)
			.setParameter("pinId", questionId)
			.setParameter("authorId", author.getId())
			.executeUpdate();
	}

	private void persistMeeting(Long meetingId, User host) {
		entityManager.createNativeQuery("""
			INSERT INTO pins (pin_id, pin_type, author_id, location, address)
			VALUES (:pinId, 'meeting', :hostId, ST_GeogFromText('SRID=4326;POINT(127.0 37.5)'), 'address')
			""")
			.setParameter("pinId", meetingId)
			.setParameter("hostId", host.getId())
			.executeUpdate();
		entityManager.createNativeQuery("""
			INSERT INTO meetings (meeting_id, pin_id, host_id, title, meeting_at)
			VALUES (:meetingId, :pinId, :hostId, 'group', TIMESTAMPTZ '2026-07-08 10:00:00+09:00')
			""")
			.setParameter("meetingId", meetingId)
			.setParameter("pinId", meetingId)
			.setParameter("hostId", host.getId())
			.executeUpdate();
	}

	@SpringBootApplication(scanBasePackages = "shinhan.fibri.ieum.common")
	@EntityScan(basePackageClasses = {User.class, ChatRoom.class, ChatMember.class, Message.class})
	static class TestApplication {
	}
}
