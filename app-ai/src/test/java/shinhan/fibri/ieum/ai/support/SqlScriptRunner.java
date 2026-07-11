package shinhan.fibri.ieum.ai.support;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.testcontainers.containers.Container;
import org.testcontainers.images.builder.Transferable;

public final class SqlScriptRunner {

	private SqlScriptRunner() {
	}

	public static void run(String databaseName, String sql) {
		AiPostgresContainer.validateDatabaseName(databaseName);
		String containerPath = "/tmp/ieum-ai-sql-" + UUID.randomUUID() + ".sql";
		AiPostgresContainer.container()
			.copyFileToContainer(Transferable.of(sql.getBytes(StandardCharsets.UTF_8)), containerPath);

		try {
			Container.ExecResult result = AiPostgresContainer.container()
				.execInContainer("psql", "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-d", databaseName, "-f", containerPath);
			if (result.getExitCode() != 0) {
				throw new IllegalStateException("""
					psql failed with exit code %d
					stdout:
					%s
					stderr:
					%s
					""".formatted(result.getExitCode(), result.getStdout(), result.getStderr()));
			}
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while running SQL script", exception);
		}
		catch (Exception exception) {
			throw new IllegalStateException("Failed to run SQL script", exception);
		}
	}
}
