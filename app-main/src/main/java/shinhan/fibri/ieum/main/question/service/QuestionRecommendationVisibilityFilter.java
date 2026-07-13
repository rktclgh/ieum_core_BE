package shinhan.fibri.ieum.main.question.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import shinhan.fibri.ieum.common.ai.question.dto.InternalQuestionRecommendationItem;
import shinhan.fibri.ieum.main.question.repository.QuestionRecommendationVisibilityProjection;
import shinhan.fibri.ieum.main.question.repository.QuestionRecommendationVisibilityRepository;

@Service
public class QuestionRecommendationVisibilityFilter {

	private final QuestionRecommendationVisibilityRepository repository;

	public QuestionRecommendationVisibilityFilter(QuestionRecommendationVisibilityRepository repository) {
		this.repository = repository;
	}

	public List<InternalQuestionRecommendationItem> filterVisible(
		List<InternalQuestionRecommendationItem> candidates,
		long viewerId,
		List<Long> blockedAuthorIds
	) {
		if (candidates == null || candidates.isEmpty()) {
			return List.of();
		}
		List<Long> candidateIds = candidates.stream()
			.map(InternalQuestionRecommendationItem::questionId)
			.toList();
		Map<Long, QuestionRecommendationVisibilityProjection> visibleById = new LinkedHashMap<>();
		for (QuestionRecommendationVisibilityProjection visible : repository.findVisibleCandidates(
			candidateIds,
			viewerId,
			blockedAuthorIds == null ? List.of() : blockedAuthorIds
		)) {
			visibleById.put(visible.questionId(), visible);
		}

		return candidates.stream()
			.filter(candidate -> visibleById.containsKey(candidate.questionId()))
			.map(candidate -> mergeVisibleData(candidate, visibleById.get(candidate.questionId())))
			.toList();
	}

	private InternalQuestionRecommendationItem mergeVisibleData(
		InternalQuestionRecommendationItem candidate,
		QuestionRecommendationVisibilityProjection visible
	) {
		return new InternalQuestionRecommendationItem(
			visible.questionId(),
			visible.authorId(),
			visible.title(),
			candidate.relevanceScore(),
			candidate.geoScope(),
			visible.resolved(),
			candidate.acceptedAnswer()
		);
	}
}
