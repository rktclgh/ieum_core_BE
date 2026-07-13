package shinhan.fibri.ieum.ai.question.embedding;

import com.google.genai.Client;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "app.ai.features", name = "question-answer-enabled", havingValue = "true")
class QuestionEmbeddingConfiguration {

	@Bean
	QuestionEmbeddingProperties questionEmbeddingProperties(
		@Value("${APP_AI_QUESTION_EMBEDDING_GEMINI_API_KEY:}") String geminiApiKey
	) {
		return new QuestionEmbeddingProperties(geminiApiKey);
	}

	@Bean(destroyMethod = "close")
	Client questionEmbeddingGeminiClient(QuestionEmbeddingProperties properties) {
		return Client.builder()
			.apiKey(properties.geminiApiKey())
			.build();
	}

	@Bean
	QuestionEmbeddingGateway questionEmbeddingGateway(
		@Qualifier("questionEmbeddingGeminiClient") Client questionEmbeddingGeminiClient
	) {
		return new GeminiQuestionEmbeddingGateway(new GoogleGenAiGeminiEmbeddingClient(questionEmbeddingGeminiClient));
	}
}
