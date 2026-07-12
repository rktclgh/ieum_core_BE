package shinhan.fibri.ieum.ai.question.repository;

import java.time.Duration;
import java.util.Optional;

public interface QuestionTaskWorkRepository {

	Optional<ClaimedQuestionTask> claimNext(String workerId, Duration lease, int maxAttempts);
}
