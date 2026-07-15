package shinhan.fibri.ieum.main.admin.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import shinhan.fibri.ieum.common.auth.domain.UserRole;
import shinhan.fibri.ieum.common.auth.domain.UserStatus;
import shinhan.fibri.ieum.common.auth.principal.AuthenticatedUser;
import shinhan.fibri.ieum.main.admin.user.exception.AdminRoleRequiredException;
import shinhan.fibri.ieum.main.admin.user.exception.LastAdminRequiredException;
import shinhan.fibri.ieum.main.auth.session.RedisAuthSessionStore;
import shinhan.fibri.ieum.testsupport.CanonicalPostgresContainer;
import shinhan.fibri.ieum.testsupport.SqlScriptRunner;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AdminUserRoleService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdminUserRoleConcurrencyIntegrationTest {

	private static final String DATABASE = "ieum_admin_role_concurrency";

	static {
		CanonicalPostgresContainer.recreateDatabase(DATABASE);
		SqlScriptRunner.run(DATABASE, "schema.sql");
	}

	@DynamicPropertySource
	static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", () -> CanonicalPostgresContainer.jdbcUrl(DATABASE));
		registry.add("spring.datasource.username", CanonicalPostgresContainer::username);
		registry.add("spring.datasource.password", CanonicalPostgresContainer::password);
		registry.add("spring.datasource.driver-class-name", CanonicalPostgresContainer::driverClassName);
		registry.add("spring.datasource.hikari.maximum-pool-size", () -> "4");
		registry.add("spring.datasource.hikari.minimum-idle", () -> "0");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
		registry.add(
			"spring.jpa.properties.hibernate.dialect",
			() -> "shinhan.fibri.ieum.common.config.SnakeCasePostgreSQLDialect"
		);
	}

	@Autowired
	private AdminUserRoleService service;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private RedisAuthSessionStore sessionStore;

	private long firstAdminId;
	private long secondAdminId;

	@AfterAll
	static void cleanUpDatabase() {
		JdbcTemplate admin = new JdbcTemplate(CanonicalPostgresContainer.dataSource("postgres"));
		admin.execute("DROP DATABASE IF EXISTS " + DATABASE + " WITH (FORCE)");
	}

	@BeforeEach
	void setUp() {
		jdbc.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
		firstAdminId = insertAdmin("first-admin@example.com", "first-admin");
		secondAdminId = insertAdmin("second-admin@example.com", "second-admin");
	}

	@Test
	void reciprocalConcurrentDemotionsLeaveOneAdminAndRejectStaleActor() throws Exception {
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			Future<RoleChangeAttempt> first = executor.submit(
				() -> demoteAfterStart(firstAdminId, secondAdminId, ready, start)
			);
			Future<RoleChangeAttempt> second = executor.submit(
				() -> demoteAfterStart(secondAdminId, firstAdminId, ready, start)
			);

			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			List<RoleChangeAttempt> attempts = List.of(
				first.get(10, TimeUnit.SECONDS),
				second.get(10, TimeUnit.SECONDS)
			);

			assertThat(attempts.stream().filter(RoleChangeAttempt::succeeded)).hasSize(1);
			assertThat(attempts.stream()
				.filter(attempt -> !attempt.succeeded())
				.map(RoleChangeAttempt::failureType))
				.containsExactly(AdminRoleRequiredException.class);
		}

		assertThat(activeAdminCount()).isOne();
		assertThat(jdbc.queryForObject("SELECT sum(auth_version) FROM users", Long.class)).isOne();
		verify(sessionStore, times(1)).revokeAllSessionsOfUser(anyLong());
	}

	@Test
	void canonicalFinalAdminCannotDemoteOwnAccount() {
		jdbc.update("UPDATE users SET role = 'user' WHERE user_id = ?", secondAdminId);

		assertThatThrownBy(() -> service.changeRole(
			principal(firstAdminId, "first-admin@example.com"),
			firstAdminId,
			UserRole.user
		)).isInstanceOf(LastAdminRequiredException.class);

		assertThat(activeAdminCount()).isOne();
		assertThat(jdbc.queryForObject(
			"SELECT auth_version FROM users WHERE user_id = ?",
			Long.class,
			firstAdminId
		)).isZero();
	}

	private RoleChangeAttempt demoteAfterStart(
		long actorId,
		long targetId,
		CountDownLatch ready,
		CountDownLatch start
	) {
		ready.countDown();
		await(start);
		try {
			service.changeRole(principal(actorId, "admin-%d@example.com".formatted(actorId)), targetId, UserRole.user);
			return new RoleChangeAttempt(true, null);
		} catch (RuntimeException exception) {
			return new RoleChangeAttempt(false, exception.getClass());
		}
	}

	private long insertAdmin(String email, String nickname) {
		return jdbc.queryForObject("""
			INSERT INTO users (email, password_hash, nickname, email_verified, role)
			VALUES (?, 'hash', ?, TRUE, 'admin')
			RETURNING user_id
			""", Long.class, email, nickname);
	}

	private int activeAdminCount() {
		return jdbc.queryForObject(
			"SELECT count(*) FROM users WHERE role = 'admin' AND deleted_at IS NULL",
			Integer.class
		);
	}

	private static AuthenticatedUser principal(long userId, String email) {
		return new AuthenticatedUser(userId, email, UserRole.admin, UserStatus.active);
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while awaiting concurrent role change", exception);
		}
	}

	private record RoleChangeAttempt(boolean succeeded, Class<? extends RuntimeException> failureType) {
	}
}
