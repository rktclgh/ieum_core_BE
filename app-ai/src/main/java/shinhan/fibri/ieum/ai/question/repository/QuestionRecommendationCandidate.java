package shinhan.fibri.ieum.ai.question.repository;

public record QuestionRecommendationCandidate(
	long questionId,
	long authorId,
	String title,
	String geoScope,
	boolean isResolved,
	AcceptedAnswer acceptedAnswer,
	double rawCandidateScore
) {

	public record AcceptedAnswer(String content, boolean isAi) {
	}
}
