package shinhan.fibri.ieum.main.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import shinhan.fibri.ieum.common.chat.domain.ChatRoom;
import shinhan.fibri.ieum.common.chat.domain.Message;
import shinhan.fibri.ieum.common.chat.domain.RoomType;
import shinhan.fibri.ieum.common.chat.repository.ChatRoomRepository;
import shinhan.fibri.ieum.common.chat.repository.MessageRepository;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ChatRepositoryPostgresIntegrationTest {

	@Container
	@SuppressWarnings("resource")
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
		DockerImageName.parse("postgis/postgis:16-3.4-alpine").asCompatibleSubstituteFor("postgres")
	).waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2));

	@DynamicPropertySource
	static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
		registry.add(
			"spring.jpa.properties.hibernate.dialect",
			() -> "shinhan.fibri.ieum.common.config.SnakeCasePostgreSQLDialect"
		);
	}

	@Autowired
	private ChatRoomRepository chatRoomRepository;

	@Autowired
	private MessageRepository messageRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUpSchema() {
		createEnumType("auth_provider", "email", "google", "kakao");
		createEnumType("user_role", "user", "admin");
		createEnumType("user_status", "active", "suspended");
		createEnumType("user_grade", "bronze", "silver", "gold", "platinum", "diamond");
		createEnumType("gender_type", "male", "female", "other");
		createEnumType("room_type", "direct", "group", "question");

		jdbcTemplate.execute("""
			CREATE TABLE IF NOT EXISTS users (
				user_id BIGINT PRIMARY KEY,
				email VARCHAR(254) NOT NULL,
				password_hash VARCHAR(255) NOT NULL,
				nickname VARCHAR(50) NOT NULL,
				provider_uid VARCHAR(255),
				birth_date DATE NOT NULL,
				provider auth_provider NOT NULL,
				email_verified BOOLEAN NOT NULL,
				role user_role NOT NULL,
				status user_status NOT NULL,
				grade user_grade NOT NULL,
				gender gender_type,
				nationality VARCHAR(2),
				accepted_count INTEGER NOT NULL,
				password_reset_required BOOLEAN NOT NULL,
				last_active_at TIMESTAMPTZ,
				profile_file_id UUID,
				deleted_at TIMESTAMPTZ
			)
			""");
		jdbcTemplate.execute("""
			CREATE TABLE IF NOT EXISTS chat_rooms (
				room_id BIGINT PRIMARY KEY,
				room_type room_type NOT NULL,
				meeting_id BIGINT,
				question_id BIGINT,
				room_key VARCHAR(80) UNIQUE,
				created_at TIMESTAMPTZ NOT NULL
			)
			""");
		jdbcTemplate.execute("""
			CREATE TABLE IF NOT EXISTS chat_members (
				room_id BIGINT NOT NULL REFERENCES chat_rooms(room_id),
				user_id BIGINT NOT NULL REFERENCES users(user_id),
				joined_at TIMESTAMPTZ NOT NULL,
				left_at TIMESTAMPTZ,
				last_read_at TIMESTAMPTZ,
				pinned_at TIMESTAMPTZ,
				notify_enabled BOOLEAN NOT NULL,
				PRIMARY KEY (room_id, user_id)
			)
			""");
		jdbcTemplate.execute("""
			CREATE TABLE IF NOT EXISTS messages (
				message_id BIGINT PRIMARY KEY,
				room_id BIGINT NOT NULL REFERENCES chat_rooms(room_id),
				sender_id BIGINT NOT NULL REFERENCES users(user_id),
				content TEXT,
				image_file_id UUID,
				created_at TIMESTAMPTZ NOT NULL,
				deleted_at TIMESTAMPTZ
			)
			""");
		jdbcTemplate.execute("TRUNCATE TABLE messages, chat_members, chat_rooms, users CASCADE");

		insertUser(1L, "me@example.com", "me");
		insertUser(2L, "friend@example.com", "friend");
		insertRoom(10L, "direct", "2026-07-08T09:00:00+09:00");
		insertRoom(20L, "question", "2026-07-08T10:00:00+09:00");
		insertRoom(30L, "group", "2026-07-08T11:00:00+09:00");
		insertMember(10L, 1L, null);
		insertMember(20L, 1L, null);
		insertMember(30L, 1L, OffsetDateTime.parse("2026-07-08T12:00:00+09:00"));

		insertMessage(101L, 10L, 2L, "oldest", "2026-07-08T10:00:00+09:00");
		insertMessage(102L, 10L, 1L, "middle", "2026-07-08T11:00:00+09:00");
		insertMessage(103L, 10L, 2L, "newest", "2026-07-08T12:00:00+09:00");
	}

	@Test
	void findsAllActiveRoomsWithoutOptionalType() {
		List<ChatRoom> rooms = chatRoomRepository.findActiveRoomsByUserId(1L);

		assertThat(rooms)
			.extracting(ChatRoom::getId)
			.containsExactlyInAnyOrder(10L, 20L);
	}

	@Test
	void filtersActiveRoomsByType() {
		List<ChatRoom> rooms = chatRoomRepository.findActiveRoomsByUserIdAndRoomType(1L, RoomType.direct);

		assertThat(rooms)
			.extracting(ChatRoom::getId)
			.containsExactly(10L);
	}

	@Test
	void findsRecentMessagesWithoutOptionalCursor() {
		List<Message> messages = messageRepository.findRecentMessages(
			10L,
			PageRequest.of(0, 3)
		);

		assertThat(messages)
			.extracting(Message::getContent)
			.containsExactly("newest", "middle", "oldest");
	}

	@Test
	void findsMessagesBeforeNonNullCursor() {
		List<Message> messages = messageRepository.findMessagesBeforeCursor(
			10L,
			OffsetDateTime.parse("2026-07-08T11:00:00+09:00"),
			102L,
			PageRequest.of(0, 3)
		);

		assertThat(messages)
			.extracting(Message::getContent)
			.containsExactly("oldest");
	}

	private void createEnumType(String typeName, String... values) {
		String enumValues = String.join(", ", java.util.Arrays.stream(values)
			.map(value -> "'" + value + "'")
			.toList());
		jdbcTemplate.execute("""
			DO $$
			BEGIN
				IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = '%s') THEN
					CREATE TYPE %s AS ENUM (%s);
				END IF;
			END
			$$
			""".formatted(typeName, typeName, enumValues));
	}

	private void insertUser(long userId, String email, String nickname) {
		jdbcTemplate.update("""
			INSERT INTO users (
				user_id, email, password_hash, nickname, birth_date, provider,
				email_verified, role, status, grade, gender, nationality,
				accepted_count, password_reset_required
			) VALUES (?, ?, 'hash', ?, DATE '2000-01-01', 'email'::auth_provider,
				true, 'user'::user_role, 'active'::user_status, 'bronze'::user_grade,
				'other'::gender_type, 'KR', 0, false)
			""", userId, email, nickname);
	}

	private void insertRoom(long roomId, String roomType, String createdAt) {
		jdbcTemplate.update(
			"INSERT INTO chat_rooms (room_id, room_type, created_at) VALUES (?, ?::room_type, ?::timestamptz)",
			roomId,
			roomType,
			createdAt
		);
	}

	private void insertMember(long roomId, long userId, OffsetDateTime leftAt) {
		jdbcTemplate.update("""
			INSERT INTO chat_members (room_id, user_id, joined_at, left_at, notify_enabled)
			VALUES (?, ?, '2026-07-08T09:00:00+09:00'::timestamptz, ?, true)
			""", roomId, userId, leftAt);
	}

	private void insertMessage(long messageId, long roomId, long senderId, String content, String createdAt) {
		jdbcTemplate.update("""
			INSERT INTO messages (message_id, room_id, sender_id, content, created_at)
			VALUES (?, ?, ?, ?, ?::timestamptz)
			""", messageId, roomId, senderId, content, createdAt);
	}
}
