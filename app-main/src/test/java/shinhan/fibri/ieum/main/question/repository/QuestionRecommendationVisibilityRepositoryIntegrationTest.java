package shinhan.fibri.ieum.main.question.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import shinhan.fibri.ieum.testsupport.CanonicalPostgresContainer;
import shinhan.fibri.ieum.testsupport.SqlScriptRunner;

class QuestionRecommendationVisibilityRepositoryIntegrationTest {

	private static final String DATABASE = "ieum_main_question_visibility";

	private JdbcClient jdbc;
	private QuestionRecommendationVisibilityRepository repository;

	@BeforeEach
	void setUp() {
		CanonicalPostgresContainer.recreateDatabase(DATABASE);
		SqlScriptRunner.run(DATABASE, "schema.sql");
		jdbc = JdbcClient.create(CanonicalPostgresContainer.dataSource(DATABASE));
		repository = new QuestionRecommendationVisibilityRepository(jdbc);
		seedUsers();
	}

	@Test
	void findsOnlyVisibleCandidateQuestionsInSingleBatch() {
		insertQuestion(100L, 20L, null, null, "visible newest");
		insertQuestion(101L, 10L, null, null, "self authored");
		insertQuestion(102L, 30L, null, null, "blocked author");
		insertQuestion(103L, 40L, "2026-07-13T00:00:00Z", null, "deleted question");
		insertQuestion(104L, 50L, null, "2026-07-13T00:00:00Z", "deleted pin");
		insertQuestion(105L, 60L, null, null, "visible oldest");

		List<QuestionRecommendationVisibilityProjection> visible = repository.findVisibleCandidates(
			List.of(105L, 999L, 100L, 103L, 102L, 104L, 101L),
			10L,
			List.of(30L)
		);

		assertThat(visible).extracting(QuestionRecommendationVisibilityProjection::questionId)
			.containsExactlyInAnyOrder(100L, 105L);
		assertThat(visible).extracting(QuestionRecommendationVisibilityProjection::authorId)
			.containsExactlyInAnyOrder(20L, 60L);
		assertThat(visible).extracting(QuestionRecommendationVisibilityProjection::title)
			.containsExactlyInAnyOrder("visible newest", "visible oldest");
		assertThat(visible).allSatisfy(row -> assertThat(row.resolved()).isFalse());
	}

	@Test
	void emptyCandidatesOrBlockedAuthorsAreSafe() {
		insertQuestion(100L, 20L, null, null, "visible");

		assertThat(repository.findVisibleCandidates(List.of(), 10L, List.of())).isEmpty();

		List<QuestionRecommendationVisibilityProjection> visible = repository.findVisibleCandidates(
			List.of(100L),
			10L,
			List.of()
		);

		assertThat(visible).extracting(QuestionRecommendationVisibilityProjection::questionId)
			.containsExactly(100L);
	}

	private void seedUsers() {
		jdbc.sql("""
			INSERT INTO users (user_id, email, provider, password_hash, nickname, email_verified, role, status)
			VALUES
			    (10, 'viewer@example.com', 'email', 'hash', 'viewer', true, 'user', 'active'),
			    (20, 'visible1@example.com', 'email', 'hash', 'visible1', true, 'user', 'active'),
			    (30, 'blocked@example.com', 'email', 'hash', 'blocked', true, 'user', 'active'),
			    (40, 'deletedq@example.com', 'email', 'hash', 'deletedq', true, 'user', 'active'),
			    (50, 'deletedp@example.com', 'email', 'hash', 'deletedp', true, 'user', 'active'),
			    (60, 'visible2@example.com', 'email', 'hash', 'visible2', true, 'user', 'active')
			""").update();
	}

	private void insertQuestion(
		long questionId,
		long authorId,
		String questionDeletedAt,
		String pinDeletedAt,
		String title
	) {
		long pinId = questionId + 1000;
		jdbc.sql("""
			INSERT INTO pins (pin_id, author_id, pin_type, location, address, detail_address, label, deleted_at)
			VALUES (
			    :pinId,
			    :authorId,
			    'question',
			    ST_SetSRID(ST_MakePoint(127.0, 37.5), 4326)::geography,
			    'address',
			    '',
			    '',
			    CAST(:pinDeletedAt AS timestamptz)
			)
			""")
			.param("pinId", pinId)
			.param("authorId", authorId)
			.param("pinDeletedAt", pinDeletedAt)
			.update();
		jdbc.sql("""
			INSERT INTO questions (question_id, pin_id, author_id, title, content, is_resolved, deleted_at)
			VALUES (:questionId, :pinId, :authorId, :title, 'content', false, CAST(:questionDeletedAt AS timestamptz))
			""")
			.param("questionId", questionId)
			.param("pinId", pinId)
			.param("authorId", authorId)
			.param("title", title)
			.param("questionDeletedAt", questionDeletedAt)
			.update();
	}
}
