package shinhan.fibri.ieum.ai.support;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@ActiveProfiles("test")
public abstract class AiDatabaseIntegrationTestBase {

	private static final String DATABASE = "ieum_ai_context_test";

	static {
		AiPostgresContainer.recreateDatabase(DATABASE);
		SqlScriptRunner.run(DATABASE, "schema.sql");
	}

	@DynamicPropertySource
	static void databaseProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", () -> AiPostgresContainer.jdbcUrl(DATABASE));
		registry.add("spring.datasource.username", AiPostgresContainer::username);
		registry.add("spring.datasource.password", AiPostgresContainer::password);
	}
}
