package shinhan.fibri.ieum.ai.question.repository;

import java.util.List;

public interface QuestionRecommendationRepository {

	List<QuestionRecommendationCandidate> findCandidates(float[] queryEmbedding, int candidateLimit);
}
