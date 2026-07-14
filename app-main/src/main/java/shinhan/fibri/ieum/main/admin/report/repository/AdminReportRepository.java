package shinhan.fibri.ieum.main.admin.report.repository;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import shinhan.fibri.ieum.main.admin.report.service.AdminReportCursor;

@Repository
public class AdminReportRepository {

	private static final String LIST_SELECT = """
		SELECT r.report_id,
		       CAST(r.target_type AS varchar) AS target_type,
		       CASE
		           WHEN r.target_type = 'message' THEN COALESCE(
		               r.message_id,
		               CAST(NULLIF(r.context_snapshot #>> '{reported,messageId}', '') AS bigint)
		           )
		           ELSE COALESCE(
		               r.answer_id,
		               CAST(NULLIF(r.context_snapshot #>> '{reported,answerId}', '') AS bigint)
		           )
		       END AS target_id,
		       CASE
		           WHEN r.target_type = 'message' THEN r.message_id IS NULL
		           ELSE r.answer_id IS NULL
		       END AS target_deleted,
		       r.reporter_id,
		       reporter.nickname AS reporter_nickname,
		       r.reported_user_id,
		       reported.nickname AS reported_user_nickname,
		       CAST(r.reason AS varchar) AS reason,
		       CAST(r.status AS varchar) AS status,
		       CAST(r.ai_review_state AS varchar) AS ai_review_state,
		       CAST(r.ai_recommendation AS varchar) AS ai_recommendation,
		       CAST(r.ai_decision AS varchar) AS ai_decision,
		       r.ai_confidence,
		       r.ai_reviewed_at,
		       r.created_at
		FROM reports r
		JOIN users reporter ON reporter.user_id = r.reporter_id
		LEFT JOIN users reported ON reported.user_id = r.reported_user_id
		WHERE 1 = 1
		""";

	private final NamedParameterJdbcTemplate jdbcTemplate;

	public AdminReportRepository(NamedParameterJdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<AdminReportListRow> findReports(
		String status,
		String aiReviewState,
		String decision,
		AdminReportCursor.Position cursor,
		int limit
	) {
		StringBuilder sql = new StringBuilder(LIST_SELECT);
		MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", limit);
		if (status != null) {
			sql.append(" AND r.status = CAST(:status AS report_status)");
			params.addValue("status", status, Types.VARCHAR);
		}
		if (aiReviewState != null) {
			sql.append(" AND r.ai_review_state = CAST(:aiReviewState AS ai_job_status)");
			params.addValue("aiReviewState", aiReviewState, Types.VARCHAR);
		}
		if (decision != null) {
			sql.append(" AND r.ai_decision = CAST(:decision AS ai_report_decision)");
			params.addValue("decision", decision, Types.VARCHAR);
		}
		if (cursor != null) {
			sql.append("""
				 AND (
				     r.created_at < :cursorCreatedAt
				     OR (r.created_at = :cursorCreatedAt AND r.report_id < :cursorReportId)
				 )
				""");
			params.addValue("cursorCreatedAt", cursor.createdAt(), Types.TIMESTAMP_WITH_TIMEZONE);
			params.addValue("cursorReportId", cursor.reportId(), Types.BIGINT);
		}
		sql.append(" ORDER BY r.created_at DESC, r.report_id DESC LIMIT :limit");
		return jdbcTemplate.query(sql.toString(), params, (rs, rowNumber) -> new AdminReportListRow(
			rs.getLong("report_id"),
			rs.getString("target_type"),
			longObject(rs.getObject("target_id")),
			rs.getBoolean("target_deleted"),
			rs.getLong("reporter_id"),
			rs.getString("reporter_nickname"),
			longObject(rs.getObject("reported_user_id")),
			rs.getString("reported_user_nickname"),
			rs.getString("reason"),
			rs.getString("status"),
			rs.getString("ai_review_state"),
			rs.getString("ai_recommendation"),
			rs.getString("ai_decision"),
			rs.getBigDecimal("ai_confidence"),
			rs.getObject("ai_reviewed_at", OffsetDateTime.class),
			rs.getObject("created_at", OffsetDateTime.class)
		));
	}

	private static Long longObject(Object value) {
		return value == null ? null : ((Number) value).longValue();
	}

	public record AdminReportListRow(
		Long reportId,
		String targetType,
		Long targetId,
		boolean targetDeleted,
		Long reporterId,
		String reporterNickname,
		Long reportedUserId,
		String reportedUserNickname,
		String reason,
		String status,
		String aiReviewState,
		String aiRecommendation,
		String aiDecision,
		BigDecimal aiConfidence,
		OffsetDateTime aiReviewedAt,
		OffsetDateTime createdAt
	) {
	}
}
