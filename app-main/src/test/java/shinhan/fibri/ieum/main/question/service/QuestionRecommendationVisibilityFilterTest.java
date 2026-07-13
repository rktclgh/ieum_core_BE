package shinhan.fibri.ieum.main.question.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import shinhan.fibri.ieum.common.ai.question.dto.InternalQuestionRecommendationItem;
import shinhan.fibri.ieum.main.question.repository.QuestionRecommendationVisibilityProjection;
import shinhan.fibri.ieum.main.question.repository.QuestionRecommendationVisibilityRepository;

class QuestionRecommendationVisibilityFilterTest {

	private final QuestionRecommendationVisibilityRepository repository = mock(QuestionRecommendationVisibilityRepository.class);
	private final QuestionRecommendationVisibilityFilter filter = new QuestionRecommendationVisibilityFilter(repository);

	@Test
	void preservesOriginalAiRankingAfterBatchVisibilityLookup() {
		List<InternalQuestionRecommendationItem> candidates = List.of(
			item(3L, 30L, "third", "0.9300"),
			item(1L, 10L, "first", "0.9100"),
			item(2L, 20L, "second", "0.9000"),
			item(4L, 40L, "removed", "0.8000")
		);
		when(repository.findVisibleCandidates(List.of(3L, 1L, 2L, 4L), 99L, List.of(40L)))
			.thenReturn(List.of(
				new QuestionRecommendationVisibilityProjection(1L, 10L, "fresh first", false),
				new QuestionRecommendationVisibilityProjection(2L, 20L, "fresh second", true),
				new QuestionRecommendationVisibilityProjection(3L, 30L, "fresh third", false)
			));

		List<InternalQuestionRecommendationItem> filtered = filter.filterVisible(candidates, 99L, List.of(40L));

		assertThat(filtered).extracting(InternalQuestionRecommendationItem::questionId)
			.containsExactly(3L, 1L, 2L);
		assertThat(filtered).extracting(InternalQuestionRecommendationItem::title)
			.containsExactly("fresh third", "fresh first", "fresh second");
		assertThat(filtered).extracting(InternalQuestionRecommendationItem::relevanceScore)
			.containsExactly(new BigDecimal("0.9300"), new BigDecimal("0.9100"), new BigDecimal("0.9000"));
		assertThat(filtered).extracting(InternalQuestionRecommendationItem::isResolved)
			.containsExactly(false, false, true);
	}

	@Test
	void emptyInputsReturnEmptyWithoutQuerying() {
		assertThat(filter.filterVisible(List.of(), 99L, List.of())).isEmpty();

		verifyNoInteractions(repository);
	}

	private InternalQuestionRecommendationItem item(long questionId, long authorId, String title, String score) {
		return new InternalQuestionRecommendationItem(
			questionId,
			authorId,
			title,
			new BigDecimal(score),
			"nearby",
			false,
			null
		);
	}
}
