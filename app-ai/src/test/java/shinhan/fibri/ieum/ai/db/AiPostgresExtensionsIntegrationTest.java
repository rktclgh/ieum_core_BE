package shinhan.fibri.ieum.ai.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import shinhan.fibri.ieum.ai.support.AiPostgresContainer;
import shinhan.fibri.ieum.ai.support.SqlScriptRunner;

class AiPostgresExtensionsIntegrationTest {

	@Test
	void providesVectorPostgisAndPgcryptoOnPostgresql16() {
		String databaseName = "ieum_ai_extensions";
		AiPostgresContainer.recreateDatabase(databaseName);
		SqlScriptRunner.run(databaseName, """
			CREATE EXTENSION IF NOT EXISTS vector;
			CREATE EXTENSION IF NOT EXISTS postgis;
			CREATE EXTENSION IF NOT EXISTS pgcrypto;
			""");

		JdbcClient jdbc = JdbcClient.create(AiPostgresContainer.dataSource(databaseName));

		Map<String, String> versions = jdbc.sql("""
			SELECT extname, extversion
			FROM pg_extension
			WHERE extname IN ('vector', 'postgis', 'pgcrypto')
			""")
			.query((resultSet, rowNumber) -> Map.entry(resultSet.getString(1), resultSet.getString(2)))
			.list()
			.stream()
			.collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

		assertThat(jdbc.sql("SHOW server_version_num").query(Integer.class).single()).isGreaterThanOrEqualTo(160000);
		assertThat(versions).containsKeys("vector", "postgis", "pgcrypto");
	}
}
