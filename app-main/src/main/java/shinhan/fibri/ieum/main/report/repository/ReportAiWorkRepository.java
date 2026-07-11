package shinhan.fibri.ieum.main.report.repository;

import java.time.Duration;
import java.util.Optional;

public interface ReportAiWorkRepository {

	Optional<ClaimedReport> claimNext(String workerId, Duration lease, int maxAttempts);
}
