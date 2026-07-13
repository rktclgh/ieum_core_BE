package shinhan.fibri.ieum.ai.question.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import shinhan.fibri.ieum.testsupport.CanonicalPostgresContainer;
import shinhan.fibri.ieum.testsupport.SqlScriptRunner;

class JdbcQuestionRecommendationRepositoryIntegrationTest {

	private static final String DATABASE = "ieum_ai_question_recommendation";
	private static final AtomicLong SEQUENCE = new AtomicLong();

	private JdbcClient jdbc;
	private QuestionRecommendationRepository repository;

	@AfterAll
	static void cleanUpDatabase() {
		JdbcClient.create(CanonicalPostgresContainer.dataSource("postgres"))
			.sql("DROP DATABASE IF EXISTS " + DATABASE + " WITH (FORCE)")
			.update();
	}

	@BeforeEach
	void setUp() {
		CanonicalPostgresContainer.recreateDatabase(DATABASE);
		SqlScriptRunner.run(DATABASE, "schema.sql");
		jdbc = JdbcClient.create(CanonicalPostgresContainer.dataSource(DATABASE));
		repository = new JdbcQuestionRecommendationRepository(jdbc);
	}

	@Test
	void findsActiveEmbeddedCandidatesInCosineOrderWithAcceptedAnswerAndLimit() {
		float[] queryEmbedding = unitVector(0);
		long firstQuestionId = insertQuestion(false, false, true, "first nearest");
		long secondQuestionId = insertQuestion(false, false, false, "second nearest");
		long tieQuestionId = insertQuestion(false, false, false, "same distance later id");
		long noEmbeddingQuestionId = insertQuestion(false, false, false, "no embedding");
		long wrongModelQuestionId = insertQuestion(false, false, false, "wrong model");
		long deletedQuestionId = insertQuestion(true, false, false, "deleted question");
		long deletedPinQuestionId = insertQuestion(false, true, false, "deleted pin");

		allowLegacyWrongEmbeddingModelFixture();
		insertEmbeddingTask(firstQuestionId, unitVector(0), "gemini-embedding-2", "local");
		insertEmbeddingTask(secondQuestionId, mixedVector(0.8f, 0.6f), "gemini-embedding-2", "regional");
		insertEmbeddingTask(tieQuestionId, mixedVector(0.8f, 0.6f), "gemini-embedding-2", "general");
		insertTaskWithoutEmbedding(noEmbeddingQuestionId);
		insertEmbeddingTask(wrongModelQuestionId, unitVector(0), "gemini-embedding-001", "general");
		insertEmbeddingTask(deletedQuestionId, unitVector(0), "gemini-embedding-2", "general");
		insertEmbeddingTask(deletedPinQuestionId, unitVector(0), "gemini-embedding-2", "general");
		insertAcceptedAnswer(firstQuestionId, "Accepted human answer", false);
		insertAnswer(secondQuestionId, "Unaccepted answer", false);

		List<QuestionRecommendationCandidate> candidates = repository.findCandidates(queryEmbedding, 2);

		assertThat(candidates).extracting(QuestionRecommendationCandidate::questionId)
			.containsExactly(firstQuestionId, secondQuestionId);
		assertThat(candidates).extracting(QuestionRecommendationCandidate::questionId)
			.doesNotContain(noEmbeddingQuestionId, wrongModelQuestionId, deletedQuestionId, deletedPinQuestionId);
		assertThat(candidates.get(0).rawCandidateScore()).isLessThan(candidates.get(1).rawCandidateScore());
		assertThat(candidates.get(0)).satisfies(candidate -> {
			assertThat(candidate.title()).isEqualTo("first nearest");
			assertThat(candidate.geoScope()).isEqualTo("local");
			assertThat(candidate.isResolved()).isTrue();
			assertThat(candidate.acceptedAnswer()).isNotNull();
			assertThat(candidate.acceptedAnswer().content()).isEqualTo("Accepted human answer");
			assertThat(candidate.acceptedAnswer().isAi()).isFalse();
		});
		assertThat(candidates.get(1)).satisfies(candidate -> {
			assertThat(candidate.questionId()).isLessThan(tieQuestionId);
			assertThat(candidate.acceptedAnswer()).isNull();
		});
	}

	private long insertQuestion(boolean deletedQuestion, boolean deletedPin, boolean resolved, String title) {
		long sequence = SEQUENCE.incrementAndGet();
		long userId = jdbc.sql("""
			INSERT INTO users (email, password_hash, nickname, email_verified)
			VALUES (:email, 'hash', :nickname, true)
			RETURNING user_id
			""")
			.param("email", "recommendation-" + sequence + "@example.com")
			.param("nickname", "recommendation-" + sequence)
			.query(Long.class)
			.single();
		long pinId = jdbc.sql("""
			INSERT INTO pins (author_id, pin_type, location, address, deleted_at)
			VALUES (
				:userId, 'question', ST_SetSRID(ST_MakePoint(127.0, 37.5), 4326)::geography, 'Seoul',
				CASE WHEN :deletedPin THEN now() ELSE NULL END
			)
			RETURNING pin_id
			""")
			.param("userId", userId)
			.param("deletedPin", deletedPin)
			.query(Long.class)
			.single();
		return jdbc.sql("""
			INSERT INTO questions (pin_id, author_id, title, content, is_resolved, deleted_at)
			VALUES (
				:pinId, :userId, :title, 'question content', :resolved,
				CASE WHEN :deletedQuestion THEN now() ELSE NULL END
			)
			RETURNING question_id
			""")
			.param("pinId", pinId)
			.param("userId", userId)
			.param("title", title)
			.param("resolved", resolved)
			.param("deletedQuestion", deletedQuestion)
			.query(Long.class)
			.single();
	}

	private void allowLegacyWrongEmbeddingModelFixture() {
		jdbc.sql("""
			ALTER TABLE ai_question_tasks
			DROP CONSTRAINT ck_ai_question_tasks_embedding_model
			""")
			.update();
	}

	private void insertEmbeddingTask(long questionId, float[] embedding, String model, String geoScope) {
		jdbc.sql("""
			INSERT INTO ai_question_tasks (question_id, embedding, embedding_model, geo_scope)
			VALUES (:questionId, :embedding::vector, :model, :geoScope)
			""")
			.param("questionId", questionId)
			.param("embedding", vectorLiteral(embedding))
			.param("model", model)
			.param("geoScope", geoScope)
			.update();
	}

	private void insertTaskWithoutEmbedding(long questionId) {
		jdbc.sql("INSERT INTO ai_question_tasks (question_id) VALUES (:questionId)")
			.param("questionId", questionId)
			.update();
	}

	private void insertAcceptedAnswer(long questionId, String content, boolean isAi) {
		insertAnswer(questionId, content, isAi, true);
	}

	private void insertAnswer(long questionId, String content, boolean isAi) {
		insertAnswer(questionId, content, isAi, false);
	}

	private void insertAnswer(long questionId, String content, boolean isAi, boolean accepted) {
		Long authorId = isAi ? null : insertAnswerAuthor();
		jdbc.sql("""
			INSERT INTO answers (question_id, author_id, is_ai, content, is_accepted)
			VALUES (:questionId, :authorId, :isAi, :content, :accepted)
			""")
			.param("questionId", questionId)
			.param("authorId", authorId)
			.param("isAi", isAi)
			.param("content", content)
			.param("accepted", accepted)
			.update();
	}

	private long insertAnswerAuthor() {
		long sequence = SEQUENCE.incrementAndGet();
		return jdbc.sql("""
			INSERT INTO users (email, password_hash, nickname, email_verified)
			VALUES (:email, 'hash', :nickname, true)
			RETURNING user_id
			""")
			.param("email", "answerer-" + sequence + "@example.com")
			.param("nickname", "answerer-" + sequence)
			.query(Long.class)
			.single();
	}

	private static float[] unitVector(int index) {
		float[] vector = new float[768];
		vector[index] = 1.0f;
		return vector;
	}

	private static float[] mixedVector(float first, float second) {
		float[] vector = new float[768];
		vector[0] = first;
		vector[1] = second;
		return vector;
	}

	private static String vectorLiteral(float[] vector) {
		StringBuilder literal = new StringBuilder("[");
		for (int index = 0; index < vector.length; index++) {
			if (index > 0) {
				literal.append(',');
			}
			literal.append(vector[index]);
		}
		return literal.append(']').toString();
	}
}
