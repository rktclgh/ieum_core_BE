package shinhan.fibri.ieum.main.auth.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Testcontainers;
import shinhan.fibri.ieum.testsupport.CanonicalPostgresContainer;
import shinhan.fibri.ieum.testsupport.SqlScriptRunner;

@Testcontainers(disabledWithoutDocker = true)
class UserAuthVersionCanonicalSchemaIntegrationTest {

	private static final String DATABASE = "ieum_user_auth_version_schema";
	private static JdbcClient jdbc;

	@BeforeAll
	static void loadCanonicalSchema() {
		CanonicalPostgresContainer.recreateDatabase(DATABASE);
		SqlScriptRunner.run(DATABASE, "schema.sql");
		jdbc = JdbcClient.create(CanonicalPostgresContainer.dataSource(DATABASE));
	}

	@AfterAll
	static void cleanUpDatabase() {
		JdbcClient admin = JdbcClient.create(CanonicalPostgresContainer.dataSource("postgres"));
		admin.sql("DROP DATABASE IF EXISTS " + DATABASE + " WITH (FORCE)").update();
	}

	@Test
	void canonicalUsersTableDefinesVersionDefaultNotNullAndNamedCheck() {
		Map<String, Object> column = jdbc.sql("""
			SELECT data_type, is_nullable, column_default
			FROM information_schema.columns
			WHERE table_schema = 'public'
			  AND table_name = 'users'
			  AND column_name = 'auth_version'
			""").query().singleRow();
		String constraintDefinition = jdbc.sql("""
			SELECT pg_get_constraintdef(oid)
			FROM pg_constraint
			WHERE conrelid = 'users'::regclass
			  AND conname = 'ck_users_auth_version_nonnegative'
			""").query(String.class).single();

		assertThat(column)
			.containsEntry("data_type", "bigint")
			.containsEntry("is_nullable", "NO");
		assertThat(column.get("column_default")).asString().contains("0");
		assertThat(constraintDefinition).contains("auth_version >= 0");

		long userId = jdbc.sql("""
			INSERT INTO users(email, nickname)
			VALUES ('schema-user@example.com', 'schema-user')
			RETURNING user_id
			""")
			.query(Long.class).single();
		assertThat(jdbc.sql("SELECT auth_version FROM users WHERE user_id = :userId")
			.param("userId", userId).query(Long.class).single()).isZero();
		assertThatThrownBy(() -> jdbc.sql("UPDATE users SET auth_version = -1 WHERE user_id = :userId")
			.param("userId", userId).update())
			.isInstanceOf(DataAccessException.class)
			.hasMessageContaining("ck_users_auth_version_nonnegative");
	}
}
