package shinhan.fibri.ieum.main.report.repository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import shinhan.fibri.ieum.main.report.domain.ReportReason;

@Repository
public class JdbcReportAiWorkRepository implements ReportAiWorkRepository {

	private final JdbcClient jdbc;

	public JdbcReportAiWorkRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<ClaimedReport> claimNext(String workerId, Duration lease, int maxAttempts) {
		validate(workerId, lease, maxAttempts);
		UUID attemptId = UUID.randomUUID();
		return jdbc.sql("""
			WITH candidate AS (
			    SELECT report_id
			    FROM reports
			    WHERE ai_review_state IN ('pending', 'retry')
			      AND ai_next_attempt_at <= CURRENT_TIMESTAMP
			      AND ai_attempts < :maxAttempts
			    ORDER BY ai_next_attempt_at, created_at, report_id
			    FOR UPDATE SKIP LOCKED
			    LIMIT 1
			)
			UPDATE reports report
			SET ai_review_state = 'processing',
			    ai_review_attempt_id = :attemptId,
			    ai_attempts = report.ai_attempts + 1,
			    ai_next_attempt_at = NULL,
			    ai_lease_until = CURRENT_TIMESTAMP + (:leaseSeconds * INTERVAL '1 second'),
			    ai_locked_by = :workerId,
			    ai_last_error_code = NULL,
			    ai_last_error_message = NULL
			FROM candidate
			WHERE report.report_id = candidate.report_id
			RETURNING report.report_id, report.message_id, report.reporter_id, report.reported_user_id,
			          report.reason::text AS reason, report.detail, report.context_snapshot::text,
			          report.context_hash, report.ai_review_attempt_id, report.ai_attempts, report.ai_lease_until
			""")
			.param("attemptId", attemptId)
			.param("leaseSeconds", lease.toSeconds())
			.param("workerId", workerId)
			.param("maxAttempts", maxAttempts)
			.query((rs, rowNumber) -> new ClaimedReport(
				rs.getLong("report_id"),
				(Long) rs.getObject("message_id"),
				rs.getLong("reporter_id"),
				rs.getLong("reported_user_id"),
				ReportReason.valueOf(rs.getString("reason")),
				rs.getString("detail"),
				rs.getString("context_snapshot"),
				rs.getString("context_hash"),
				rs.getObject("ai_review_attempt_id", UUID.class),
				rs.getInt("ai_attempts"),
				rs.getObject("ai_lease_until", OffsetDateTime.class)
			))
			.optional();
	}

	private void validate(String workerId, Duration lease, int maxAttempts) {
		if (workerId == null || workerId.isBlank() || workerId.length() > 120) {
			throw new IllegalArgumentException("workerId must contain 1 to 120 characters");
		}
		if (lease == null || lease.isZero() || lease.isNegative()) {
			throw new IllegalArgumentException("lease must be positive");
		}
		if (maxAttempts < 1 || maxAttempts > Short.MAX_VALUE) {
			throw new IllegalArgumentException("maxAttempts must be between 1 and " + Short.MAX_VALUE);
		}
	}
}
