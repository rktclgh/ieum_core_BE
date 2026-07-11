package shinhan.fibri.ieum.ai.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;

public final class AiPostgresContainer {

	private static final String IMAGE_NAME = "ieum-ai-postgres:pg16-vector-postgis-issue53-v8";
	private static final String DEFAULT_DATABASE = "ieum_ai_test";
	private static final String USERNAME = "postgres";
	private static final String PASSWORD = "postgres";
	private static final Pattern DATABASE_NAME_PATTERN = Pattern.compile("[a-z][a-z0-9_]{0,62}");
	private static final PostgreSQLContainer<?> POSTGRES = createContainer();

	private AiPostgresContainer() {
	}

	public static DataSource dataSource() {
		return dataSource(DEFAULT_DATABASE);
	}

	public static DataSource dataSource(String databaseName) {
		DriverManagerDataSource dataSource = new DriverManagerDataSource(jdbcUrl(databaseName), USERNAME, PASSWORD);
		dataSource.setDriverClassName("org.postgresql.Driver");
		return dataSource;
	}

	public static String jdbcUrl() {
		return jdbcUrl(DEFAULT_DATABASE);
	}

	public static String jdbcUrl(String databaseName) {
		validateDatabaseName(databaseName);
		ensureStarted();
		return "jdbc:postgresql://%s:%d/%s".formatted(
			POSTGRES.getHost(),
			POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
			databaseName);
	}

	public static void recreateDatabase(String databaseName) {
		validateDatabaseName(databaseName);
		ensureStarted();
		try (Connection connection = dataSource("postgres").getConnection();
			 Statement statement = connection.createStatement()) {
			statement.execute("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '" + databaseName + "'");
			statement.execute("DROP DATABASE IF EXISTS \"" + databaseName + "\"");
			statement.execute("CREATE DATABASE \"" + databaseName + "\"");
		}
		catch (SQLException exception) {
			throw new IllegalStateException("Failed to recreate test database: " + databaseName, exception);
		}
	}

	static PostgreSQLContainer<?> container() {
		ensureStarted();
		return POSTGRES;
	}

	private static PostgreSQLContainer<?> createContainer() {
		DockerImageName imageName = DockerImageName.parse(builtImageName()).asCompatibleSubstituteFor("postgres");
		PostgreSQLContainer<?> container = new PostgreSQLContainer<>(
			imageName)
			.withDatabaseName(DEFAULT_DATABASE)
			.withUsername(USERNAME)
			.withPassword(PASSWORD);
		container.start();
		return container;
	}

	private static String builtImageName() {
		return imageFromDockerfile().get();
	}

	private static ImageFromDockerfile imageFromDockerfile() {
		return new ImageFromDockerfile(IMAGE_NAME, false)
			.withDockerfile(repositoryRoot().resolve("db/test-support/postgres-ai/Dockerfile"));
	}

	private static Path repositoryRoot() {
		Path current = Path.of("").toAbsolutePath();
		while (current != null) {
			if (Files.exists(current.resolve("settings.gradle.kts"))) {
				return current;
			}
			current = current.getParent();
		}
		throw new IllegalStateException("Could not locate repository root");
	}

	private static void ensureStarted() {
		if (!POSTGRES.isRunning()) {
			POSTGRES.start();
		}
	}

	static void validateDatabaseName(String databaseName) {
		if (databaseName == null || !DATABASE_NAME_PATTERN.matcher(databaseName).matches()) {
			throw new IllegalArgumentException("Invalid PostgreSQL database name: " + databaseName);
		}
	}
}
