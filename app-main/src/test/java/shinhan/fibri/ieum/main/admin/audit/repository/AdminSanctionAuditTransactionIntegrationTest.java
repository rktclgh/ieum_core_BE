package shinhan.fibri.ieum.main.admin.audit.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import shinhan.fibri.ieum.common.auth.domain.UserRole;
import shinhan.fibri.ieum.common.auth.domain.UserStatus;
import shinhan.fibri.ieum.common.auth.principal.AuthenticatedUser;
import shinhan.fibri.ieum.main.admin.audit.domain.AdminAuditAction;
import shinhan.fibri.ieum.main.admin.user.domain.SanctionType;
import shinhan.fibri.ieum.main.admin.user.dto.CreateSanctionRequest;
import shinhan.fibri.ieum.main.admin.user.service.AdminSanctionService;
import shinhan.fibri.ieum.main.auth.session.RedisAuthSessionStore;
import shinhan.fibri.ieum.main.notification.sse.SseConnectionRegistry;
import shinhan.fibri.ieum.testsupport.CanonicalPostgresDataSource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
	AdminSanctionService.class,
	AdminSanctionAuditTransactionIntegrationTest.AuditWriterTestConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdminSanctionAuditTransactionIntegrationTest {

	private static final String DATABASE = "ieum_admin_sanction_audit_transaction";

	@DynamicPropertySource
	static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
		CanonicalPostgresDataSource.recreateAndRegister(registry, DATABASE);
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
	}

	@Autowired
	private AdminSanctionService service;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private RedisAuthSessionStore sessionStore;

	@MockitoBean
	private SseConnectionRegistry sseConnectionRegistry;

	@BeforeEach
	void resetRows() {
		jdbc.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
		jdbc.update("""
			INSERT INTO users(user_id, email, password_hash, nickname, email_verified, role, status)
			VALUES
				(1, 'admin@example.com', 'hash', 'admin', TRUE, 'admin', 'active'),
				(10, 'target@example.com', 'hash', 'target', TRUE, 'user', 'active')
			""");
	}

	@Test
	void successfulAuditInsertFollowedByWriterFailureRollsBackJpaAndJdbcTogether() {
		assertThatThrownBy(() -> service.sanction(
			new AuthenticatedUser(1L, "admin@example.com", UserRole.admin, UserStatus.active),
			10L,
			new CreateSanctionRequest(SanctionType.permanent, "abuse", null)
		)).isInstanceOf(ForcedAfterAuditInsertException.class)
			.hasMessage("forced failure after administrator audit insert");

		assertThat(jdbc.queryForObject("SELECT status::text FROM users WHERE user_id = 10", String.class))
			.isEqualTo("active");
		assertThat(jdbc.queryForObject("SELECT auth_version FROM users WHERE user_id = 10", Long.class))
			.isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM user_sanctions", Long.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_audit_logs", Long.class)).isZero();
		verify(sessionStore, never()).revokeAllSessionsOfUser(10L);
		verify(sseConnectionRegistry, never()).closeUser(10L);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class AuditWriterTestConfiguration {

		@Bean
		JdbcClient jdbcClient(DataSource dataSource) {
			return JdbcClient.create(dataSource);
		}

		@Bean
		ObjectMapper objectMapper() {
			return new ObjectMapper().findAndRegisterModules();
		}

		@Bean
		AdminAuditLogWriter auditLogWriter(JdbcClient jdbcClient, ObjectMapper objectMapper) {
			return new InsertThenFailAdminAuditLogWriter(jdbcClient, objectMapper);
		}
	}

	private static final class InsertThenFailAdminAuditLogWriter extends AdminAuditLogWriter {

		private InsertThenFailAdminAuditLogWriter(JdbcClient jdbcClient, ObjectMapper objectMapper) {
			super(jdbcClient, objectMapper);
		}

		@Override
		public void append(
			Long actorUserId,
			AdminAuditAction action,
			String targetType,
			long targetId,
			Map<String, ?> details
		) {
			super.append(actorUserId, action, targetType, targetId, details);
			throw new ForcedAfterAuditInsertException();
		}
	}

	private static final class ForcedAfterAuditInsertException extends RuntimeException {

		private ForcedAfterAuditInsertException() {
			super("forced failure after administrator audit insert");
		}
	}
}
