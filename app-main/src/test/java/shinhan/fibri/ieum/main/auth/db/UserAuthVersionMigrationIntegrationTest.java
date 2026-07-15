package shinhan.fibri.ieum.main.auth.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Testcontainers;
import shinhan.fibri.ieum.testsupport.CanonicalPostgresContainer;
import shinhan.fibri.ieum.testsupport.SqlScriptRunner;

@Testcontainers(disabledWithoutDocker = true)
class UserAuthVersionMigrationIntegrationTest {

	private static final String DATABASE = "ieum_user_auth_version_migration";

	private JdbcClient jdbc;

	@BeforeEach
	void recreateDatabase() {
		CanonicalPostgresContainer.recreateDatabase(DATABASE);
		jdbc = JdbcClient.create(CanonicalPostgresContainer.dataSource(DATABASE));
	}

	@AfterAll
	static void cleanUpDatabase() {
		JdbcClient admin = JdbcClient.create(CanonicalPostgresContainer.dataSource("postgres"));
		admin.sql("DROP DATABASE IF EXISTS " + DATABASE + " WITH (FORCE)").update();
	}

	@Test
	void upgradesLegacyRowsWithNonnegativeNotNullVersion() {
		jdbc.sql("""
			CREATE TABLE users (
				user_id BIGSERIAL PRIMARY KEY,
				email VARCHAR(255)
			)
			""").update();
		jdbc.sql("INSERT INTO users(email) VALUES ('legacy@example.com')").update();

		SqlScriptRunner.run(DATABASE, "migrations/v25_user_auth_version.sql");

		assertThat(jdbc.sql("SELECT auth_version FROM users WHERE email = 'legacy@example.com'")
			.query(Long.class).single()).isZero();
		assertThat(columnMetadata())
			.containsEntry("data_type", "bigint")
			.containsEntry("is_nullable", "NO");
		assertThat(columnMetadata().get("column_default")).asString().contains("0");
		assertThat(checkConstraintDefinition()).contains("auth_version >= 0");
		assertThatThrownBy(() -> jdbc.sql("UPDATE users SET auth_version = -1").update())
			.isInstanceOf(DataAccessException.class)
			.hasMessageContaining("ck_users_auth_version_nonnegative");
	}

	@Test
	void failedConstraintReplacementRollsBackTheWholeMigration() {
		jdbc.sql("""
			CREATE TABLE users (
				user_id BIGSERIAL PRIMARY KEY,
				auth_version BIGINT NOT NULL DEFAULT 0,
				CONSTRAINT ck_users_auth_version_nonnegative CHECK (auth_version >= -1)
			)
			""").update();
		jdbc.sql("INSERT INTO users(auth_version) VALUES (-1)").update();

		assertThatThrownBy(() -> SqlScriptRunner.run(DATABASE, "migrations/v25_user_auth_version.sql"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("ck_users_auth_version_nonnegative");

		assertThat(jdbc.sql("SELECT auth_version FROM users").query(Long.class).single()).isEqualTo(-1L);
		assertThatThrownBy(() -> jdbc.sql("UPDATE users SET auth_version = -2").update())
			.isInstanceOf(DataAccessException.class)
			.hasMessageContaining("ck_users_auth_version_nonnegative");
	}

	private Map<String, Object> columnMetadata() {
		return jdbc.sql("""
			SELECT data_type, is_nullable, column_default
			FROM information_schema.columns
			WHERE table_schema = 'public'
			  AND table_name = 'users'
			  AND column_name = 'auth_version'
			""").query().singleRow();
	}

	private String checkConstraintDefinition() {
		return jdbc.sql("""
			SELECT pg_get_constraintdef(oid)
			FROM pg_constraint
			WHERE conrelid = 'users'::regclass
			  AND conname = 'ck_users_auth_version_nonnegative'
			""").query(String.class).single();
	}
}
