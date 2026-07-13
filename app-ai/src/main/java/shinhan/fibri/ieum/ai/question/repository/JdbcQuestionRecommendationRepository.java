package shinhan.fibri.ieum.ai.question.repository;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcQuestionRecommendationRepository implements QuestionRecommendationRepository {

	private static final int EMBEDDING_DIMENSIONS = 768;
	private static final int MAX_CANDIDATE_LIMIT = 100;
	private static final String EMBEDDING_MODEL = "gemini-embedding-2";

	private final JdbcClient jdbc;

	public JdbcQuestionRecommendationRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<QuestionRecommendationCandidate> findCandidates(float[] queryEmbedding, int candidateLimit) {
		validateQueryEmbedding(queryEmbedding);
		validateCandidateLimit(candidateLimit);
		return jdbc.sql("""
			SELECT question.question_id,
			       question.author_id,
			       question.title,
			       task.geo_scope,
			       question.is_resolved,
			       accepted.content AS accepted_answer_content,
			       accepted.is_ai AS accepted_answer_is_ai,
			       task.embedding <=> :queryEmbedding::vector AS raw_candidate_score
			FROM ai_question_tasks task
			JOIN questions question ON question.question_id = task.question_id
			JOIN pins pin ON pin.pin_id = question.pin_id
			LEFT JOIN answers accepted
			       ON accepted.question_id = question.question_id
			      AND accepted.is_accepted = true
			WHERE question.deleted_at IS NULL
			  AND pin.deleted_at IS NULL
			  AND task.embedding IS NOT NULL
			  AND task.embedding_model = :embeddingModel
			ORDER BY raw_candidate_score ASC, question.question_id ASC
			LIMIT :candidateLimit
			""")
			.param("queryEmbedding", vectorLiteral(queryEmbedding))
			.param("embeddingModel", EMBEDDING_MODEL)
			.param("candidateLimit", candidateLimit)
			.query((resultSet, rowNumber) -> {
				String acceptedAnswerContent = resultSet.getString("accepted_answer_content");
				QuestionRecommendationCandidate.AcceptedAnswer acceptedAnswer = acceptedAnswerContent == null
					? null
					: new QuestionRecommendationCandidate.AcceptedAnswer(
						acceptedAnswerContent,
						resultSet.getBoolean("accepted_answer_is_ai")
					);
				return new QuestionRecommendationCandidate(
					resultSet.getLong("question_id"),
					resultSet.getLong("author_id"),
					resultSet.getString("title"),
					resultSet.getString("geo_scope"),
					resultSet.getBoolean("is_resolved"),
					acceptedAnswer,
					resultSet.getDouble("raw_candidate_score")
				);
			})
			.list();
	}

	private void validateQueryEmbedding(float[] queryEmbedding) {
		if (queryEmbedding == null) {
			throw new IllegalArgumentException("queryEmbedding must not be null");
		}
		if (queryEmbedding.length != EMBEDDING_DIMENSIONS) {
			throw new IllegalArgumentException("queryEmbedding must have " + EMBEDDING_DIMENSIONS + " dimensions");
		}
		for (float value : queryEmbedding) {
			if (!Float.isFinite(value)) {
				throw new IllegalArgumentException("queryEmbedding must contain only finite values");
			}
		}
	}

	private void validateCandidateLimit(int candidateLimit) {
		if (candidateLimit < 1 || candidateLimit > MAX_CANDIDATE_LIMIT) {
			throw new IllegalArgumentException("candidateLimit must be between 1 and " + MAX_CANDIDATE_LIMIT);
		}
	}

	private String vectorLiteral(float[] embedding) {
		StringBuilder literal = new StringBuilder("[");
		for (int index = 0; index < embedding.length; index++) {
			if (index > 0) {
				literal.append(',');
			}
			literal.append(embedding[index]);
		}
		return literal.append(']').toString();
	}
}
