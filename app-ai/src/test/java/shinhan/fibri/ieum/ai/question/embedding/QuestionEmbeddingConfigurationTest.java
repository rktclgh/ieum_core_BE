package shinhan.fibri.ieum.ai.question.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.Client;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class QuestionEmbeddingConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(QuestionEmbeddingConfiguration.class);

	@Test
	void doesNotRegisterEmbeddingGatewayWhenQuestionAnswerIsDisabledByDefault() {
		contextRunner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).doesNotHaveBean(QuestionEmbeddingGateway.class);
			assertThat(context).doesNotHaveBean(Client.class);
		});
	}

	@Test
	void failsFastForBlankApiKeyWhenQuestionAnswerIsEnabled() {
		contextRunner
			.withPropertyValues(
				"app.ai.features.question-answer-enabled=true",
				QuestionEmbeddingProperties.API_KEY_ENVIRONMENT_VARIABLE + "="
			)
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void wiresGeminiEmbeddingGatewayWhenQuestionAnswerIsEnabled() {
		contextRunner
			.withPropertyValues(
				"app.ai.features.question-answer-enabled=true",
				QuestionEmbeddingProperties.API_KEY_ENVIRONMENT_VARIABLE + "=test-only-not-a-real-key"
			)
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).hasSingleBean(QuestionEmbeddingGateway.class);
				assertThat(context).hasBean("questionEmbeddingGeminiClient");
				assertThat(context).hasSingleBean(Client.class);
			});
	}
}
