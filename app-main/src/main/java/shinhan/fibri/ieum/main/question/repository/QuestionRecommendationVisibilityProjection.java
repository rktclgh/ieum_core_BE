package shinhan.fibri.ieum.main.question.repository;

public record QuestionRecommendationVisibilityProjection(
	long questionId,
	long authorId,
	String title,
	boolean resolved
) {
}
