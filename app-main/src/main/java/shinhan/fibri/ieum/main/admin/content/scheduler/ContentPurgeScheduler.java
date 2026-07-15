package shinhan.fibri.ieum.main.admin.content.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import shinhan.fibri.ieum.main.admin.content.service.ContentPurgeService;

@Component
@RequiredArgsConstructor
public class ContentPurgeScheduler {

	private final ContentPurgeService contentPurgeService;

	@Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
	public void purgeExpiredQuestionContent() {
		contentPurgeService.purgeExpiredQuestionContent();
	}
}
