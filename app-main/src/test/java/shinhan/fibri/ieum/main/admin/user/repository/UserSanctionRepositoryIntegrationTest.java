package shinhan.fibri.ieum.main.admin.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * SnakeCasePostgreSQLDialect 회귀 테스트 — {@code user_sanctions.type}은 실제로 varchar(30) 컬럼이다
 * (마이그레이션 DDL 참조). {@code UserSanction.type}에 {@code @JdbcType(PostgreSQLEnumJdbcType)}를 붙이면
 * {@code findExpiredTemporarySanctions}의 JPQL enum 리터럴이 존재하지 않는 {@code sanction_type} pg 타입으로
 * 캐스팅되어 42704로 실패한다(운영에서 스케줄러가 매분 실패하는 버그). 순수 varchar 매핑으로 되돌린 뒤
 * 실제 Postgres에서 정상 동작하는지 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class UserSanctionRepositoryIntegrationTest {

	@Container
	@SuppressWarnings("resource")
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
		DockerImageName.parse("postgis/postgis:16-3.4-alpine").asCompatibleSubstituteFor("postgres")
	);

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
	private UserSanctionRepository userSanctionRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUpSchemaAndRows() {
		createSchema();
		jdbcTemplate.update("TRUNCATE TABLE user_sanctions RESTART IDENTITY");
		jdbcTemplate.update("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
		insertUser(1L);
		insertUser(2L);
		insertUser(3L);
		insertUser(4L);
		insertUser(99L);

		// user 1: 만료된 활성 temporary — 대상
		insertSanction(1L, "temporary", "2026-07-10T09:00:00+09:00", null, 99L);
		// user 2: 아직 만료되지 않은 temporary — 제외
		insertSanction(2L, "temporary", "2099-01-01T00:00:00+09:00", null, 99L);
		// user 3: permanent(ends_at 없음) — 제외
		insertSanction(3L, "permanent", null, null, 99L);
		// user 4: 이미 해제된 만료 temporary — 제외
		insertSanction(4L, "temporary", "2026-07-10T08:00:00+09:00", "2026-07-10T08:30:00+09:00", 99L);
	}

	@Test
	void findExpiredTemporarySanctionsRendersPlainVarcharComparisonAndSucceeds() {
		List<ExpiredSanctionRef> expired = userSanctionRepository.findExpiredTemporarySanctions(
			OffsetDateTime.parse("2026-07-10T10:00:00+09:00")
		);

		assertThat(expired).hasSize(1);
		assertThat(expired.get(0).userId()).isEqualTo(1L);
	}

	@Test
	void findExpiredTemporarySanctionsReturnsEmptyWhenNoneExpired() {
		List<ExpiredSanctionRef> expired = userSanctionRepository.findExpiredTemporarySanctions(
			OffsetDateTime.parse("2020-01-01T00:00:00+09:00")
		);

		assertThat(expired).isEmpty();
	}

	private void createSchema() {
		jdbcTemplate.execute("""
			CREATE TABLE IF NOT EXISTS users (
				user_id BIGINT PRIMARY KEY
			)
			""");
		jdbcTemplate.execute("""
			CREATE TABLE IF NOT EXISTS user_sanctions (
				sanction_id  bigserial PRIMARY KEY,
				user_id      bigint      NOT NULL REFERENCES users (user_id),
				type         varchar(30) NOT NULL,
				reason       text        NOT NULL,
				ends_at      timestamptz,
				created_by   bigint      NOT NULL REFERENCES users (user_id),
				created_at   timestamptz NOT NULL DEFAULT now(),
				released_at  timestamptz,
				released_by  bigint      REFERENCES users (user_id),
				CONSTRAINT chk_user_sanctions_ends_at CHECK (
					(type = 'temporary' AND ends_at IS NOT NULL)
					OR (type = 'permanent' AND ends_at IS NULL)
				)
			)
			""");
	}

	private void insertUser(Long userId) {
		jdbcTemplate.update("INSERT INTO users (user_id) VALUES (?)", userId);
	}

	private void insertSanction(Long userId, String type, String endsAt, String releasedAt, Long createdBy) {
		jdbcTemplate.update(
			"""
				INSERT INTO user_sanctions (user_id, type, reason, ends_at, created_by, released_at)
				VALUES (?, ?, 'abuse', ?::timestamptz, ?, ?::timestamptz)
				""",
			userId,
			type,
			endsAt,
			createdBy,
			releasedAt
		);
	}
}
