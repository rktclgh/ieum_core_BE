package shinhan.fibri.ieum.main.ai.client;

public record ReportReviewMessage(
	long messageId,
	String actor,
	String content,
	ReportReviewImage image,
	String createdAt
) {
}
