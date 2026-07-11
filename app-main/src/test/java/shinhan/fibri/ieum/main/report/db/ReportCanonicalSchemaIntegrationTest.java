package shinhan.fibri.ieum.main.report.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import shinhan.fibri.ieum.testsupport.CanonicalPostgresContainer;
import shinhan.fibri.ieum.testsupport.SqlScriptRunner;

class ReportCanonicalSchemaIntegrationTest {

	private static final String DATABASE = "ieum_main_report_schema";

	@Test
	void canonicalSchemaExposesReportTablesForMigrationSmokeTests() {
		URL schema = getClass().getClassLoader().getResource("schema.sql");
		assertThat(schema).isNotNull();

		CanonicalPostgresContainer.recreateDatabase(DATABASE);
		SqlScriptRunner.run(DATABASE, "schema.sql");

		JdbcClient jdbc = JdbcClient.create(CanonicalPostgresContainer.dataSource(DATABASE));
		assertThat(columns(jdbc, "reports")).contains(
			"report_id",
			"reporter_id",
			"message_id",
			"reported_user_id",
			"reason",
			"context_snapshot",
			"ai_recommendation",
			"status");
		assertThat(tableExists(jdbc, "user_sanctions")).isTrue();
	}

	private static java.util.List<String> columns(JdbcClient jdbc, String tableName) {
		return jdbc.sql("""
			SELECT column_name
			FROM information_schema.columns
			WHERE table_schema = 'public' AND table_name = :tableName
			ORDER BY ordinal_position
			""")
			.param("tableName", tableName)
			.query(String.class)
			.list();
	}

	private static boolean tableExists(JdbcClient jdbc, String tableName) {
		return jdbc.sql("SELECT to_regclass(:tableName) IS NOT NULL")
			.param("tableName", tableName)
			.query(Boolean.class)
			.single();
	}
}
