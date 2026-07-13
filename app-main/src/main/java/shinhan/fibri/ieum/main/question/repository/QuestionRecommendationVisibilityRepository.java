package shinhan.fibri.ieum.main.question.repository;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class QuestionRecommendationVisibilityRepository {

	private final JdbcClient jdbc;

	public QuestionRecommendationVisibilityRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<QuestionRecommendationVisibilityProjection> findVisibleCandidates(
		List<Long> candidateQuestionIds,
		long viewerId,
		List<Long> blockedAuthorIds
	) {
		if (candidateQuestionIds == null || candidateQuestionIds.isEmpty()) {
			return List.of();
		}
		List<Long> blockedIds = blockedAuthorIds == null ? List.of() : blockedAuthorIds;
		String blockedAuthorClause = blockedIds.isEmpty() ? "" : "  AND q.author_id NOT IN (:blockedAuthorIds)\n";

		var query = jdbc.sql("""
			SELECT q.question_id,
			       q.author_id,
			       q.title,
			       q.is_resolved
			FROM questions q
			JOIN pins p ON p.pin_id = q.pin_id
			WHERE q.question_id IN (:candidateQuestionIds)
			  AND q.author_id <> :viewerId
			  AND q.deleted_at IS NULL
			  AND p.deleted_at IS NULL
			""" + blockedAuthorClause)
			.param("candidateQuestionIds", candidateQuestionIds)
			.param("viewerId", viewerId);
		if (!blockedIds.isEmpty()) {
			query = query.param("blockedAuthorIds", blockedIds);
		}
		return query.query((rs, rowNumber) -> new QuestionRecommendationVisibilityProjection(
			rs.getLong("question_id"),
			rs.getLong("author_id"),
			rs.getString("title"),
			rs.getBoolean("is_resolved")
		)).list();
	}
}
