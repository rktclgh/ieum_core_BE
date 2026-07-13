package shinhan.fibri.ieum.ai.question.embedding;

public record QuestionEmbeddingProperties(String geminiApiKey) {

	public static final String API_KEY_ENVIRONMENT_VARIABLE = "APP_AI_QUESTION_EMBEDDING_GEMINI_API_KEY";

	public QuestionEmbeddingProperties {
		geminiApiKey = required(geminiApiKey);
	}

	private static String required(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(API_KEY_ENVIRONMENT_VARIABLE + " must not be blank");
		}
		return value.trim();
	}
}
